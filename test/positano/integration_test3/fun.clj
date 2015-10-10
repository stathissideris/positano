(ns positano.integration-test3.fun
  (:require [positano.trace :as trace]
            [clojure.string :as string]))

(trace/deftrace baz [x]
  (inc x))

(trace/deftrace bar [x]
  (* (baz (/ x 2.0)) 3))

(trace/deftrace foo
  "I don't do a whole lot."
  [x]
  (println (string/join ", " ["Hello" "World!"]))
  (bar (first x)))
