(ns positano.analyze
  (:require [clojure.walk :as walk]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.tools.analyzer.jvm :as jvm]

            [cljs.analyzer.api :as cljs]
            [clojure.tools.analyzer.passes.jvm.emit-form :as e]
            [clojure.tools.analyzer.ast :as ast]
            [clojure.tools.analyzer.ast.query :as ast.query]
            [refactor-nrepl.analyzer]
            [clojure.pprint :refer [pprint]]
            [refactor-nrepl.analyzer]
            ;;[datascript.core :as d]
            [datomic.api :as d])
  (:import [java.io File]))

(defn var-name [v] (subs (str v) 2))

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
     [?fn :var ?raw-var]
     [(positano.analyze/var-name ?raw-var) ?var]]

    [(invoke-local ?invoke ?fn)
     [?invoke :op :invoke]
     [?invoke :fn ?fn]
     [?fn :op :local]]

    [(static-call ?static-call ?method)
     [?static-call :op :static-call]
     [?static-call :method ?method]]

    [(env ?form ?env)
     [?form :env ?env]]

    [(parent ?parent ?child) [?parent :init ?child]]
    [(parent ?parent ?child) [?parent :expr ?child]]
    [(parent ?parent ?child) [?parent :methods ?child]]
    [(parent ?parent ?child) [?parent :body ?child]]
    [(parent ?parent ?child) [?parent :args ?child]]
    [(parent ?parent ?child) [?parent :bindings ?child]]

    [(ancestor ?a ?b)
     [parent ?a ?b]]
    [(ancestor ?a ?b)
     [parent ?a ?x]
     [ancestor ?x ?b]]

    [(caller ?a ?b)
     [ancestor ?a ?x]
     (or (invoke-var ?x ?b)
         (invoke-local ?x ?b)
         (static-call ?x ?b))]])

(defn walk-select-keys [m ks]
  (walk/prewalk
   (fn [x]
     (cond (not (map? x)) x
           :else (select-keys x ks)))
   m))

(defn readable [node]
  (walk-select-keys

   node
   [:op :init :methods :body :statements :val :children :bindings :test :then :else :keyword :target :form
    :args :name :ret :method :params :variadic? :fixed-arity :expr
    :meta :fn :private :var
    :env :ns :file :line :end-line :column :end-column
    ]))

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

(defn db
  "Given a list of ASTs, returns a representation of those
   that can be used as a database in a Datomic Datalog query"
  [asts]
  (mapcat ast->eav asts))

(defn analyze-file [filename]
  (cond (str/ends-with? filename ".clj")
        (do
          (println "Analyzing:" filename)
          (refactor-nrepl.analyzer/ns-ast (slurp filename)))

        ;; (str/ends-with? filename ".cljs")
        ;; (binding [cljs.analyzer/*verbose* true]
        ;;   (cljs/analyze-file (File. filename) {:verbose true}))


        ;; Stathis Sideris [10:21 AM]
        ;; I’m using this in an attempt to get an AST for my cljs source:
        ;; `(cljs/cljs.analyzer.api (File. filename))`
        ;; But it fails with `No such namespace: cljsjs.react` which is a transitive dependency to my project via re-frame. Is it a problem with the project setup or am I using the analyzer incorrectly?
        ;;
        ;;
        ;; Thomas Heller [10:36 AM]
        ;; @stathissideris the analyzer must be initialized with the `:foreign-lib` data from `deps.cljs` files
        ;; I'm not quite sure what call that was. you'll probably find it in `cljs.closure`


        ;; (do
        ;;   (let [env (cljs/empty-state)]
        ;;     (cljs/analyze-file env (File. filename))
        ;;     env))
        ))

(defn analyze-ns [ns-sym]
  (jvm/analyze-ns ns-sym))

(defn analyze-dir [path]
  (->> (file-seq (io/file path))
       (mapcat #(analyze-file (.getPath %)))
       (remove nil?)
       (db)))

(defn ast-q [q db]
    (d/q q db query-rules))
