(ns positano.event-transformation-test.fun
  (:require [positano.trace :as trace :refer [deftrace]]))

(deftrace baz [x]
  (inc x))

(deftrace bar [x]
  (* (baz (/ x 2.0)) 3))

(deftrace foo
  "I don't do a whole lot."
  [x]
  (println "Hello World!")
  (bar (first x)))
