(ns welding.advisor
  "WeldingAdvisor — the advisor named in this repository's README,
  proposing a weld operation (approve a weld inspection, approve
  arc-welding operation, approve flammable proximity) from a job
  order, material spec and safety plan. Swappable mock/llm; the
  advisor ONLY proposes — `welding.governor` checks the defect-length
  and alignment-offset ceilings independently and always escalates
  arc-welding/flammable-proximity decisions. Modeled on
  cloud-itonami-isco-4311's advisor.

  A proposal: {:op :approve-weld-inspection|:approve-arc-welding-operation|:approve-flammable-proximity
               :effect :propose :joint-id str :defect-length-mm number
               :alignment-offset-mm number :stake kw :confidence n
               :rationale str}"
  (:require #?(:clj [clojure.edn :as edn] :cljs [cljs.reader :as edn])))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake joint-id defect-length-mm alignment-offset-mm] :as request}]
  {:op op
   :effect :propose
   :joint-id joint-id
   :defect-length-mm defect-length-mm
   :alignment-offset-mm alignment-offset-mm
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a welding-practice advisor. Given a request, propose an
   :op, the :joint-id, :defect-length-mm and :alignment-offset-mm, an
   honest :confidence and a :stake. Never call an over-ceiling defect
   or alignment offset conforming — the governor checks both against
   the registered joint record. Arc-welding and flammable-proximity
   decisions always require human sign-off regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
