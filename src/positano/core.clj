(ns positano.core
  (:require [datascript.core :as d]
            [clojure.core.async :as async :refer [>!!]]
            [positano.trace :as trace :refer [deftrace]]
            [positano.db :as db]
            [positano.query :as q]
            [clojure.string :as string]))

(defn init-db!
  ([]
   (init-db! {}))
  ([{:keys [event-transducer] :as opts}]
   (let [conn (db/init-connection)]
     (reset! trace/event-channel
             (db/event-channel conn event-transducer))
     conn)))

(defn stop-db! [conn]
  (async/close! @trace/event-channel)
  (db/destroy-db! conn))

(defn clear-db! [conn]
  (db/clear-db! conn))

(comment
  (do
    (deftrace baz [x]
      (+ x 10))
    
    (deftrace bar [x]
      (baz (* 2 x)))
    
    (deftrace foo [b x c d]
      (bar (inc x)))
    
    (def conn (init-db!)))
  
  (foo 5 10 20 40)

  (def r
    (let [db (d/db conn)
          res (d/q '[:find ?e :where [?e :event/fn-name _]] db)]
      (for [r res]
        (d/touch (d/entity db (first r))))))

  (-> r first q/stack q/print-stack)

  (db/destroy-db! uri))

;;prints:

;;  (foo 5 10 20 40) b=5 x=10 c=20 d=40
;;  ⃓ (bar 11) x=11
;;  ⃓ ⃓ (baz 22) x=22
;;  ⃓ ⃓ └ 32 ⌚:1msec
;;  ⃓ └ 32 ⌚:1msec
;;  └ 32 ⌚:2msec
