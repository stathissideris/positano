(ns positano.analyze-test
  (:require [positano.analyze :refer :all]
            [clojure.test :refer :all]
            [datascript.core :as d]
            [positano.core :as core]
            [clojure.tools.analyzer.jvm :as ana]
            [clojure.pprint :refer [pprint]]
            ))

(defn- query [conn q]
  (d/q q @conn query-rules))

(defn analyze [fragment]
  (let [ast (ana/analyze fragment)
        conn (core/init-db!)]
    (d/transact! conn [(fix-ast ast)])
    conn))

(deftest test-analyze-fragments
  (testing "invoke clojure core"
    (let [conn (analyze '(drop 1 [1 2 3 4]))]
      (is
       (= #{[#'clojure.core/drop]}
          (query conn '[:find ?var
                        :in $ %
                        :where
                        (invoke-var ?var)])))))
  (testing "arg-lists invoke"
    (let [conn (analyze '(defn arg-lists
                           "fun should be a symbol that resolves to a function. Does not work if you load a file with cider-load-buffer (C-c C-k)"
                           [fun]
                           (let [var (resolve fun)]
                             (cond
                               (nil? var)
                               (throw (ex-info (str "Cannot resolve " fun) {}))

                               (not (fn? (var-get var)))
                               (throw (ex-info (str "Value of " var " is not a function") {}))

                               :else
                               (or (some-> var meta :arglists)
                                   (let [source (some-> fun read-string)]
                                     (when (and (= 'def (first source))
                                                (= 'fn (ffirst (drop 2 source))))
                                       (let [method-form (first (drop 2 source))]
                                         (if (vector? (second method-form))
                                           (list (second method-form))
                                           (map first (rest method-form)))))))))))]
      (testing "can find invoke ops"
        (is
         (< 0
            (count
             (query conn '[:find ?in
                           :in $ %
                           :where
                           [?in :op :invoke]])))))
      (testing "can find invoke ops"
        (is
         (< 0
            (count
             (query conn '[:find ?var
                           :in $ %
                           :where
                           (invoke-var ?var)])))))))
  (testing "invoke locals"
    (let [conn (analyze
                '(let [a identity
                       b identity
                       x 10]
                   (if (> x 5)
                     (a 1 2 3)
                     (b 4 5 6))))]
      (testing "can find invoke ops"
        (is
         (< 0
            (count
             (query conn '[:find [(pull ?in [*]) ...]
                           :in $ %
                           :where
                           [?in :op :invoke]])))))
      (testing "can find invoke locals"
        (is
         (= #{['a__#0 'a] ['b__#0 'b]}
            (query conn '[:find ?name ?form
                          :in $ %
                          :where
                          (invoke-local ?fn)
                          (?fn :name ?name)
                          (?fn :form ?form)])))))))

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
