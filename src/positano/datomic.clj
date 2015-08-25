(ns positano.datomic
  (:require [datomic.api :as d]
            [clojure.core.async :as async :refer [<!! thread]]))

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

(defn memory-connection
  "Create a connection to an anonymous, in-memory database."
  []
  (let [uri (str db-uri-base (d/squuid))]
    (d/delete-database uri)
    (d/create-database uri)
    (let [conn (d/connect uri)]
      @(d/transact conn schema)
      conn)))

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
                              :fn-arg/value (pr-str val)})
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
      :event/return-value (pr-str (:return-value e))
      :event/fn-entry [:event/id call-event-id]}
     {:db/id [:event/id call-event-id]
      :event/fn-return #db/id[:db.part/user -1]}]))

(defn event-channel
  [conn]
  (let [channel (async/chan)]
    (thread
      (loop []
        (try
          @(d/transact conn (to-transactions (<!! channel)))
          (catch Exception e
            (println "ERROR" (.getMessage e))))
        (recur)))
    channel))

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
