(ns welding.governor
  "WeldingGovernor — the independent safety/traceability layer named
  in this repository's README/business-model.md, gating the
  robot-dispensed physical work (joint-alignment checks, post-weld
  inspection scans) an advisor may propose. The governor never
  dispatches hardware itself. Modeled on cloud-itonami-isco-4311's
  bookkeeping.governor. Inspection twist: a measured weld defect
  length and a measured joint-alignment offset are each arithmetic
  comparison against their own registered ceiling — weld defect
  acceptance is measured against code, not eyeballed.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. no-actuation      — proposal :effect must be :propose (the
                           governor never dispatches hardware; it only
                           gates what the robot may execute).
    3. joint basis          — an inspection approval must cite a
                           REGISTERED joint belonging to this client.
    4. defect-length ceiling — the proposed measured defect length
                           must not exceed the joint's registered
                           :max-defect-length-mm (measured against
                           code, not eyeballed).
    5. alignment-offset ceiling — the proposed measured alignment
                           offset must not exceed the joint's
                           registered :max-alignment-offset-mm.
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off per
  business-model.md's Trust Controls — these are :high/
  :safety-critical regardless of confidence):
    6. :op :approve-arc-welding-operation (no robot operation near
                           open flame/arc welding without the
                           governor gate).
    7. :op :approve-flammable-proximity (flammable-material proximity
                           requires human sign-off).
    8. low confidence (< `confidence-floor`)."
  (:require [welding.store :as store]))

(def confidence-floor 0.6)

(def ^:private always-escalate-ops #{:approve-arc-welding-operation
                                     :approve-flammable-proximity})

(defn- hard-violations [{:keys [request proposal]} client-record j]
  (let [{:keys [op defect-length-mm alignment-offset-mm]} proposal
        inspect? (= :approve-weld-inspection op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor はハードウェアを直接起動しない）"})

      (and inspect? (nil? j))
      (conj {:rule :unknown-joint :detail "未登録 joint への検査承認は不可"})

      (and inspect? j (not= (:client-id j) (:client-id request)))
      (conj {:rule :joint-wrong-client :detail "joint が別 client のもの"})

      (and inspect? j (number? defect-length-mm) (> defect-length-mm (:max-defect-length-mm j)))
      (conj {:rule :defect-exceeds-acceptance
             :detail (str "欠陥長 " defect-length-mm "mm > 登録済み受入上限 "
                          (:max-defect-length-mm j) "mm（溶接欠陥の受入は規格照合であって目視ではない）")})

      (and inspect? j (number? alignment-offset-mm)
           (> alignment-offset-mm (:max-alignment-offset-mm j)))
      (conj {:rule :alignment-offset-exceeds-ceiling
             :detail (str "接合部ずれ " alignment-offset-mm "mm > 登録済み上限 "
                          (:max-alignment-offset-mm j) "mm")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `welding.store/Store`. Pure — never mutates the
  store, never dispatches the robot."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        j (some->> (:joint-id proposal) (store/joint store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record j)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
