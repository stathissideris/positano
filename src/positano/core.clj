(ns positano.core
  (:require [datomic.api :as d]
            [positano.trace :as trace]
            [positano.query :as q]))

(trace/deftrace baz [x]
  (inc x))

(trace/deftrace bar [x]
  (* (baz (/ x 2.0)) 3))

(trace/deftrace foo
  "I don't do a whole lot."
  [x]
  (println "Hello, World!")
  (bar (first x)))

(comment
  (def conn (trace/init-db))
  (foo [5 10 20 40])

  (def r
    (let [db (d/db conn)
          res (d/q '[:find ?e :where [?e :event/fn-name "baz"]] db)]
      (for [r res]
        (d/touch (d/entity db (first r))))))

  (-> r first q/stack q/print-stack)

  ;;prints:

  ;; (foo [5 10 20 40])
  ;;   (bar 5)
  ;;     (baz 2.5)
  ;;     => 3.5
  ;;   => 10.5
  ;; => 10.5
  )
