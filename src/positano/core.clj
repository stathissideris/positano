(ns positano.core
  (:require [positano.trace :as trace]
            [datomic.api :as d]))

(trace/deftrace bar [x]
  (* x 3))

(trace/deftrace foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!")
  (bar x))

(comment
  (def conn (trace/init-datomic))
  (foo 10)
  (def r
    (let [db (d/db conn)
          res (d/q '[:find ?e :where [?e :event/type :fn-call]] db)]
      (for [r res]
        (d/touch (d/entity db (first r))))))

  ;;produces:
  [{:type :fn-call, :id t17298, :fn foo, :args (10)}
   {:type :fn-call, :id t17299, :fn bar, :args (10)}
   {:type :fn-return, :id t17299, :fn bar, :value 30}
   {:type :fn-return, :id t17298, :fn foo, :value 30}])
