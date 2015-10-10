(ns positano.reflect-test.fun)

(def ooo 999)

(defn aa
  ([x y [a b]] 0)
  ([x y z [a b]] 0))

(def bb (fn [x y [a b]] 0))

(def cc
  (fn
    ([x y [a b]] 0)
    ([x y z [a b]] 0)))
