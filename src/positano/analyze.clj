(ns positano.analyze
  (:require [clojure.walk :as walk]
            [clojure.tools.analyzer.jvm :as ana]
            [clojure.tools.analyzer.passes.jvm.emit-form :as e]
            [clojure.tools.analyzer.ast :as ast]
            [clojure.tools.analyzer.ast.query :as ast.query]
            [refactor-nrepl.analyzer]
            [clojure.pprint :refer [pprint]]
            ;;[datascript.core :as d]
            [datomic.api :as d]
            [positano.core :as core])
  (:import [java.io File]))

(def query-rules
  '[[(def ?def ?name)
     [?def :op :def]
     [?def :name ?symbol-name]
     [(str ?symbol-name) ?name]] ;;because it's harder to query symbols

    [(top-level-fn-or-macro ?def ?name)
     (def ?def ?name)
     [?def :init ?with-meta]
     [?with-meta :op :with-meta]
     [?with-meta :expr ?fn]
     [?fn :op :fn]]

    [(macro ?def ?name)
     (top-level-fn-or-macro ?def ?name)
     (?do :statements ?def)
     (?do :op :do)
     (?do :statements ?set-macro)
     (?set-macro :op :instance-call)
     ;;(?set-macro :method 'setMethod) ;;TODO not possible with symbols
     ;;TODO also match order when available
     (?do :ret ?ret)
     (?ret :op :the-var)]

    [(top-level-fn ?def ?name) ;;TODO fix this to not match macros
     (def ?def ?name)
     [?def :init ?with-meta]
     [?with-meta :op :with-meta]
     [?with-meta :expr ?fn]
     [?fn :op :fn]]

    [(private ?def)
     [?def :meta ?meta]
     [?meta :val ?val]
     [?val :private true]]

    [(public ?def)
     (not (private ?def))]

    [(ns ?def ?ns)
     [?def :env ?env]
     [?env :ns ?ns]]

    [(invoke-var ?invoke ?var)
     [?invoke :op :invoke]
     [?invoke :fn ?fn]
     [?fn :op :var]
     [?fn :var ?var]]

    [(invoke-local ?invoke ?fn)
     [?invoke :op :invoke]
     [?invoke :fn ?fn]
     [?fn :op :local]]

    [(env ?form ?env)
     [?form :env ?env]]])

(defn- weird-core-async-map? [m]
  (try
    (:a m)
    false
    (catch Exception e
      true)))

(defn walk-select-keys [m ks]
  (walk/prewalk
   (fn [x]
     (cond (not (map? x)) x
           (weird-core-async-map? x) {}
           :else (select-keys x ks)))
   m))

(defn walk-dissoc [m ks]
  (walk/prewalk
   (fn [x] (if-not (map? x) x
                   (apply dissoc x ks))) m))

(defn bounds [node]
  (select-keys (:env node) [:line :end-line :column :end-column]))

(defn readable [node]
  (walk-select-keys

   node
   [:op :init :methods :body :statements :val :children :bindings :test :then :else :keyword :target :form
    :args :name :ret :method :params :variadic? :fixed-arity :expr
    :meta :fn :private :var
    :env :ns :file :line :end-line :column :end-column
    ]))

(defn replace-nils [m replacement]
  (walk/prewalk (fn [x] (if (nil? x) replacement x)) m))

(defn resolve-vars
  "Replaces (var x) with the resolved var in the passed datalog query."
  [query]
  (walk/prewalk (fn [x] (if (and (list? x)
                                 (= 'var (first x)))
                          (resolve (second x))
                          x)) query))

(defn- rename-key [m k new-k]
  (if-let [value (get m k)]
    (-> m (dissoc k) (assoc new-k value))
    m))

(defn- rename-meta-val [ast]
  (walk/prewalk
   (fn [x]
     (cond (not (map? x)) x
           (weird-core-async-map? x) {}
           (:meta x) (update x :meta #(rename-key % :val :meta-val))
           :else x)) ast))

(defn fix-ast [ast]
  (-> ast
      readable
      rename-meta-val
      (replace-nils ::nil)))

(defn analyze-dir! [conn dir]
  (doseq [f (file-seq (File. dir))]
    (when-not (.isDirectory f) ;;maybe check extension too
      (println "Analyzing" (.getPath f))
      (let [ast (refactor-nrepl.analyzer/ns-ast (slurp f))]
        (d/transact conn (fix-ast ast))))))

(defn ast-q [q ast]
    (d/q q (db ast) query-rules))

(defn ast->eav
  "Returns an EAV representation of the current AST that can be used by
   Datomic's Datalog. Produces too much data."
  [ast]
  (mapcat (fn [[k v]]
            (if (#{:form :arglists :raw-forms} k)
              []
              (cond (map? v)
                    (into [[ast k v]] (ast->eav v))
                    (coll? v)
                    (mapcat (fn [v]
                              (if-not (coll? v)
                                [[ast k v]]
                                (into [[ast k v]] (ast->eav v)))) v)
                    :else [[ast k v]])))
          ast))

(defn ast->eav*
  "Returns an EAV representation of the current AST that can be used
  by Datomic's Datalog. Produces double the tuples in comparison to
  ast.query/db."
  [ast parent]
  (let [children (set (:children ast))]
    (mapcat (fn [[k v]]
              (if (or (#{:env :meta} k)
                      (and (= parent :meta) (= k :val))
                      (children k))
                (if (map? v)
                  (into [[ast k v]] (ast->eav* v k))
                  (mapcat (fn [v] (into [[ast k v]] (ast->eav* v k))) v))
                [[ast k v]]))
            ast)))

(defn ast->eav
  [ast]
  (ast->eav* ast nil))

(comment
  (db [{:op 100
        :children [:foo :bar :baz]
        :else {:foo 20}
        :other [{:bar 40}
                {:zoo 102}]}]))

(defn db
  "Given a list of ASTs, returns a representation of those
   that can be used as a database in a Datomic Datalog query"
  [asts]
  (mapcat ast->eav asts))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  ;;keys to follow:
  ;;{:op :fn-method :body ... :params [...]}
  ;;{:op :do :statements [...]}
  ;;{:op :static-call :args [{:op :local :name x__#0} {:op :const :val 1}] :method add}
  ;;{:op :let, :body ... :bindings [...]}

  (def xx
    (ana/analyze
     '(defn foo
        "I don't do a whole lot."
        ([] 0)
        ([x]
         (+ x 1)
         (* x 2)))))

  (-> (walk-select-keys
       xx
       [:op :init :methods :body :statements :val :args :name :ret :method :params])
      clojure.pprint/pprint)

  {:op :def
   :name foo
   :init
   {:op :fn
    :methods
    [{:op :fn-method :body {:op :const :val 0} :params []}
     {:op :fn-method
      :body
      {:op :do
       :statements
       [{:op :static-call
         :args [{:op :local :name x__#0} {:op :const :val 1}]
         :method add}]
       :ret
       {:op :static-call
        :args [{:op :local :name x__#0} {:op :const :val 2}]
        :method multiply}}
      :params [{:op :binding :name x__#0}]}]}}

  (def yy
    (ana/analyze
     '(fn [x y & rrr] 0)))

  (-> (walk-select-keys
       yy
       [:op :init :methods :body :statements :val
        :args :name :ret :method :params :variadic? :fixed-arity])
      clojure.pprint/pprint)

  {:op :fn
   :methods
   :variadic? true
   [{:op :fn-method
     :body {:op :const :val 0}
     :variadic? true?
     :fixed-arity 2
     :params
     [{:op :binding :name x__#0 :variadic? false}
      {:op :binding :name y__#0 :variadic? false}
      {:op :binding :name rrr__#0 :variadic? true}]}]}


  (def yy
    (ana/analyze
     '(fn [x y & {:keys [a b c]}] 0)))

  (def yy
    (ana/analyze
     '(fn [x y [a b]] 0)))

  (-> (walk-select-keys
       yy
       [:op :init :methods :body :statements :val
        :args :name :ret :method :params :variadic? :fixed-arity])
      clojure.pprint/pprint)


  {:op :def
   :name 'foo
   :top-level true
   :arglists '([x])
   :env {:ns 'positano.core}
   :init
   {:op :fn
    :val {:doc "I don't do a whole lot."}
    :methods
    [{:op          :fn-method
      :fixed-arity 0 ;;number of fixed arguments
      :arglist     []
      :params      []
      :body        {:op   :const
                    :form 0}}
     {:op          :fn-method
      :fixed-arity 1 ;;number of fixed arguments
      :arglist     '[x]
      :params      [{:op        :binding
                     :form      'x
                     :arg-id    0
                     :variadic? false}]
      :body {:op :do ;;the implicit do of the body
             :statements ;;only one statement, the multiplication is under :ret
             [{:op     :static-call
               :method 'add
               :args   [{:op    :local
                         :local :arg
                         :form  'x}
                        {:op       :const
                         :type     :number
                         :literal? true
                         :val      1
                         :form     1
                         :o-tag    long
                         :tag      long}]}]
             :ret
             {:op     :static-call
              :method 'multiply
              :args   [{:op    :local
                        :local :arg
                        :form  'x}
                       {:op       :const
                        :type     :number
                        :literal? true
                        :val      1
                        :form     1
                        :o-tag    long
                        :tag      long}]}}}]}}

  (-> (ana/analyze
       '(defn foo
          "I don't do a whole lot."
          ([] 0)
          ([x]
           (+ x 1)
           (* x 2))))
      (assoc-in [:init :methods 1 :body :statements] [])
      (e/emit-form))


  (-> (walk-select-keys
       (ana/analyze '(let [a 6 b 7] (+ a b)))
       [:op :init :methods :body :statements :val :children :bindings
        :args :name :ret :method :params :variadic? :fixed-arity])
      clojure.pprint/pprint)

  {:op :let,
   :body
   {:op :static-call,
    :children [:args],
    :args
    [{:op :local, :children [], :name a__#0}
     {:op :local, :children [], :name b__#0}],
    :method add},
   :children [:bindings :body],
   :bindings
   [{:op :binding,
     :init {:op :const, :val 6},
     :children [:init],
     :name a__#0}
    {:op :binding,
     :init {:op :const, :val 7},
     :children [:init],
     :name b__#0}]}


  (defn mini
  ([data]
   (mini data 5))
  ([data max-len]
   (walk/prewalk
    (fn [x]
      (if (and (sequential? x) (not (map-entry? x)))
        (if (> (count x) max-len)
          (into [] (concat (take max-len x) ['... (count x)]))
          x)
        x))
    data)))

  (defn add [a b]
    #dbg
    (do
      (println "foo")
      (println "bar")
      (println "baz")
      (* a a b b)))


  (def file1 (refactor-nrepl.analyzer/ns-ast (slurp "src/positano/reflect.clj")))
  (def file2 (refactor-nrepl.analyzer/ns-ast (slurp "src/positano/utils.clj")))

  (require '[spec-provider.provider :as sp])
  (sp/pprint-specs (sp/infer-specs a ::ast) 'an 's)

  (require '[spec-provider.stats :as stats])
  (def ss (stats/collect a))
  (-> (sp/summarize-stats ss ::ast {})
      (sp/pprint-specs 'positano.analyze 's))

  (require '[clj-memory-meter.core :as mm])
  (mm/measure ss)

  (def a (refactor-nrepl.analyzer/ns-ast (slurp "src/positano/utils.clj"))) ;;this is how clj-refactor does it
  (def a (refactor-nrepl.analyzer/ns-ast (slurp "/Volumes/work/bsq/vittle-analytics/src/monitor/utils.clj")))

  ;;it ultimately calls this, but with a no-op for macroexpansion (TODO: look into this)
  (def b
    (clojure.tools.analyzer.jvm/analyze-ns 'positano.reflect))

  ;;lets try "without" macroexpansion
  (defn noop-macroexpand-1 [form] form)
  (def c
    (binding [clojure.tools.analyzer/macroexpand-1 noop-macroexpand-1]
      (clojure.tools.analyzer.jvm/analyze-ns 'positano.reflect)))

  ;;compare the two (excluding the unique parts). They are the same!
  (def dd (clojure.data/diff
           (walk-dissoc (nth b 2) [:loop-id :atom])
           (walk-dissoc (nth c 2) [:loop-id :atom])))

  (-> b
      (nth 1)
      (walk-select-keys
       [:op :init :methods :body :statements :val :children :bindings :test :then :else
        :args :name :ret :method :params :variadic? :fixed-arity]) clojure.pprint/pprint)

  (->> (second b)
       ast/nodes
       (filter #(and (#{:local :binding} (:op %))
                     (:local %)))
       (map (fn [x] {:name (:name x)
                     :form (:form x)
                     :line (-> x :env :line)}))
       pprint)

  (->> (second b)
       ast/nodes
       (filter #(and (#{;;:local
                        :binding} (:op %))
                     (= "var" (-> % :form str))
                     (:local %)))
       (map (fn [x] {:name (:name x)
                     :form (:form x)
                     :line (-> x :env :line)
                     :init (readable (:init x))
                     :op (:op x)
                     :bounds (bounds x)
                     }))
       pprint)

  ;;out:

  ({:name var__#0,
    :form var,
    :line 8,
    :init
    {:op :invoke,
     :children [:fn :args],
     :form (resolve fun),
     :args
     [{:op :local,
       :children [],
       :form fun,
       :name fun__#0,
       :variadic? false}]},
    :op :binding,
    :bounds {:line 8, :end-line 8, :column 9, :end-column 12}})




  (def b
    (clojure.tools.analyzer.jvm/analyze-ns 'positano.reflect))
  ;;or
  (def b (refactor-nrepl.analyzer/ns-ast (slurp "src/positano/reflect.clj")))

  (-> b
      readable
      (replace-nils ::nil)
      pprint)

  ;;;;;;;;;;;;;;;;;;;; START HERE ;;;;;;;;;;;;;;;;;;;;;;;;

  (def conn (core/init-db!))
  (analyze-dir! conn "src")

  ;;top level defs
  (def res
    (d/q
     '[:find ?ns ?name
       :in $ %
       :where
       (def ?def ?name)
       (ns ?def ?ns)]
     @conn
     query-rules))

  ;;top level functions
  (def res
    (d/q '[:find ?ns ?name
           :in $ %
           :where
           (top-level-fn ?def ?name)
           (ns ?def ?ns)]
         @conn
         query-rules))

  ;;top level private functions?
  (def res
    (d/q '[:find ?ns ?name
           :in $ %
           :where
           (top-level-fn ?def ?name)
           (ns ?def ?ns)
           (private ?def)]
         @conn
         query-rules))

  ;;top-level fns with files and line numbers
  (pprint
   (d/q '[:find ?ns ?name (pull ?env [:file :line])
          :in $ %
          :where
          (top-level-fn ?def ?name)
          (ns ?def ?ns)
          (?def :env ?env)]
        @conn query-rules))

;;; datomic

  (def ast
    (clojure.tools.analyzer.jvm/analyze-ns 'positano.reflect))

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
  )
