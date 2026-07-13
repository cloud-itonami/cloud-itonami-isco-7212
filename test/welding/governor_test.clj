(ns welding.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [welding.store :as store]
            [welding.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Fab Shop"})
    (store/register-joint! st {:joint-id "J-1" :client-id "client-1"
                               :name "beam-splice-7"
                               :max-defect-length-mm 1.5
                               :max-alignment-offset-mm 2.0})
    st))

(defn- inspect [defect offset]
  {:op :approve-weld-inspection :effect :propose :joint-id "J-1"
   :defect-length-mm defect :alignment-offset-mm offset :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-both-ceilings
  (let [st (fresh-store)
        v (governor/check req {} (inspect 0.5 1.0) st)]
    (is (:ok? v))))

(deftest ok-at-exact-ceilings
  (testing "both ceilings are inclusive"
    (let [st (fresh-store)
          v (governor/check req {} (inspect 1.5 2.0) st)]
      (is (:ok? v)))))

(deftest hard-on-defect-exceeds-acceptance
  (testing "weld defect acceptance is measured against code, not eyeballed"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (inspect 3.0 1.0) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :defect-exceeds-acceptance (:rule %)) (:violations v))))))

(deftest hard-on-alignment-offset-exceeds-ceiling
  (let [st (fresh-store)
        v (governor/check req {} (assoc (inspect 0.5 5.0) :confidence 0.99) st)]
    (is (:hard? v))
    (is (some #(= :alignment-offset-exceeds-ceiling (:rule %)) (:violations v)))))

(deftest hard-on-unknown-joint
  (let [st (fresh-store)
        v (governor/check req {} (assoc (inspect 0.5 1.0) :joint-id "J-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-joint (:rule %)) (:violations v)))))

(deftest hard-on-foreign-joint
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (inspect 0.5 1.0) st)]
      (is (:hard? v))
      (is (some #(= :joint-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (inspect 0.5 1.0) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (inspect 0.5 1.0) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest always-escalates-arc-welding-operation-even-at-high-confidence
  (testing "no robot operation near open flame/arc welding without the governor gate"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-arc-welding-operation :effect :propose
                                    :joint-id "J-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-flammable-proximity-even-at-high-confidence
  (testing "flammable-material proximity requires human sign-off"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-flammable-proximity :effect :propose
                                    :joint-id "J-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (inspect 0.5 1.0) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
