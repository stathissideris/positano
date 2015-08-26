(ns positano.utils
  (:require [clojure.set :as set]
            [clojure.string :as string]
            [clojure.core.async :as async]))

(defn- length [^String s] (.length s))

(def ^:private clojure->java
  (conj
   (->> (assoc (Compiler/CHAR_MAP) "/" "$")
        set/map-invert)))

(defn demangle
  "Converts a Java class name back to a Clojure name"
  [s]
  (reduce
   (fn [s [before after]]
     (string/replace s before (str after)))
   s clojure->java))

(defn stack-element->clojure-function [s]
  (-> s .getClassName demangle))

(defn find-first
  [f coll]
  (first (filter f coll)))

(defn in-recursive-stack? []
  (let [stack (seq (.getStackTrace (Thread/currentThread)))
        caller-name (->> stack (drop 2) first .getClassName)
        rest-names (->> stack (drop 3) (map #(.getClassName %)))]
    (some? (find-first #(= caller-name %) rest-names))))

(defn now-millis []
  (.getTime (java.util.Date.)))

(defn block-until [pred interval timeout]
  (let [time-started (now-millis)]
   (loop []
     (cond
       (pred) :done
       (> (- (now-millis) time-started) timeout) :timed-out
       :else (do
               (Thread/sleep interval)
               (recur))))))

(defn foo [x]
  (println "in recursive?" (in-recursive-stack?))
  (if x (foo false) :done))
