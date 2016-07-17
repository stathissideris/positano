(require '[positano.core :as pos]
         '[positano.analyze :as ana]
         '[positano.trace :as trace]
         '[positano.query :as pq]
         '[datascript.core :as d]
         '[clojure.set :as set]
         '[clojure.test :as test])

;; initialise positano
(def pos (pos/init-db!))

;; analyze all code under src/
(ana/analyze-dir! pos "src")
(ana/analyze-dir! pos "test")

;; select all top level functions
(def top
  (d/q '[:find ?ns ?name
         :in $ %
         :where
         (top-level-fn ?def ?name)
         (ns ?def ?ns)]
       @pos
       ana/query-rules))

;;exploring the AST with pull:
(def private-vars-exposed-for-tests
  (d/q '[:find ?ns ?name (pull ?init [{:fn [:op :var]} :op {:args [*]}])
         :in $ %
         :where
         (def ?def ?name)
         (ns ?def ?ns)
         (?def :init ?init)]
       @pos
       ana/query-rules))

;;actually getting the list of vars:
(def private-vars-exposed-for-tests
  (d/q (ana/resolve-vars
        '[:find ?ns ?name ?private-fn
          :in $ %
          :where
          (def ?def ?name)
          (ns ?def ?ns)
          (?def :init ?invoke)
          (invoke-var ?invoke (var clojure.core/deref))
          (?invoke :args ?arg)
          (?arg :var ?private-fn)])
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

(def vv2
  [['monitor.analyst.clustering 'group-incidents-for-monitor]
   ['monitor.analyst.clustering 'find-data-clusters]
   ['monitor.analyst.clustering 'assign-second-level]
   ['monitor.analyst.clustering 'get-cluster-leader]
   ['monitor.analyst.clustering 'row->kv]
   ['monitor.analyst.clustering 'dod-change]
   ['monitor.analyst.clustering 'get-winning-dim]
   ['monitor.analyst.clustering 'select-totals-for-third] ;;;
   ['monitor.analyst.clustering 'assign-totals]
   ['monitor.analyst.clustering 'get-matching-leader]
   ['monitor.analyst.clustering 'assign-third-level]
   ['monitor.analyst.clustering 'get-matching-row]
   ['monitor.analyst.clustering 'get-issue-dim-values]
   ['monitor.analyst.clustering 'assign-total-for-row]
   ['monitor.analyst.clustering 'issue]
   ['monitor.analyst.clustering 'total?]
   ['monitor.analyst.clustering 'assign-first-level]
   ['monitor.analyst.clustering 'dod-change-relative-to-row]
   ['monitor.analyst.clustering 'get-cluster-leader-with-totals]
   ['monitor.analyst.clustering 'totals-in-row]
   ['monitor.analyst.clustering 'gen-clust-num]
   ['monitor.analyst.clustering 'get-cluster-issue]
   ['monitor.analyst.clustering 'all-totals]
   ['monitor.analyst.clustering 'get-matching-cluster]
   ['monitor.analyst.clustering 'same-direction]
   ['monitor.analyst.clustering 'first-not-total-dimension]
   ['monitor.analyst.clustering 'select-keys-ordered]])

(doseq [[ns s] vv]
  (println "Tracing:" ns s)
  (trace/trace-var* ns s)
  (test/run-tests 'monitor.analyst.clustering-test)
  ;;(println "Event counter:" (count (pq/all-function-events @pos)))
  (println "Event counter:" @positano.db/event-counter)
  (println "Event sequence:" @positano.db/event-sequence))

(doseq [[ns s] vv]
  (println "Tracing:" ns s)
  (trace/trace-var* ns s))
