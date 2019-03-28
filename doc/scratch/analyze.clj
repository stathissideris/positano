(require '[positano.analyze :refer :all]
         '[clojure.tools.analyzer.ast.query :as ast.query]
         '[refactor-nrepl.analyzer]
         ;'[trinket.repl :as t]
         '[clojure.pprint :as pp]
         '[clojure.java.io :as io])

(def ast (analyze-ns 'positano.reflect))
;; OR
(def ast (analyze-file "src/positano/reflect.clj"))

(def ast (analyze-dir "/Users/sideris/devel/work/gt/taz/src/"))

(ast-q
 '[:find ?ns ?name
   :in $ %
   :where
   (def ?def ?name)
   (ns ?def ?ns)]
 ast)

(ast-q
 '[:find ?def
   :in $ %
   :where
   [?def :op :def]]
 ast)

(d/q '[:find ?a
       :in $
       :where [?m :b ?a]]
     [[{:a 10 :b 20} :a 10]
      [{:a 10 :b 20} :b 20]
      [{:a 11 :b 21} :a 11]
      [{:a 11 :b 21} :b 21]])

(ast-q
 '[:find ?name
   :in $ %
   :where
   (def ?def ?name)]
 ast)

(ast-q
 '[:find ?def
   :in $ %
   :where
   (def ?def ?name)]
 ast)

(ast-q
 '[:find ?name ?doc
   :in $ %
   :where
   (def ?def ?name)
   [?meta :val ?val]
   [?val :doc ?doc]]
 ast)

(ast-q
 '[:find ?ns ?name
   :in $ %
   :where
   (def ?def ?name)
   (ns ?def ?ns)
   (private ?def)]
 ast)

(assert
 (=
  (ast-q
   '[:find ?ns ?name
     :in $ %
     :where
     (def ?def ?name)
     (ns ?def ?ns)
     (not (private ?def))] ;; NEGATION AT LAST
   ast)

  (ast-q
   '[:find ?ns ?name
     :in $ %
     :where
     (def ?def ?name)
     (ns ?def ?ns)
     (public ?def)]
   ast)
  ))

(ast-q
 '[:find ?op
   :in $ %
   :where
   (def ?def 'flatten-with-maps)
   [?op :op ?op-type]
   [?def :child ?op]]
 ast)

;; Find the top var in the body of the implementation.
;; seq is here to force pretty printing
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
  ast))

(seq
 (ast-q
  '[:find ?child
    :in $ %
    :where
    (def ?def ?name)
    [(= ?name "flatten-with-maps")]
    (parent ?def ?child)]
  ast))

;;all invoked vars in function
(seq
 (ast-q
  '[:find ?var
    :in $ %
    :where
    (def ?def ?name)
    [(= ?name "flatten-with-maps")]
    (ancestor ?def ?invoke)
    (invoke-var ?invoke ?var)]
  ast))

;;all invoked vars in function
(seq
 (ast-q
  '[:find ?var
    :in $ %
    :where
    (def ?def ?name)
    [(= ?name "flatten-with-maps")]
    (caller ?def ?var)]
  ast))

;;who calls clojure.core/filter ?
(seq
 (ast-q
  '[:find ?name
    :in $ %
    :where
    (def ?def ?name)
    (caller ?def "clojure.core/filter")]
  ast))

;;all invoked vars
(seq
 (ast-q
  '[:find ?var
    :in $ %
    :where
    [?op :op :invoke]
    [?op :fn ?fn]
    [?fn :var ?var]]
  ast))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;                            taz                             ;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(do
  (def ast (analyze-dir "/Users/sideris/devel/work/gt/taz/src/"))
  (def the-db (doall (db ast))))

(->>
 (ast-q
  '[:find ?k
    :in $ %
    :where
    (keyword ?op ?k)]
  the-db)
 (map first)
 distinct
 (filter namespace)
 sort)

(def slurp-code
  (memoize
   (fn [filename]
     (with-open [r (io/reader filename)]
       (doall (line-seq r))))))

(defn- extract-line [filename line-number]
  (let [code-seq (slurp-code filename)]
    (nth code-seq (dec line-number))))

(->>
 (ast-q
  '[:find ?k ?file ?line
    :in $ %
    :where
    (keyword ?op ?k)
    (file ?op ?file)
    (line ?op ?line)]
  the-db)
 (map (partial zipmap [:k :file :line]))
 (map (fn [{:keys [file line] :as row}]
        (assoc row :snippet (extract-line file line))))
 (sort-by (juxt :file :line))
 pp/print-table)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;                         dashboard                          ;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ast (analyze-dir "/Users/sideris/devel/work/bsq/vittle-dashboard/src"))
(def the-db (doall (db ast)))

(sort-by
 first
 (ast-q
  '[:find ?ns ?name
    :in $ %
    :where
    (def ?def ?name)
    (ns ?def ?ns)]
  the-db))

(seq
 (ast-q
  '[:find ?var
    :in $ %
    :where
    (def ?def ?name)
    [(= ?name "list-execution-events")]
    (ancestor ?def ?invoke)
    (or (invoke-var ?invoke ?var)
        (static-call ?invoke ?var))]
  the-db))

(seq
 (ast-q
  '[:find ?var
    :in $ %
    :where
    (def ?def ?name)
    [(= ?name "list-execution-events")]
    (caller ?def ?var)]
  the-db))

(seq
 (ast-q
  '[:find ?var
    :in $ %
    :where
    (def ?def ?name)
    [(= ?name "tabular-executions")]
    (caller ?def ?var)]
  the-db))

;;get all keywords
(seq
 (ast-q
  '[:find ?k
    :in $ %
    :where
    (keyword ?op ?k)]
  the-db))

;;get all keywords used as functions (and where)
(->>
 (ast-q
  '[:find ?k ?file ?line
    :in $ %
    :where
    (keyword-invoke ?op ?k)
    (file ?op ?file)
    (line ?op ?line)]
  the-db)
 (map (partial zipmap [:k :file :line]))
 (sort-by (juxt :file :line))
 pp/print-table)
