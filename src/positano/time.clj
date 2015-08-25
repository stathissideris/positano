(ns positano.time
  (:require [clojure.string :as str]))

(def second-in-millis 1000)
(def minute (* 60 second-in-millis))
(def hour (* 60 minute))
(def day (* 24 hour))
(def month (* 30 day))
(def year (* 365 day))

(defn to-millis [x]
  (.getTime x))

(defn duration [millis]
  (let [years (int (/ millis year))
        millis (mod millis year)

        months (int (/ millis month))
        millis (mod millis month)

        days (int (/ millis day))
        millis (mod millis day)

        hours (int (/ millis hour))
        millis (mod millis hour)

        minutes (int (/ millis minute))
        millis (mod millis minute)

        seconds (int (/ millis second-in-millis))
        millis (mod millis second-in-millis)]
    (merge
     (when-not (zero? years) {:years years})
     (when-not (zero? months) {:months months})
     (when-not (zero? days) {:days days})
     (when-not (zero? hours) {:hours hours})
     (when-not (zero? minutes) {:minutes minutes})
     (when-not (zero? seconds) {:seconds seconds})
     {:millis millis})))

(defn difference [a b]
  (duration
   (- (to-millis b) (to-millis a))))

(defn duration-string [{:keys [years months days hours minutes seconds millis] :as d}]
  (let [has? (fn [x] (and x (not (zero? x))))]
   (if (and (= 1 (count d)) (:millis d)) (str millis "msec")
       (str/join
        ", "
        (remove nil?
         [(when (has? years)   (str years " years"))
          (when (has? months)  (str months " months"))
          (when (has? days)    (str days " days"))
          (when (has? hours)   (str hours "h"))
          (when (has? minutes) (str minutes "min"))
          (when (has? seconds) (str seconds "sec"))
          (when (has? millis)  (str millis "msec"))])))))
