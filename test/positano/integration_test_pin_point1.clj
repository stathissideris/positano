(ns positano.integration-test-pin-point1
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [positano.trace :as trace]
            [positano.db :as db]
            [positano.query :as q]
            [positano.utils :refer [block-until]]
            [positano.core :refer :all]
            [datomic.api :as d]))

;;explore values being passed between functions using datomic queries

(defn step0 [m]
  (assoc m :k0 "k0"))
(defn step1 [m]
  (assoc (step0 m) :k1 "k1"))
(defn step2 [m]
  (assoc (step1 m) :k2 "k2"))
(defn step3 [m]
  (let [m2 (assoc (step2 m) :k3 "k3")]
    (dissoc m2 :k1)))
(defn step4 [m]
  (assoc (step3 m) :k4 "k4"))
(defn step5 [m]
  (assoc (step4 m) :k5 "k5"))
(defn step6 [m]
  (assoc (step5 m)
         :k6 "k6"
         :k4 "NEW VALUE"))
(defn step7 [m]
  (assoc (step6 m) :k7 "k7"))

(def funs [step7 step6 step5 step4 step3 step2 step1 step0])

(defn setup []
  (trace/untrace-all)
  
  (doseq [f funs]
    (trace/trace-var* f)))

(defn tear-down [uri]
  (stop-db! uri)
  (trace/untrace-all))

(deftest simple-tracing
  (let [uri  (init-db!)
        conn (d/connect uri)]

    (setup)

    (let [traced (set (map str (filter trace/traced? (trace/all-fn-vars))))]
      (is (= 8 (count traced)))
      (is (= #{"#'positano.integration-test-pin-point1/step0"
               "#'positano.integration-test-pin-point1/step1"
               "#'positano.integration-test-pin-point1/step2"
               "#'positano.integration-test-pin-point1/step3"
               "#'positano.integration-test-pin-point1/step4"
               "#'positano.integration-test-pin-point1/step5"
               "#'positano.integration-test-pin-point1/step6"
               "#'positano.integration-test-pin-point1/step7"} traced)))

    (step7 {:foo :bar})
    
    (is (not= :timed-out (block-until #(= 16 @db/event-counter) 10 3000)))

    (let [db (d/db conn)]
      (let [events (q/all-function-events db)
            names ["step7" "step6" "step5" "step4" "step3" "step2" "step1" "step0"]]
        (is (= 16 (count events)))
        (is (= (into #{} (for [n names
                               t [:fn-call :fn-return]]
                           [n t]))
               (->> events (map (juxt :event/fn-name :event/type)) set)))

        ;;test function return values
        (is (= {"step0" {:foo :bar :k0 "k0"}
                "step1" {:foo :bar :k0 "k0" :k1 "k1"}
                "step2" {:foo :bar :k0 "k0" :k1 "k1" :k2 "k2"}
                "step3" {:foo :bar :k0 "k0" :k2 "k2" :k3 "k3"}
                "step4" {:foo :bar :k0 "k0" :k2 "k2" :k3 "k3" :k4 "k4"}
                "step5" {:foo :bar :k0 "k0" :k2 "k2" :k3 "k3" :k4 "k4" :k5 "k5"}
                "step6" {:foo :bar :k0 "k0" :k2 "k2" :k3 "k3" :k4 "NEW VALUE" :k5 "k5" :k6 "k6"}
                "step7" {:foo :bar :k0 "k0" :k2 "k2" :k3 "k3" :k4 "NEW VALUE" :k5 "k5" :k6 "k6" :k7 "k7"}}
               (->> events
                    (filter q/fn-return?)
                    (map db/deserialise)
                    (map (juxt :event/fn-name :event/return-value))
                    (into {}))))

        ;;find which function removed :k1 from the map
        (let [res (d/q '[:find ?name2
                         :where
                         [?call1   :event/fn-caller ?call2] ;;call2 calls call1
                         [?call2   :event/fn-name ?name2]
                         [?return1 :event/fn-entry ?call1] ;;return1 is the return event for call1
                         [?return2 :event/fn-entry ?call2]
                         [?return1 :event/return-value ?val1] ;;focus on return values
                         [?return2 :event/return-value ?val2]
                         
                         [(read-string ?val1) ?v1] ;;deserialise
                         [(read-string ?val2) ?v2]
                         [(get ?v1 :k1 ::none) ?key1]
                         [(get ?v2 :k1 ::none) ?key2]
                         [(not= ?key1 ::none)] ;;call1 had a value for key :k1
                         [(= ?key2 ::none)]    ;;call2 has no value for key :k2
                         ] db)]
          (is (= "step3" (ffirst res))))

        ;;discover which function changed the value of :k4
        (let [res (d/q '[:find ?name2
                         :where
                         [?call1   :event/fn-caller ?call2] ;;call2 calls call1
                         [?call2   :event/fn-name ?name2]
                         [?return1 :event/fn-entry ?call1] ;;return1 is the return event for call1
                         [?return2 :event/fn-entry ?call2]
                         [?return1 :event/return-value ?val1] ;;focus on return values
                         [?return2 :event/return-value ?val2]
                         
                         [(read-string ?val1) ?v1] ;;deserialise
                         [(read-string ?val2) ?v2]
                         [(get ?v1 :k4 ::none) ?key1]
                         [(get ?v2 :k4 ::none) ?key2]
                         [(not= ?key1 ::none)] ;;both maps should have values for :k4
                         [(not= ?key2 ::none)]
                         [(not= ?key1 ?key2)] ;;but the values should be different
                         ] db)]
          (is (= "step6" (ffirst res)))
;;          (def events (map d/touch events))
          )))
    (tear-down uri)))
