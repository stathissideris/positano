(require '[positano.core :as pos]
         '[positano.analyze :as ana]
         '[positano.trace :as trace]
         '[positano.query :as pq]
         '[datascript.core :as d]
         '[clojure.set :as set])

;; initialise positano
(def pos (pos/init-db!))

;; analyze all code under src/
(ana/analyze-dir! pos "src")

;; select all top level functions
(def top
  (d/q '[:find ?ns ?name
         :in $ %
         :where
         (top-level-fn ?def ?name)
         (ns ?def ?ns)]
       @pos
       ana/query-rules))

(count top)
(first top)
(-> (map first top) set sort)

;; instrument all top level functions

(doseq [[ns fun] top]
  (println "tracing" (str ns "/" fun))
  (trace/trace-var* ns fun))

;; run unit tests

(dev/run-all-my-tests) ;;hangs on monitor.analyst.clustering-test

;; collect all functions that were called

(def called
  (set
   (map (juxt (comp symbol :event/ns)
              (comp symbol :event/fn-name))
        (pq/all-function-events @pos))))

(count called)

;; remove all functions that were called from all top level functions

(def not-called
  (set/difference (set top) called))
