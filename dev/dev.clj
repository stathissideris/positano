(ns dev
  (:require [clojure.tools.namespace.repl :refer [clear refresh-all]]))

(defn refresh []
  (clojure.tools.namespace.repl/refresh))
