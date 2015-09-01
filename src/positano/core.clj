(ns positano.core
  (:require [datomic.api :as d]
            [clojure.core.async :as async :refer [>!!]]
            [positano.trace :as trace]
            [positano.db :as db]
            [positano.query :as q]
            [clojure.walk :as walk]
            [clojure.string :as string]))

(defn init-db! []
  (let [uri (db/memory-connection)]
    (reset! trace/event-channel (db/event-channel uri))
    uri))

(defn stop-db! [uri]
  (async/close! @trace/event-channel)
  (db/destroy-db! uri))

(defn clear-db! [conn]
  (db/clear-db! conn))


(comment
  (def uri (init-db!)))

(comment
  (def uri (trace/init-db!))
  (def conn (d/connect db))
  (foo [5 10 20 40])

  (def r
    (let [db (d/db conn)
          res (d/q '[:find ?e :where [?e :event/fn-name _]] db)]
      (for [r res]
        (d/touch (d/entity db (first r))))))

  (-> r first q/stack q/print-stack)

  (db/destroy-db! uri)
  
  ;;prints:

  ;; (foo [5 10 20 40])
  ;;   (bar 5)
  ;;     (baz 2.5)
  ;;     => 3.5 -- 0msec
  ;;   => 10.5 -- 4msec
  ;; => 10.5 -- 6msec  

  )
