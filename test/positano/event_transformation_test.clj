(ns positano.event-transformation-test
  (:require [clojure.test :refer :all]
            [positano.trace :as trace]
            [positano.db :as db]
            [positano.query :as q]
            [positano.utils :refer [block-until]]
            [positano.core :refer :all]
            [datascript.core :as d]
            [positano.event-transformation-test.fun]))

;;test simple tracing with mixture of deftrace and trace-var*

(defn setup []
  (trace/untrace-all)
  (require '[positano.event-transformation-test.fun] :reload))

(defn tear-down [conn]
  (stop-db! conn)
  (trace/untrace-all))

(deftest test-simple-transform
  (let [conn (init-db!
              {:event-transducer
               (map
                (fn [e]
                  (update-in e [:event/fn-name] #(str % "-FOO"))))})]

    (setup)

    (positano.event-transformation-test.fun/foo [5 10 20 40])

    (is (not= :timed-out (block-until #(= 6 @db/event-counter) 10 3000)))

    (let [db (d/db conn)
          events (q/all-function-events db)]
      (is (= 6 (count events)))
      (is (= #{["baz-FOO" :fn-call]
               ["baz-FOO" :fn-return]
               ["bar-FOO" :fn-call]
               ["bar-FOO" :fn-return]
               ["foo-FOO" :fn-call]
               ["foo-FOO" :fn-return]}
             (->> events (map (juxt :event/fn-name :event/type)) set))))
    (tear-down conn)))

(comment
 (deftest test-transform-throwing-exception
   (let [uri  (init-db!
               {:event-transducer
                (map
                 (fn [e]
                   (if (= 'bar (:fn-name e))
                     (throw (ex-info "I don't like bars (expected exception)" e))
                     (update-in e [:fn-name] #(str % "-FOO")))))})
         conn (d/connect uri)]

     (setup)

     (foo [5 10 20 40])

     (is (not= :timed-out (block-until #(= 6 @db/event-counter) 10 3000)))

     (let [db (d/db conn)
           events (q/all-function-events db)]
       (is (= 6 (count events)))
       (is (= #{["baz-FOO" :fn-call :yes]
                ["baz-FOO" :fn-return :yes]
                ["bar" :fn-call :failed]
                ["bar" :fn-return :failed]
                ["foo-FOO" :fn-call :yes]
                ["foo-FOO" :fn-return :yes]}
              (->> events (map (juxt :event/fn-name :event/type)) set))))
     (tear-down conn))))
