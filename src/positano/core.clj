(ns positano.core
  (:require [datomic.api :as d]
            [positano.trace :as trace]
            [positano.query :as q]
            [clojure.string :as string]))

(trace/deftrace baz [x]
  (inc x))

(trace/deftrace bar [x]
  (* (baz (/ x 2.0)) 3))

(defn foo
  "I don't do a whole lot."
  [x]
  (println (string/join "," ["Hello" " World!"]))
  (bar (first x)))

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
