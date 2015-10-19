(ns dev
  (:require [clojure.tools.namespace.repl :refer [clear refresh-all]]))

(defn refresh []
  (clojure.tools.namespace.repl/refresh))

(defn load-print []
  (require '[print :refer [pprint-analyze]]))
