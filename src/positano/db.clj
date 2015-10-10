(ns positano.db
  (:require [datascript.core :as d]
            [clojure.edn :as edn]
            [clojure.core.async :as async :refer [<!! thread]]
            [positano.trace :as trace]
            [positano.print :as pr]))

(def event-counter (atom 0))

(def event-sequence (atom 0))

(def db-uri-base "datomic:mem://")

(def schema
  {:event/type
   {:db/cardinality :db.cardinality/one
    :db/index       true}
     
   :event/id
   {:db/cardinality :db.cardinality/one
    :db/index       true
    :db/unique      :db.unique/identity}
     
   :event/timestamp
   {:db/cardinality :db.cardinality/one
    :db/index       true}
     
   :event/fn-name
   {:db/cardinality :db.cardinality/one
    :db/index       true}
     
   :event/ns
   {:db/cardinality :db.cardinality/one
    :db/index       true}
     
   :event/thread
   {:db/cardinality :db.cardinality/one
    :db/index       true}
     
   :event/return-value
   {:db/cardinality :db.cardinality/one}
     
   :event/sequence
   {:db/cardinality :db.cardinality/one}

   ;;refs
   :event/fn-entry
   {:db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}
     
   :event/fn-return
   {:db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}
     
   :event/fn-caller
   {:db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}
     
   ;;fn args
   :event/fn-args
   {:db/valueType   :db.type/ref
    :db/isComponent true
    :db/cardinality :db.cardinality/many}
     
   :fn-arg/value
   {:db/cardinality :db.cardinality/one}
     
   :fn-arg/position
   {:db/cardinality :db.cardinality/one
    }})

(defn destroy-db! [uri]
  ;;TODO delete database here?
  (reset! event-counter 0)
  (reset! event-sequence 0))

(defn init-connection
  []
  (d/create-conn schema))

(defn- edn-str [x]
  (pr/pr-str x))

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
                              :fn-arg/value (edn-str val)})
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
       :event/return-value (edn-str (:event/return-value e))
       :event/fn-entry [:event/id call-event-id]})
     {:db/id [:event/id call-event-id]
      :event/fn-return -100}]))

(defmulti deserialise :event/type)

(defn- read-edn-string [s]
  (try
    (edn/read-string {:default (fn [_ x] x)} s)
    (catch Exception e
      (throw (ex-info "Cannot read EDN string" {:string s} e)))))

(defmethod deserialise :fn-call [e]
  (assoc
   (into {} e)
   :event/fn-args
   (->> e :event/fn-args
        (sort-by :fn-arg/position)
        (mapv (comp read-edn-string :fn-arg/value)))))

(defmethod deserialise :fn-return [e]
  (assoc
   (into {} e)
   :event/return-value
   (-> e :event/return-value read-edn-string)))


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
)
