(ns positano.analyze
  (:require [clojure.walk :as walk]
            [clojure.tools.analyzer.jvm :as ana]
            [clojure.tools.analyzer.passes.jvm.emit-form :as e]
            [clojure.repl :refer [source-fn]]))

(defn walk-select-keys [m ks]
  (walk/prewalk
   (fn [x] (if-not (map? x) x
                   (select-keys x ks))) m))

(comment
  (def kkk 999)

  (defn aa
    ([x y [a b]] 0)
    ([x y z [a b]] 0))

  (def bb (fn [x y [a b]] 0))

  (def cc
    (fn
      ([x y [a b]] 0)
      ([x y z [a b]] 0))))

(defn arg-lists [fun]
  (or (some-> fun resolve meta :arglists)
      (let [source (some-> fun source-fn read-string)]
        (when (and (= 'def (first source))
                   (= 'fn (ffirst (drop 2 source))))
          (let [method-form (first (drop 2 source))]
            (if (vector? (second method-form))
              (list (second method-form))
              (map first (rest method-form))))))))

(defn- flatten-with-maps
  [x]
  (filter (complement coll?)
          (rest (tree-seq coll? seq x))))

(defn arg-names [arg-list]
  (filter #(and (symbol? %)
                (not= % '&)) (flatten-with-maps arg-list)))

(defn param-extractor-fn-form [arg-lists]
  `(fn ~'param-extractor-fn
     ~@(for [arg-list arg-lists]
         (let [args (arg-names arg-list)]
           `(~arg-list ~(zipmap
                         (map (fn [a] `(quote ~a)) args)
                         args)))))) 

(def make-param-extractor-fn
  (memoize
   (fn [arg-lists]
     (eval (param-extractor-fn-form arg-lists)))))

(defn apply-arg-lists [arg-lists params]
  (apply (make-param-extractor-fn arg-lists) params))

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
      (e/emit-form)))
