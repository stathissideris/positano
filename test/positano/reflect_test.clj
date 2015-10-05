(ns positano.reflect-test
  (:require [positano.reflect :refer :all]
            [clojure.test :refer :all]))

(def ooo 999)

(defn aa
  ([x y [a b]] 0)
  ([x y z [a b]] 0))

(def bb (fn [x y [a b]] 0))

(def cc
  (fn
    ([x y [a b]] 0)
    ([x y z [a b]] 0)))

(deftest test-arg-lists
  (is (thrown? Exception (arg-lists 'non-existent)))
  (is (thrown? Exception (arg-lists 'ooo)))
  (is (= '[[x y [a b]] [x y z [a b]]] (arg-lists 'aa)))
  (is (= '[[x y [a b]]] (arg-lists 'bb)))
  (is (= '[[x y [a b]] [x y z [a b]]] (arg-lists 'cc))))

(deftest test-arg-names
  (is (= '(x y a b) (arg-names '[x y a b])))
  (is (= '(x y a b) (arg-names '[x y [a b]])))
  (is (= '(x y a b) (arg-names '[x y {:keys [a b]}])))
  (is (= '(x y a b) (arg-names '[x y & {:keys [a b]}]))))

(deftest test-param-extractor-fn-form
  (is (= '(clojure.core/fn param-extractor-fn
            ([x y [a b]] [['x x] ['y y] ['a a] ['b b]])
            ([x y z [a b]] [['x x] ['y y] ['z z] ['a a] ['b b]]))
         (param-extractor-fn-form '[[x y [a b]] [x y z [a b]]]))))

(deftest test-bind-arg-lists
  (let [arg-lists '[[x y [a b]] [x y z [a b]]]]
    (is (= '[[x 1] [y 2] [a :aa] [b :bb]]
           (bind-arg-lists arg-lists [1 2 [:aa :bb]])))
    (is (= '[[x 1] [y 2] [z 3] [a :aa] [b :bb]]
           (bind-arg-lists arg-lists [1 2 3 [:aa :bb]]))))
  (let [arg-lists '[[x [a b]] [x y z & rest]]]
    (is (= '[[x 1] [a :aa] [b :bb]]
           (bind-arg-lists arg-lists [1 [:aa :bb]])))
    (is (= '[[x 1] [y 2] [z 3] [rest (:aa :bb)]]
           (bind-arg-lists arg-lists [1 2 3 :aa :bb])))
    (is (= '[[x 1] [y 2] [z 3] [rest nil]]
           (bind-arg-lists arg-lists [1 2 3])))
    (is (thrown? clojure.lang.ArityException (bind-arg-lists arg-lists [1])))
    (is (thrown? UnsupportedOperationException (bind-arg-lists arg-lists [1 2])))))
