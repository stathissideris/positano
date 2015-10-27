(ns positano.analyze-test
  (:require [clojure.test :refer :all]
            [positano.analyze :refer :all]
            [clojure.tools.analyzer.jvm :as ana]
            [clojure.zip :as zip]
            [datascript.core :as d]))

(defn nexts [zipper times]
  (last (take (inc times) (iterate zip/next zipper))))

(deftest test-generic-zipper
  (let [x [1 2 [5 6 {:a 10 :b 20} 7 8] 3 4]]
    (is (= 2 (-> x generic-zipper zip/next zip/next zip/node)))
    (is (= [1 2 :new 3 4]
           (-> x generic-zipper zip/next zip/next zip/next (zip/replace :new) zip/root)))

    ;;dive in and just go back to the root
    (is (= x (-> x generic-zipper zip/next zip/next zip/next zip/root)))

    ;;replace the key of a map
    (is (= [1 2 [5 6 {:new-key 10 :b 20} 7 8] 3 4]
           (-> x generic-zipper (nexts 8) (zip/replace :new-key) zip/root)))

    ;;replace both keys at the same time:
    (is (= [1 2 [5 6 {:new-key 10 :new-key2 20} 7 8] 3 4]
           (-> x generic-zipper
               (nexts 8) (zip/replace :new-key)
               (nexts 3) (zip/replace :new-key2)
               zip/root)))
    (is (= [1 2 [5 6 {:new-key 10 :new-key2 100} 7 8] 3 4]
           (-> x generic-zipper
               (nexts 8) (zip/replace :new-key) ;;replace key
               (nexts 2) (zip/replace [:new-key2 100]) ;;replace key-pair
               zip/root)))))

(deftest transact-analyze-output
  (let [process (fn [form] (->> form ana/analyze to-transaction (d/transact (d/create-conn))))]
    (is (process '(defn foo#
                    "I don't do a whole lot."
                    ([] 0)
                    ([x]
                     (+ x 1)
                     (* x 2)))))))
