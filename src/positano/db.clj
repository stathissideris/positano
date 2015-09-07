(ns positano.db
  (:require [datomic.api :as d]
            [clojure.edn :as edn]
            [clojure.core.async :as async :refer [<!! thread]]
            [positano.trace :as trace]))

(def event-counter (atom 0))

(def db-uri-base "datomic:mem://")

(def schema
  [{:db/id #db/id[:db.part/db]
    :db/ident :event/type
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/index true
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :event/id
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/index true
    :db/unique :db.unique/value
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :event/timestamp
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/index true
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :event/fn-name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/index true
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :event/ns
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/index true
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :event/thread
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/index true
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :event/return-value
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   ;;refs
   {:db/id #db/id[:db.part/db]
    :db/ident :event/fn-entry
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :event/fn-return
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :event/fn-caller
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   
   ;;fn args
   {:db/id #db/id[:db.part/db]
    :db/ident :event/fn-args
    :db/valueType :db.type/ref
    :db/isComponent true
    :db/cardinality :db.cardinality/many
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :fn-arg/value
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :fn-arg/position
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}])

(defn destroy-db! [uri]
  (when (d/delete-database uri)
    (reset! event-counter 0)))

(defn memory-connection
  "Create a connection to an anonymous, in-memory database."
  []
  (let [uri (str db-uri-base (d/squuid))]
    (destroy-db! uri)
    (d/create-database uri)
    (let [conn (d/connect uri)]
      @(d/transact conn schema)
      uri)))

(defn- edn-str [x]
  (binding [*print-dup* true]
    (pr-str x)))

(defmulti to-transactions :type)

(defmethod to-transactions :fn-call
  [e]
  [(merge
    {:db/id #db/id[:db.part/user]
     :event/type :fn-call
     :event/id (str (:id e))
     :event/timestamp (:timestamp e)
     :event/fn-name (str (:fn-name e))
     :event/ns (str (:ns e))
     :event/thread (:thread e)}
    (when (:fn-caller e)
      {:event/fn-caller [:event/id (:fn-caller e)]})
    (when (seq (:args e))
      {:event/fn-args (map (fn [pos val]
                             {:fn-arg/position pos
                              :fn-arg/value (edn-str val)})
                           (range) (:args e))}))])

(defn- return-id->call-id [id]
  (str "c" (subs id 1)))

(defmethod to-transactions :fn-return
  [e]
  (let [id (str (:id e))
        call-event-id (return-id->call-id id)]
    [{:db/id #db/id[:db.part/user -1]
      :event/type :fn-return
      :event/id id
      :event/timestamp (:timestamp e)
      :event/fn-name (str (:fn-name e))
      :event/ns (str (:ns e))
      :event/thread (:thread e)
      :event/return-value (edn-str (:return-value e))
      :event/fn-entry [:event/id call-event-id]}
     {:db/id [:event/id call-event-id]
      :event/fn-return #db/id[:db.part/user -1]}]))

(defmethod print-dup :default [o w] (print-method o w))

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
        (map (comp read-edn-string :fn-arg/value)))))

(defmethod deserialise :fn-return [e]
  (assoc
   (into {} e)
   :event/return-value
   (-> e :event/return-value read-edn-string)))


(defn event-channel
  [uri]
  (let [conn    (d/connect uri)
        channel (async/chan 1024)]
    (thread
      (trace/without-recording ;;dynamic binding is thread-local so we need to say this once more here
       (loop []
         (when-let [event (<!! channel)]
           (try
             @(d/transact conn (to-transactions event))
             (swap! event-counter inc)
             (catch Exception e
               (println "ERROR" (.getMessage e))))
           (recur))))
      ;;TODO do we need to close the connection here?
      )
    channel))

(defn clear-db! [conn]
  ;;TODO
  )

(comment
  (def conn (memory-connection))
  (def ch (event-channel conn))

  (async/>!! ch {:type :fn-call :id "t999" :fn-name "function-name" :ns "positano.core" :args [1 2 3] :timestamp (java.util.Date.)})
  
  @(d/transact
    conn
    [{:db/id #db/id[:db.part/user]
      :event/type :fn-call
      :event/id "t999"
      :event/timestamp (java.util.Date.)
      :event/fn-name "foo"
      :event/ns "positano.core"
      :event/fn-args [{:fn-arg/position 0 :fn-arg/value "9"}
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
