(ns positano.integration-test3
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [positano.trace :as trace]
            [positano.db :as db]
            [positano.query :as q]
            [positano.utils :refer [block-until]]
            [positano.core :refer :all]
            [datascript.core :as d]
            [positano.integration-test3.fun]))

;;test simple tracing which includes a core library function

(defn setup []
  (trace/untrace-all)
  (require '[positano.integration-test3.fun] :reload)
  (trace/trace-var* 'clojure.string 'join))

(defn tear-down [conn]
  (stop-db! conn)
  (trace/untrace-all))

(deftest simple-tracing3
  (let [conn (init-db!)]

    (setup)

    (let [traced (set (map str (filter trace/traced? (trace/all-fn-vars))))]
      (is (= 4 (count traced)))
      (is (= #{"#'positano.integration-test3.fun/baz"
               "#'positano.integration-test3.fun/bar"
               "#'positano.integration-test3.fun/foo"
               "#'clojure.string/join"} traced)))
    
    (positano.integration-test3.fun/foo [5 10 20 40])
    
    (is (not= :timed-out (block-until #(= 8 @db/event-counter) 10 3000)))

    (let [db (d/db conn)]
      (let [events (q/all-function-events db)]
        (is (= 8 (count events)))
        (is (= #{["baz" :fn-call]
                 ["baz" :fn-return]
                 ["bar" :fn-call]
                 ["bar" :fn-return]
                 ["foo" :fn-call]
                 ["foo" :fn-return]
                 ["join" :fn-call]
                 ["join" :fn-return]}
               (->> events (map (juxt :event/fn-name :event/type)) set)))

        ;;test function arguments
        (is (= {"foo" [[5 10 20 40]]
                "bar" [5]
                "baz" [2.5]
                "join" [", " ["Hello" "World!"]]}
               (->> events
                    (filter q/fn-call?)
                    (map db/deserialise)
                    (map (juxt :event/fn-name :event/fn-args))
                    (into {}))))

        ;;test function return values
        (is (= {"baz" 3.5
                "bar" 10.5
                "foo" 10.5
                "join" "Hello, World!"}
               (->> events
                    (filter q/fn-return?)
                    (map db/deserialise)
                    (map (juxt :event/fn-name :event/return-value))
                    (into {}))))))
    (tear-down conn)))
