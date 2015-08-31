(ns positano.integration-test1
  (:require [clojure.test :refer :all]
            [positano.trace :as trace]
            [positano.db :as db]
            [positano.query :as q]
            [positano.utils :refer [block-until]]
            [datomic.api :as d]))

(defn setup []
  (trace/deftrace baz [x]
    (inc x))

  (trace/deftrace bar [x]
    (* (baz (/ x 2.0)) 3))

  (trace/deftrace foo
    "I don't do a whole lot."
    [x]
    (println "Hello World!")
    (bar (first x))))

(defn tear-down [uri]
  (trace/stop-db! uri)
  (trace/untrace-all))

(deftest simple-tracing
  (let [uri  (trace/init-db!)
        conn (d/connect uri)]

    (setup)

    (let [traced (set (map str (filter trace/traced? (trace/all-fn-vars))))]
      (is (= 3 (count traced)))
      (is (= #{"#'positano.integration-test1/baz"
               "#'positano.integration-test1/bar"
               "#'positano.integration-test1/foo"} traced)))
    
    (foo [5 10 20 40])
    
    (is (not= :timed-out (block-until #(= 6 @db/event-counter) 10 3000)))

    (let [db (d/db conn)]
      (let [events (q/all-function-events db)]
        (is (= 6 (count events)))
        (is (= #{["baz" :fn-call]
                 ["baz" :fn-return]
                 ["bar" :fn-call]
                 ["bar" :fn-return]
                 ["foo" :fn-call]
                 ["foo" :fn-return]}
               (->> events (map (juxt :event/fn-name :event/type)) set)))

        ;;test function arguments
        (is (= {"foo" [[5 10 20 40]]
                "bar" [5]
                "baz" [2.5]}
               (->> events
                    (filter q/fn-call?)
                    (map db/deserialise)
                    (map (juxt :event/fn-name :event/fn-args))
                    (into {}))))

        ;;test function return values
        (is (= {"baz" 3.5
                "bar" 10.5
                "foo" 10.5}
               (->> events
                    (filter q/fn-return?)
                    (map db/deserialise)
                    (map (juxt :event/fn-name :event/return-value))
                    (into {}))))

        (def events (map d/touch events))))
    (tear-down uri)))
