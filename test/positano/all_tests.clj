(ns positano.all-tests
  (:require  [clojure.test :as t]))

(defn run-tests []
  (t/run-tests
   'positano.integration-test1
   'positano.integration-test2
   'positano.integration-test3
   'positano.integration-test4
   'positano.event-transformation-test
   'positano.integration-test-pin-point1
   'positano.reflect-test))
