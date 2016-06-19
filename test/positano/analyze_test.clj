(ns positano.analyze-test
  (:require [positano.analyze :refer :all]
            [clojure.test :refer :all]
            [datascript.core :as d]
            [positano.core :as core]))

(defn- query [conn q]
  (d/q q @conn query-rules))

(deftest test-analyze
  (let [conn (core/init-db!)]

    (def cc conn)

    (testing "analyze all code"
      (analyze-dir! conn "src"))
    (testing "top level defs"
      (is
       (< 0 (count
             (query conn '[:find ?ns ?name
                           :in $ %
                           :where
                           (def ?def ?name)
                           (ns ?def ?ns)])))))
    (testing "top level functions"
      (is
       (< 0 (count
             (query conn '[:find ?ns ?name
                           :in $ %
                           :where
                           (top-level-fn ?def ?name)
                           (ns ?def ?ns)])))))
    (testing "top level memoized functions"
      (is
       (< 0
          (count
           (query conn (resolve-vars
                        '[:find ?ns ?name
                          :in $ %
                          :where
                          (def ?def ?name)
                          [?def :init ?memoize]

                          [?memoize :op :invoke]
                          [?memoize :fn ?memoize-fn]
                          [?memoize-fn :op :var]
                          [?memoize-fn :var (var clojure.core/memoize)]

                          [?with-meta :expr ?fn]

                          [?fn :op :fn]
                          (ns ?def ?ns)]))))))))
