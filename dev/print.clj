(ns print
  (:require [clojure.set :as set]
            [fipp.visit :refer [visit visit*]]
            [fipp.ednize :refer [edn]]
            [fipp.engine :refer [pprint-document]]))

(defn- key-order [m map-first map-last sort-map-keys?]
  (let [k          (set (keys m))
        map-middle (set/difference k (set map-first) (set map-last))]
    (remove
     nil?
     (concat (map k map-first)
             (if sort-map-keys?
               (sort map-middle)
               map-middle)
             (map k map-last)))))

(defrecord EdnPrinter [print-meta map-first map-last sort-map-keys?]

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
    (let [kvps (for [key (key-order x map-first map-last sort-map-keys?)]
                 [:span (visit this key) " " (visit this (get x key))])]
      [:group "{" [:align (interpose [:span "," :line] kvps)]  "}"]))

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

(defn pprint-analyze
  ([x] (pprint-analyze x {}))
  ([x options]
   (let [printer (map->EdnPrinter (merge {:print-meta *print-meta*} options))]
     (binding [*print-meta* false]
       (pprint-document (visit printer x) options)))))

(comment
  (pprint-analyze
   {:tag :foo :a 1 :b 2 :env {} :c 3 :attrs [1 2 3 4]}
   {:map-first      [:attrs]
    :map-last       [:tag :env]
    :sort-map-keys? true}))
