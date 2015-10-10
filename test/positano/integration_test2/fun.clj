(ns positano.integration-test2.fun
  (:require [positano.trace :as trace]))

(trace/deftrace baz [x]
  (inc x))

(trace/deftrace bar [x]
  (* (baz (/ x 2.0)) 3))

(defn foo
  "I don't do a whole lot."
  [x]
  (println "Hello World!")
  (bar (first x)))
