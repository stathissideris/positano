(ns positano.reflect
  (:require [clojure.walk :as walk]
            [clojure.repl :refer [source-fn]]))

(defn walk-select-keys [m ks]
  (walk/prewalk
   (fn [x] (if-not (map? x) x
                   (select-keys x ks))) m))

(defn arg-lists
  "fun should be a symbol that resolves to a function. Does not work
  if you load a file with cider-load-buffer (C-c C-k)"
  [fun]
  (let [var (resolve fun)]
    (cond
      (nil? var)
      (throw (ex-info (str "Cannot resolve " fun) {}))
      
      (not (fn? (var-get var)))
      (throw (ex-info (str "Value of " var " is not a function") {}))

      :else
      (or (some-> var meta :arglists)
          (let [source (some-> fun source-fn read-string)]
            (when (and (= 'def (first source))
                       (= 'fn (ffirst (drop 2 source))))
              (let [method-form (first (drop 2 source))]
                (if (vector? (second method-form))
                  (list (second method-form))
                  (map first (rest method-form))))))))))

(defn- flatten-with-maps
  "Like the core flatten, but it goes into maps."
  [x]
  (filter (complement coll?)
          (rest (tree-seq coll? seq x))))

(defn arg-names
  "Extract the names of the arguments from a passed arg-list in
  \"flatten\" order."
  [arg-list]
  (filter #(and (symbol? %)
                (not= % '&)) (flatten-with-maps arg-list)))

(defn param-extractor-fn-form
  "Creates the form of a function that has the passed arg-lists (and
  potentially multiple arities). For each arity, the function returns
  a vector containing pairs of argument names (as symbols) to the
  actual bound value of each argument."
  [arg-lists]
  `(fn ~'param-extractor-fn
     ~@(for [arg-list arg-lists]
         (let [args (arg-names arg-list)]
           `(~arg-list ~(vec (map vector
                                  (map (fn [a] `(quote ~a)) args)
                                  args))))))) 

(def make-param-extractor-fn
  (memoize
   (fn [arg-lists]
     (eval (param-extractor-fn-form arg-lists)))))

(defn bind-arg-lists [arg-lists params]
  (apply (make-param-extractor-fn arg-lists) params))

(defn bind-params
  "Answers the question: if I gave you a function and some parameters,
  how would those parameters be bound to the arguments? fun should be
  a symbol that resolves to a function. Returns a vector of pairs,
  each containing the name of the argument (as a symbol) and the bound
  value. The order of this vector is derived by flattening the
  arg-lists."
  [fun params]
  (bind-arg-lists (arg-lists fun) params))
