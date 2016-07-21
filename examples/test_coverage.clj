(require '[positano.core :as pos]
         '[positano.analyze :as ana]
         '[positano.trace :as trace]
         '[positano.query :as pq]
         '[datascript.core :as d]
         '[clojure.set :as set]
         '[clojure.test :as test])

;; initialise positano
(def pos (pos/init-db!))

;; analyze all code under src/ and test/
(do
  (ana/analyze-dir! pos "src")
  (ana/analyze-dir! pos "test"))

(def ends-with #(.endsWith (str %1) %2))
(def does-not-end-with (complement ends-with))
(defn sym= [a b] (= (str a) (str b)))

;; select all non-test top level functions for now, this also selects
;; macros! (see what defmacro expands to)
(def top-fns
  (set
   (d/q '[:find ?ns ?name
          :in $ % ?does-not-end-with
          :where
          (top-level-fn ?def ?name)
          (ns ?def ?ns)
          [(?does-not-end-with ?ns "-test")]]
        @pos
        ana/query-rules
        does-not-end-with))) ;;datascript does not support `not` yet

;;actually getting the list of vars:
(def private-vars-exposed-for-tests
  (d/q (ana/resolve-vars
        '[:find ?ns ?name ?private-fn
          :in $ % ?ends-with
          :where
          (def ?def ?name)
          (ns ?def ?ns)
          [(?ends-with ?ns "-test")]
          (?def :init ?invoke)
          (invoke-var ?invoke (var clojure.core/deref))
          (?invoke :args ?arg)
          (?arg :var ?private-fn)])
       @pos
       ana/query-rules
       ends-with))

(defn- var->ns-name-pair [v]
  [(-> v .ns str symbol)
   (-> v .sym)])

(def fns-to-instrument
  (set/union
   (set/difference
    top-fns
    (map (comp var->ns-name-pair last) private-vars-exposed-for-tests))
   (map (juxt first second) private-vars-exposed-for-tests)))

;; sanity check
(assert (= (count top-fns)
           (count fns-to-instrument)))

;; instrument all top level functions

(doseq [[ns fun] fns-to-instrument]
  (println "tracing" (str ns "/" fun))
  (try
    (trace/trace-var* ns fun)
    (catch Exception e
      (if (= :macro (:type (ex-data e))) ;;TODO rule macros out in the original query
        (println (.getMessage e))
        (throw e)))))

;; run unit tests

(dev/run-all-my-tests)

;; collect all functions that were called

(def called
  (set
   (map (juxt (comp symbol :event/ns)
              (comp symbol :event/fn-name))
        (pq/all-function-events @pos))))

(count called)

;; remove all functions that were called from all top level functions

(def not-called
  (set/difference fns-to-instrument called))

;; report results

(println
 (format
  (str "Namespaces: %d\n"
       "Top level functions: %d\n"
       "Private functions exposed in tests: %d\n"
       "Called functions: %d\n"
       "Not called: %d\n"
       "Coverage: %.2f%%\n")

  (count (set (map first top-fns)))
  (count top-fns)
  (count private-vars-exposed-for-tests)
  (count called)
  (count not-called)
  (float
   (* 100
      (/ (count called)
         (count top-fns))))))





;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;Outakes

;;exploring the AST with pull:
(def private-vars-exposed-for-tests
  (d/q '[:find ?ns ?name (pull ?init [{:fn [:op :var]} :op {:args [*]}])
         :in $ % ?ends-with
         :where
         (def ?def ?name)
         (ns ?def ?ns)
         [(?ends-with ?ns "-test")]
         (?def :init ?init)]
       @pos
       ana/query-rules
       ends-with))
