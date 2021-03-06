(ns positano.query
  (:require [clojure.string :as str]
            [datascript.core :as d]
            [positano.db :as db]
            [positano.time :as time]
            [positano.reflect :as reflect]))

(defn fn-call? [e] (= :fn-call (:event/type e)))
(defn fn-return? [e] (= :fn-return (:event/type e)))

(defn fn-entry
  "If event is a fn-return, this returns the corresponding fn-call
  event, otherwise e is returned"
  [e]
  (if (fn-return? e) (:event/fn-entry e) e))

(defn fn-return
  [e]
  (if (fn-call? e) (:event/fn-return e) e))

(defn root? [e]
  (nil? (:event/fn-caller (fn-entry e))))

(defn caller [e]
  (:event/fn-caller (fn-entry e)))

(defn root-caller
  "Returns the root caller of the fn-call event passed. Also works on
  fn-return events."
  [e]
  (if (root? e)
    e
    (recur (caller e))))

(defn stack [e]
  (cons {:entry (fn-entry e)
         :return (fn-return e)}
        (when-not (root? e) (lazy-seq (stack (caller e))))))

(defn- args-str [e]
  (str/join " " (map pr-str (:event/fn-args e))))

(defn- arg-bindings [e]
  (let [fun (symbol (str (:event/ns e) "/" (:event/fn-name e)))
        b (reflect/bind-params fun (:event/fn-args e))]
    (str/join
     " "
     (for [[name value] b]
       (str name "=" (pr-str value))))))

(defn event-duration [e]
  (time/difference
   (:event/timestamp (fn-entry e))
   (:event/timestamp (fn-return e))))

(defn all-function-events [db]
  (for [r (d/q '[:find ?e :where [?e :event/type _]] db)]
    (d/entity db (first r))))

(defn- indent [x]
  (str/join (repeat x "⃓ ")))

(defn print-stack [s]
  (doseq [{:keys [entry i]} (map #(assoc %1 :i %2) (reverse s) (range))]
    (let [entry (db/deserialise entry)]
      (println (str (indent i) "(" (:event/fn-name entry) " " (args-str entry) ") " (arg-bindings entry)))))
  (doseq [{:keys [return i]} (map #(assoc %1 :i %2) s (range (count s) 0 -1))]
    (if-not return
      (println (str (indent (dec i)) "└ <pending>"))
      (let [entry (db/deserialise return)]
        (println
         (str (indent (dec i))
              "└ "
              (:event/return-value entry)
              " ⌚:"
              (time/duration-string (event-duration return))))))))
