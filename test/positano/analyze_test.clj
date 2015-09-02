(ns positano.analyze-test
  (:require [positano.analyze :refer :all]
            [clojure.test :refer :all]))

(deftest test-arg-names
  (is (= '(x y a b) (arg-names '[x y a b])))
  (is (= '(x y a b) (arg-names '[x y [a b]])))
  (is (= '(x y a b) (arg-names '[x y {:keys [a b]}])))
  (is (= '(x y a b) (arg-names '[x y & {:keys [a b]}]))))

(deftest test-param-extractor-fn-form
  (is (= '(clojure.core/fn param-extractor-fn
            ([x y [a b]] {'x x, 'y y, 'a a, 'b b})
            ([x y z [a b]] {'x x, 'y y, 'z z, 'a a, 'b b}))
         (param-extractor-fn-form '[[x y [a b]] [x y z [a b]]]))))

(deftest test-apply-arg-lists
  (let [arg-lists '[[x y [a b]] [x y z [a b]]]]
    (is (= '{x 1, y 2, a :aa, b :bb}
           (apply-arg-lists arg-lists [1 2 [:aa :bb]])))
    (is (= '{x 1, y 2, z 3, a :aa, b :bb}
           (apply-arg-lists arg-lists [1 2 3 [:aa :bb]]))))
  (let [arg-lists '[[x [a b]] [x y z & rest]]]
    (is (= '{x 1, a :aa, b :bb}
           (apply-arg-lists arg-lists [1 [:aa :bb]])))
    (is (= '{x 1, y 2, z 3, rest (:aa :bb)}
           (apply-arg-lists arg-lists [1 2 3 :aa :bb])))
    (is (= '{x 1, y 2, z 3, rest nil}
           (apply-arg-lists arg-lists [1 2 3])))
    (is (thrown? clojure.lang.ArityException (apply-arg-lists arg-lists [1])))
    (is (thrown? UnsupportedOperationException (apply-arg-lists arg-lists [1 2])))))
