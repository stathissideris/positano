# TODO

* Make keys sent to event channel be identical to what goes into datomic
* Add a datascript backend
* Start collecting function facts in datomic (such as arg-lists)
* Guard against record-event chain reactions blowing the stack
* Run clojure.core tests, derive type annotations and compare to
  existing (hand-crafted) annotations.

# DONE

* Use a transducer for event processing
