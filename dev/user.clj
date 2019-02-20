(ns user
  (:require [clojure.tools.namespace.repl :as ns-tools]))

(defn load-dev []
  (require 'dev)
  (in-ns 'dev))

(def dev load-dev)

(defn fix []
  (ns-tools/refresh-all))
