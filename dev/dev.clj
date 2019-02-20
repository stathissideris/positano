(ns dev
  (:require [clojure.tools.namespace.repl :refer [clear refresh-all]]))

(set! *print-length* 20)
(set! *print-level* 4)

(defn refresh []
  (clojure.tools.namespace.repl/refresh))
