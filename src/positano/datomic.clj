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
    :db/ident :event/return-value
    :db/valueType :db.type/ref
    :db/isComponent true
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
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

(defn event-channel
  [conn]
  (let [channel (async/chan)]
    (thread
      (loop []
        @(d/transact
          conn
          [[:db/add
            (d/tempid :db.part/user)
            :db/doc
            "Hello world"]])
        (<!! channel)
        (recur)))
    channel))

(comment
  (def conn (memory-connection))
  
  @(d/transact
    conn
    [[:db/add
      (d/tempid :db.part/user)
      :db/doc
      "Hello world"]])

  @(d/transact
    conn
    [{:db/id #db/id[:db.part/user]
       :event/type :fn-call
       :event/id "t999"
       :event/fn-name "foo"
       :event/ns "positano.core"
       :event/fn-args [{:fn-arg/position 0 :fn-arg/value "9"}
                       {:fn-arg/position 1 :fn-arg/value "[1 2]"}]}])
  
  ;;;;

  (def db (d/db conn))
  
  (def q-result
    (d/q '[:find ?e .
           :where [?e :db/doc "Hello world"]]
         db))

  (def ent (d/entity db q-result))

  (d/touch ent)

  ;;;;

  (def q-result
    (d/q '[:find ?e .
           :where [?e :event/id "t999"]]
         (d/db conn)))
  
  @(d/transact
    conn
    [[:db/add
      (d/tempid :db.part/user)
      :event/type :fn-call
      :event/id 't17298
      :event/fn 'foo
      :event/args '(10)]])

  @(d/transact
    conn
    {:db/id #db/id [:db.part/user]
     :story/title "Teach Yourself Programming in Ten Years"
     :story/url "http://norvig.com/21-days.html"}))
