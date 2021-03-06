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
