(ns positano.analyze
  (:require [clojure.tools.analyzer.jvm :as ana]
            [clojure.tools.analyzer.passes.jvm.emit-form :as e]
            [clojure.tools.analyzer.ast :as ast]
            [clojure.set :as set]
            [clojure.zip :as zip]
            [print :refer [smart-pprint weight]]))

(defn bounds [node]
  (select-keys (:env node) [:line :end-line :column :end-column]))

(defn pp [x]
  (smart-pprint
   x
   {:map-first   [:op :name :tag :o-tag :arglists :form :raw-forms :children]
    :sort-map-fn (partial sort-by (comp weight val))
    :map-last    [:init :statements :ret]
    :map-dissoc  [:env :loop-id :meta]}))

(def key-renames
  {:methods    :method
   :params     :param
   :args       :arg
   :statements :statement
   :bindings   :binding})

(def key-dissoc
  [:children])

(def single-ref
  {:db/valueType :db.type/ref})

(def many-ref
  {:db/valueType   :db.type/ref
   :db/cardinality :db.cardinality/many})

(defn generic-zipper
  "Walks vectors, lists, maps, and maps' keys and values
  individually. Take care not to replace a keypair with a single
  value (will throw an exception)."
  [x]
  (zip/zipper
   (some-fn sequential? map?)
   seq
   (fn [node children]
     (cond (vector? node) (vec children)
           (seq? node) (seq children)
           (map? node) (into {} children)))
   x))

(defn- dump-zipper [z]
  (loop [z z]
    (prn (zip/node z))
    (if-not (zip/end? z) (recur (zip/next z)))))

(defn to-transaction [x]
  ())

(def schema
  {:method    many-ref
   :param     many-ref
   :arg       many-ref
   :statement many-ref
   :binding   many-ref
   :ret       single-ref
   :body      single-ref
   :init      single-ref})

(comment

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

  
  
  (def a (refactor-nrepl.analyzer/ns-ast (slurp "s:/devel/positano/src/positano/reflect.clj"))) ;;this is how clj-refactor does it

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

  ;;if you replace (resolve fun) with (:h fun)
  
  ({:name var__#0,
    :form var,
    :line 8,
    :init
    {:op :keyword-invoke,
     :children [:keyword :target],
     :keyword {:op :const, :val :h, :form :h},
     :target
     {:op :local,
      :children [],
      :form fun,
      :name fun__#0,
      :variadic? false},
     :form (:h fun)},
    :op :binding,
    :bounds {:line 8, :end-line 8, :column 9, :end-column 12}})

  )
