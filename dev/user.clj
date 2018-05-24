(ns user)

(set! *print-length* 20)
(set! *print-level* 4)

(defn load-dev []
  (require 'dev)
  (in-ns 'dev))

(def dev load-dev)
