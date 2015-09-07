(ns positano.db-test
  (:require [positano.db :refer :all]
            [clojure.test :refer :all])
  (:import java.nio.ByteBuffer))

(def edn-str @#'positano.db/edn-str)
(def read-edn-string @#'positano.db/read-edn-string)

(defrecord FakeRecord [foo])

(defmethod clojure.core/print-method FakeRecord
  [mt ^java.io.Writer writer]
  (.write writer "SO FAKE!"))

(deftest test-edn-str
  (is (= "[1 2 3]" (edn-str [1 2 3])))
  (is (= "SO FAKE!" (pr-str (FakeRecord. 6))))
  (is (= "#positano.db_test.FakeRecord[6]" (edn-str (FakeRecord. 6))))
  (let [[c _ s] (read-edn-string (edn-str (ByteBuffer/allocate 1)))]
    (is (= java.nio.HeapByteBuffer c))
    (is (= "java.nio.HeapByteBuffer[pos=0 lim=1 cap=1]" s))))
