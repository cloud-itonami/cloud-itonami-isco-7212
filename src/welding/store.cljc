(ns welding.store
  "SSoT for the ISCO-08 7212 independent welding practice actor
  (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors section;
  README's 'Robotics premise' — a weld-prep and inspection robot
  performs joint-alignment checks and post-weld inspection scans
  under this advisor/governor pair, which never dispatches hardware
  itself). Modeled on cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client — a registered organization (:client-id, :name)
    joint  — a registered weld joint {:joint-id :client-id :name
             :max-defect-length-mm number
             :max-alignment-offset-mm number}.
             `:max-defect-length-mm` is the registered acceptance
             ceiling a proposed inspection's measured defect length
             must not exceed (weld defect acceptance is measured
             against code, not eyeballed); `:max-alignment-offset-mm`
             is the registered ceiling a proposed inspection's
             measured joint-alignment offset must not exceed.
    record — a committed operating record (approved weld inspection)
             — written ONLY via commit-record!.
    ledger — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (joint [s joint-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-joint! [s j])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (joint [_ joint-id] (get-in @a [:joints joint-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-joint! [s j]
    (swap! a assoc-in [:joints (:joint-id j)] j) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :joints {} :records [] :ledger []}
                                   seed)))))
