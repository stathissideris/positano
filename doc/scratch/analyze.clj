(require '[positano.analyze :refer :all]
         '[clojure.tools.analyzer.ast.query :as ast.query]
         '[clojure.tools.analyzer.jvm :as ana])

(def ast
  (ana/analyze-ns 'positano.reflect))

(-> ast ast.query/db first first pprint)



(ast.query/q
 '[:find ?ns ?name
   :in $ %
   :where
   (def ?def ?name)
   (ns ?def ?ns)]
 ast
 query-rules)

(ast.query/q
 '[:find ?def
   :in $ %
   :where
   [?def :op :def]]
 ast
 query-rules)

(d/q '[:find ?a
       :in $
       :where [?m :b ?a]]
     [[{:a 10 :b 20} :a 10]
      [{:a 10 :b 20} :b 20]
      [{:a 11 :b 21} :a 11]
      [{:a 11 :b 21} :b 21]])

(ast-query
 '[:find ?name
   :in $ %
   :where
   (def ?def ?name)]
 ast)

(ast-query
 '[:find ?def
   :in $ %
   :where
   (def ?def ?name)]
 ast)

(pprint (first (ast-query
                '[:find ?def
                  :in $ %
                  :where
                  (def ?def ?name)]
                ast)))

(pprint
 (take
  3
  (ast-query
   '[:find ?name
     :in $ %
     :where
     [?def :meta ?meta]
     [?meta :doc ?name]]
   ast)))

(pprint
 (take
  3
  (d/q
   '[:find ?ns
     :in $ %
     :where
     [?def :meta ?meta]
     [?meta :ns ?ns]]
   (db ast)
   query-rules)))

(pprint
 (seq
  (d/q
   '[:find ?ns ?name
     :in $ %
     :where
     (def ?def ?name)
     [?def :env ?env]
     [?env :ns ?ns]]
   (db ast)
   query-rules)))

(pprint
 (seq
  (d/q
   '[:find ?ns ?name
     :in $ %
     :where
     (def ?def ?name)
     (ns ?def ?ns)]
   (db ast)
   query-rules)))

(pprint
 (seq
  (ast-q
   '[:find ?ns ?name ?meta
     :in $ %
     :where
     (def ?def ?name)
     (ns ?def ?ns)
     (?def :meta ?meta)]
   ast)))

(pprint
 (seq
  (ast-q
   '[:find ?ns ?name
     :in $ %
     :where
     (def ?def ?name)
     (ns ?def ?ns)
     (private ?def)]
   ast)))

(pprint
 (seq
  (ast-q
   '[:find ?ns ?name
     :in $ %
     :where
     (def ?def ?name)
     (ns ?def ?ns)
     (not (private ?def))] ;; NEGATION AT LAST
   ast)))

(pprint
 (seq
  (ast-q
   '[:find ?ns ?name
     :in $ %
     :where
     (def ?def ?name)
     (ns ?def ?ns)
     (public ?def)]
   ast)))

(pprint
 (seq
  (ast-q
   '[:find ?op
     :in $ %
     :where
     (def ?def 'flatten-with-maps)
     [?op :op ?op-type]
     [?def :chilxd ?op]]
   ast)))

(pprint
 (seq
  (ast-q
   '[:find ?def
     :in $ %
     :where
     (def ?def ?name)
     [(= ?name "flatten-with-maps")]]
   ast)))

(pprint
 (seq
  (ast-q
   '[:find ?var
     :in $ %
     :where
     (def ?def ?name)
     [(= ?name "flatten-with-maps")]
     [?def :init ?init]
     [?init :expr ?expr]
     [?expr :methods ?methods]
     [?methods :body ?body]
     [?body :fn ?fn]
     [?fn :var ?var]]
   ast)))

(pprint
 (seq
  (ast-q
   '[:find ?child
     :in $ %
     :where
     (def ?def ?name)
     [(= ?name "flatten-with-maps")]
     [parent ?def ?child]]
   ast)))

;;all invoked vars in function
(pprint
 (seq
  (ast-q
   '[:find ?var
     :in $ %
     :where
     (def ?def ?name)
     [(= ?name "flatten-with-maps")]
     [ancestor ?def ?invoke]
     [invoke-var ?invoke ?var]]
   ast)))

(pprint
 (seq
  (ast-q
   '[:find ?var
     :in $ %
     :where
     (def ?def ?name)
     [(= ?name "flatten-with-maps")]
     [caller ?def ?var]]
   ast)))

;;who calls clojure.core/filter ?
(ast-q
 '[:find ?name
   :in $ %
   :where
   (def ?def ?name)
   [caller ?def "clojure.core/filter"]]
 ast)

;;all invoked vars
(pprint
 (seq
  (ast-q
   '[:find ?var
     :in $ %
     :where
     [?op :op :invoke]
     [?op :fn ?fn]
     [?fn :var ?var]]
   ast)))

;;;;
;; if you analyze this:
;; (let [var (resolve fun)] ...)

;; you get something like this:
{:op :let
 :bindings [{:form 'x
             :name 'x__#0
             :init {:op   :invoke
                    :fn   {:op  :var
                           :var #'clojure.core/resolve}
                    :args [{:op   :local
                            :form 'fun}]}}]}
