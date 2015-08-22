(ns positano.core
  (:require [positano.trace :as trace]))

(trace/deftrace bar [x]
  (* x 3))

(trace/deftrace foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!")
  (bar x))

(comment
  (foo 10)
  (in-ns 'positano.trace)
  (clojure.pprint/pprint @DB)

  ;;produces:
  [{:type :fn-call, :id t17298, :fn foo, :args (10)}
   {:type :fn-call, :id t17299, :fn bar, :args (10)}
   {:type :fn-return, :id t17299, :fn bar, :value 30}
   {:type :fn-return, :id t17298, :fn foo, :value 30}])
