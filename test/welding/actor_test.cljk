(ns welding.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [welding.actor :as actor]
            [welding.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Fab Shop"})
    (store/register-joint! st {:joint-id "J-1" :client-id "client-1"
                               :name "beam-splice-7"
                               :max-defect-length-mm 1.5
                               :max-alignment-offset-mm 2.0})
    st))

(deftest commits-an-in-ceiling-inspection
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-weld-inspection :stake :low
                 :joint-id "J-1" :defect-length-mm 0.5 :alignment-offset-mm 1.0}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-over-ceiling-inspection
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-weld-inspection :stake :low
                 :joint-id "J-1" :defect-length-mm 5.0 :alignment-offset-mm 1.0}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-approves-arc-welding-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-arc-welding-operation :stake :low
                 :joint-id "J-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
