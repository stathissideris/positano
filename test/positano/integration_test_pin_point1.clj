(ns positano.integration-test-pin-point1
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [positano.trace :as trace]
            [positano.db :as db]
            [positano.query :as q]
            [positano.utils :refer [block-until]]
            [positano.core :refer :all]
            [datascript.core :as d]))

;;explore values being passed between functions using datomic queries

(defn fun0 [m]
  (assoc m :k0 "k0"))
(defn fun1 [m]
  (assoc (fun0 m) :k1 "k1"))
(defn fun2 [m]
  (assoc (fun1 m) :k2 "k2"))
(defn fun3 [m]
  (let [m2 (assoc (fun2 m) :k3 "k3")]
    (dissoc m2 :k1)))
(defn fun4 [m]
  (assoc (fun3 m) :k4 "k4"))
(defn fun5 [m]
  (assoc (fun4 m) :k5 "k5"))
(defn fun6 [m]
  (assoc (fun5 m)
         :k6 "k6"
         :k4 "NEW VALUE"))
(defn fun7 [m]
  (assoc (fun6 m) :k7 "k7"))

(def funs [fun7 fun6 fun5 fun4 fun3 fun2 fun1 fun0])

(defn setup []
  (trace/untrace-all)
  
  (doseq [f funs]
    (trace/trace-var* f)))

(defn tear-down [conn]
  (stop-db! conn)
  (trace/untrace-all))

(deftest test-pin-point-query
  (let [conn (init-db!)]

    (setup)

    (let [traced (set (map str (filter trace/traced? (trace/all-fn-vars))))]
      (is (= 8 (count traced)))
      (is (= #{"#'positano.integration-test-pin-point1/fun0"
               "#'positano.integration-test-pin-point1/fun1"
               "#'positano.integration-test-pin-point1/fun2"
               "#'positano.integration-test-pin-point1/fun3"
               "#'positano.integration-test-pin-point1/fun4"
               "#'positano.integration-test-pin-point1/fun5"
               "#'positano.integration-test-pin-point1/fun6"
               "#'positano.integration-test-pin-point1/fun7"} traced)))

    (fun7 {:foo :bar})
    
    (is (not= :timed-out (block-until #(= 16 @db/event-counter) 10 3000)))

    (let [db (d/db conn)]
      (def ddd db)
      (let [events (q/all-function-events db)
            names ["fun7" "fun6" "fun5" "fun4" "fun3" "fun2" "fun1" "fun0"]]
        (is (= 16 (count events)))
        (is (= (into #{} (for [n names
                               t [:fn-call :fn-return]]
                           [n t]))
               (->> events (map (juxt :event/fn-name :event/type)) set)))

        ;;test function return values
        (is (= {"fun0" {:foo :bar :k0 "k0"}
                "fun1" {:foo :bar :k0 "k0" :k1 "k1"}
                "fun2" {:foo :bar :k0 "k0" :k1 "k1" :k2 "k2"}
                "fun3" {:foo :bar :k0 "k0" :k2 "k2" :k3 "k3"}
                "fun4" {:foo :bar :k0 "k0" :k2 "k2" :k3 "k3" :k4 "k4"}
                "fun5" {:foo :bar :k0 "k0" :k2 "k2" :k3 "k3" :k4 "k4" :k5 "k5"}
                "fun6" {:foo :bar :k0 "k0" :k2 "k2" :k3 "k3" :k4 "NEW VALUE" :k5 "k5" :k6 "k6"}
                "fun7" {:foo :bar :k0 "k0" :k2 "k2" :k3 "k3" :k4 "NEW VALUE" :k5 "k5" :k6 "k6" :k7 "k7"}}
               (->> events
                    (filter q/fn-return?)
                    (map db/deserialise)
                    (map (juxt :event/fn-name :event/return-value))
                    (into {}))))

        ;;find which function removed :k1 from the map
        (let [res (d/q '[:find ?name2
                         :in $ ?get
                         :where
                         [?call1   :event/fn-caller ?call2] ;;call2 calls call1
                         [?call2   :event/fn-name ?name2]
                         [?return1 :event/fn-entry ?call1] ;;return1 is the return event for call1
                         [?return2 :event/fn-entry ?call2]
                         [?return1 :event/return-value ?v1] ;;focus on return values
                         [?return2 :event/return-value ?v2]
                         
                         [(?get ?v1 :k1 ::none) ?key1]
                         [(?get ?v2 :k1 ::none) ?key2]
                         [(not= ?key1 ::none)] ;;call1 had a value for key :k1
                         [(= ?key2 ::none)]    ;;call2 has no value for key :k2
                         ] db get)]
          (is (= "fun3" (ffirst res))))

        ;;discover which function changed the value of :k4
        (let [res (d/q '[:find ?name2
                         :in $ ?get
                         :where
                         [?call1   :event/fn-caller ?call2] ;;call2 calls call1
                         [?call2   :event/fn-name ?name2]
                         [?return1 :event/fn-entry ?call1] ;;return1 is the return event for call1
                         [?return2 :event/fn-entry ?call2]
                         [?return1 :event/return-value ?v1] ;;focus on return values
                         [?return2 :event/return-value ?v2]
                         
                         [(?get ?v1 :k4 ::none) ?key1]
                         [(?get ?v2 :k4 ::none) ?key2]
                         [(not= ?key1 ::none)] ;;both maps should have values for :k4
                         [(not= ?key2 ::none)]
                         [(not= ?key1 ?key2)] ;;but the values should be different
                         ] db get)]
          (is (= "fun6" (ffirst res)))
;;          (def events (map d/touch events))
          )))
    (tear-down conn)))
