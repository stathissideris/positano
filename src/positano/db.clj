(ns positano.db
  (:require [datascript.core :as d]
            [clojure.edn :as edn]
            [clojure.core.async :as async :refer [<!! thread]]
            [positano.trace :as trace]))

(def event-counter (atom 0))

(def event-sequence (atom 0))

(def one {:db/cardinality :db.cardinality/one})
(def one-ref {:db/valueType   :db.type/ref
              :db/cardinality :db.cardinality/one})

(def many-ref {:db/valueType   :db.type/ref
               :db/cardinality :db.cardinality/many})

(def schema
  {:event/type         one

   :event/id
   {:db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}

   :event/timestamp    one
   :event/fn-name      one
   :event/ns           one
   :event/thread       one
   :event/return-value one
   :event/sequence     one

   ;;refs
   :event/fn-entry     one-ref
   :event/fn-return    one-ref
   :event/fn-caller    one-ref

   ;;fn args
   :event/fn-args
   {:db/valueType   :db.type/ref
    :db/isComponent true
    :db/cardinality :db.cardinality/many}

   :fn-arg/value       one
   :fn-arg/position    one

   ;;TODO prefix these with analyze/
   :expr               one-ref
   :init               one-ref
   :statements         many-ref
   :ret                one-ref
   :args               many-ref
   :bindings           many-ref
   :methods            many-ref
   :meta               one-ref
   :env                one-ref
   :test               one-ref
   :body               one-ref
   :then               one-ref
   :else               one-ref
   :fn                 one-ref
   :meta-val           one-ref})

(defn destroy-db! [uri]
  ;;TODO delete database here?
  (reset! event-counter 0)
  (reset! event-sequence 0))

(defn init-connection
  []
  (d/create-conn schema))

(defmulti to-transactions :event/type)

(defmethod to-transactions :fn-call
  [e]
  (swap! event-sequence inc)
  [(merge
    (dissoc e :event/fn-caller :event/fn-args)
    {:db/id -1
     :event/sequence @event-sequence}
    (when (:event/fn-caller e)
      {:event/fn-caller [:event/id (:event/fn-caller e)]})
    (when (seq (:event/fn-args e))
      {:event/fn-args (map (fn [pos val]
                             {:fn-arg/position pos
                              :fn-arg/value (or val :positano/nil)})
                           (range) (:event/fn-args e))}))])

(defn- return-id->call-id [id]
  (str "c" (subs id 1)))

(defmethod to-transactions :fn-return
  [e]
  (swap! event-sequence inc)
  (let [id (str (:event/id e))
        call-event-id (return-id->call-id id)]
    [(merge
      e
      {:db/id -100
       :event/sequence @event-sequence
       :event/return-value (or (:event/return-value e) :positano/nil)
       :event/fn-entry [:event/id call-event-id]})
     {:db/id [:event/id call-event-id]
      :event/fn-return -100}]))

(defmulti deserialise :event/type)

(defmethod deserialise :fn-call [e]
  (assoc
   (into {} e)
   :event/fn-args
   (->> e :event/fn-args
        (sort-by :fn-arg/position)
        (mapv :fn-arg/value))))

(defmethod deserialise :fn-return [e] e)

(defn event-channel
  "Make a channel which can receive events that will be sent to the
  datomic at the passed uri. The optional event-transducer allows
  somehow transforming the events before sending thme to datomic."
  [conn & [event-transducer]]
  (let [channel           (if event-transducer
                            (async/chan 1024 event-transducer)
                            (async/chan 1024))
        send-event!       (fn [e]
                            (try
                              @(d/transact conn (to-transactions e))
                              (swap! event-counter inc)
                              (catch Exception e (println e))))]
    (thread
      (trace/without-recording ;;dynamic binding is thread-local so we need to say this once more here
       (loop []
         (let [event           (<!! channel)]
           (if (nil? event)
             nil ;;TODO do we need to close the connection here?
             (do
               (send-event! event)
               (recur)))))))
    channel))

(defn clear-db! [conn]
  ;;TODO
  )

(comment
  (def conn (init-connection))
  (def ch (event-channel conn))

  (async/>!! ch {:type :fn-call :id "t999" :fn-name "function-name" :ns "positano.core" :args [1 2 3] :timestamp (java.util.Date.)})

  @(d/transact
    conn
    [{:db/id           -1
      :event/type      :fn-call
      :event/id        "t999"
      :event/timestamp (java.util.Date.)
      :event/fn-name   "foo"
      :event/ns        "positano.core"
      :event/fn-args   [{:fn-arg/position 0 :fn-arg/value "9"}
                        {:fn-arg/position 1 :fn-arg/value "[1 2]"}]}])

  ;;;;

  (def db (d/db conn))

  (def q-result
    (d/q '[:find ?e .
           :where [?e :event/id "t999"]]
         db))

  (def ent (d/entity db q-result))

  (d/touch ent)

  (defn partition-at
  ([sep? coll]
   (lazy-seq
    (when-let [s (seq coll)]
      (let [first-sep? (sep? (first s))
            run        (take-while #(not (sep? %)) (if first-sep? (next s) s))
            run        (if first-sep?
                         (cons (first s) run)
                         run)]
        (cons run (partition-at sep? (seq (drop (count run) s)))))))))

  (partition-at #{:sep} [1 2 3 4 :sep 5 6 7 :seq 8 9 10 11 12 13 14])
  (partition-at #{:sep} [:sep 1 2 3 4 :sep 5 6 7 :sep 8 9 10 11 12 13 14])
  )
