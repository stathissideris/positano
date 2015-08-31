(ns positano.integration-test4.fun)

(defn baz [x]
  (inc x))

(defn bar [x]
  (* (baz (/ x 2.0)) 3))

(defn foo
  "I don't do a whole lot."
  [x]
  (println "Hello, World!")
  (bar (first x)))
