(ns positano.core
  (:require [datomic.api :as d]
            [positano.trace :as trace]
            [positano.query :as q]))

(trace/deftrace baz [x]
  (inc x))

(trace/deftrace bar [x]
  (* (baz x) 3))

(trace/deftrace foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!")
  (bar x))

(comment
  (def conn (trace/init-db))
  (foo 10)
  (def r
    (let [db (d/db conn)
          res (d/q '[:find ?e :where [?e :event/type _]] db)]
      (for [r res]
        (d/touch (d/entity db (first r))))))
  (def r
    (let [db (d/db conn)
          res (d/q '[:find ?e :where [?e :event/fn-name "baz"]] db)]
      (for [r res]
        (d/touch (d/entity db (first r))))))

  ;;produces:
  [{:type :fn-call, :id t17298, :fn foo, :args (10)}
   {:type :fn-call, :id t17299, :fn bar, :args (10)}
   {:type :fn-return, :id t17299, :fn bar, :value 30}
   {:type :fn-return, :id t17298, :fn foo, :value 30}])
