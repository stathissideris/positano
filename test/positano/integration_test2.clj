(ns positano.integration-test2
  (:require [clojure.test :refer :all]
            [positano.trace :as trace]
            [positano.db :as db]
            [positano.query :as q]
            [positano.utils :refer [block-until]]
            [datomic.api :as d]))

(trace/deftrace baz [x]
  (inc x))

(trace/deftrace bar [x]
  (* (baz (/ x 2.0)) 3))

(defn foo
  "I don't do a whole lot."
  [x]
  (println "Hello World!")
  (bar (first x)))

(deftest simple-tracing
  (let [uri  (trace/init-db!)
        conn (d/connect uri)]
    (trace/trace-var* 'foo)
    (foo [5 10 20 40])

    (is (not= :timed-out (block-until #(= 6 @db/event-counter) 10 3000)))

    (let [db (d/db conn)
          events (q/all-function-events db)]
      (is (= 6 (count events)))
      (is (= #{["baz" :fn-call]
               ["baz" :fn-return]
               ["bar" :fn-call]
               ["bar" :fn-return]
               ["foo" :fn-call]
               ["foo" :fn-return]}
             (->> events (map (juxt :event/fn-name :event/type)) set))))
    (trace/stop-db! uri)))
