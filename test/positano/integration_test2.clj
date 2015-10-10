(ns positano.integration-test2
  (:require [clojure.test :refer :all]
            [positano.trace :as trace]
            [positano.db :as db]
            [positano.query :as q]
            [positano.utils :refer [block-until]]
            [positano.core :refer :all]
            [datascript.core :as d]
            [positano.integration-test2.fun]))

;;test simple tracing with mixture of deftrace and trace-var*

(defn setup []
  (trace/untrace-all)
  (require '[positano.integration-test2.fun] :reload)
  (trace/trace-var* 'positano.integration-test2.fun/foo))

(defn tear-down [conn]
  (stop-db! conn)
  (trace/untrace-all))

(deftest simple-tracing2
  (let [conn (init-db!)]

    (setup)

    (let [traced (set (map str (filter trace/traced? (trace/all-fn-vars))))]
      (is (= 3 (count traced)))
      (is (= #{"#'positano.integration-test2.fun/baz"
               "#'positano.integration-test2.fun/bar"
               "#'positano.integration-test2.fun/foo"} traced)))
    
    (positano.integration-test2.fun/foo [5 10 20 40])

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
    (tear-down conn)))
