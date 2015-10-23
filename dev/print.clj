(ns print
  (:require [clojure.set :as set]
            [fipp.visit :refer [visit visit*]]
            [fipp.ednize :refer [edn]]
            [fipp.engine :refer [pprint-document]]))

(defn weight [x]
  (cond (map? x) (+ 5 (count x))
        (sequential? x) (+ 4 (count x))
        (string? x) 3
        (keyword x) 3
        (var? x) 3
        (instance? Boolean x) 3
        (number? x) 1
        :else 2))

(defn- key-order
  "If not nil, sort-map-fn is passed key-value pairs and is expected
  to return the pairs in the order they should be used for the
  \"middle\" part of the map, (middle keys are the ones not mentioned
  in map-first and map-last)"
  [m map-first map-last sort-map-fn]
  (let [k          (set (keys m))
        map-middle (set/difference k (set map-first) (set map-last))]
    (remove
     nil?
     (concat (map k map-first)
             (if sort-map-fn
               (map first (sort-map-fn (zipmap map-middle (map m map-middle))))
               map-middle)
             (map k map-last)))))

(defrecord EdnPrinter [print-meta map-first map-last sort-map-fn map-dissoc]

  fipp.visit/IVisitor

  (visit-unknown [this x]
    (visit this (edn x)))

  (visit-nil [this]
    [:text "nil"])

  (visit-boolean [this x]
    [:text (str x)])

  (visit-string [this x]
    [:text (pr-str x)])

  (visit-character [this x]
    [:text (pr-str x)])

  (visit-symbol [this x]
    [:text (str x)])

  (visit-keyword [this x]
    [:text (str x)])

  (visit-number [this x]
    [:text (pr-str x)])

  (visit-seq [this x]
    [:group "(" [:align (interpose :line (map #(visit this %) x))] ")"])

  (visit-vector [this x]
    [:group "[" [:align (interpose :line (map #(visit this %) x))] "]"])

  (visit-map [this x]
    (let [kvps (for [key (key-order (apply dissoc x map-dissoc) map-first map-last sort-map-fn)]
                 [:span (visit this key) " " (visit this (get x key))])]
      [:group "{" [:align (interpose :line kvps)]  "}"]))

  (visit-set [this x]
    [:group "#{" [:align (interpose :line (map #(visit this %) x)) ] "}"])

  (visit-tagged [this {:keys [tag form]}]
    [:group "#" (pr-str tag)
            (when (or (and print-meta (meta form))
                      (not (coll? form)))
              " ")
            (visit this form)])

  (visit-meta [this m x]
    (if print-meta
      [:align [:span "^" (visit this m)] :line (visit* this x)]
      (visit* this x)))

  (visit-var [this x]
    [:text (str x)])

  (visit-pattern [this x]
    [:text (pr-str x)]))

(defn smart-pprint
  ([x] (smart-pprint x {}))
  ([x options]
   (let [printer (map->EdnPrinter (merge {:print-meta *print-meta*} options))]
     (binding [*print-meta* false]
       (pprint-document (visit printer x) options)))))

(comment
  (smart-pprint
   {:tag :foo :a 3 :b 2 :env {} :c 1 :attrs [1 2 3 4]}
   {:map-first   [:attrs]
    :map-last    [:tag :env]
    :sort-map-fn (partial sort-by val)}))
