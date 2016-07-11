# TODO

* Start collecting function facts in datascript (such as arg-lists)
* Guard against record-event chain reactions blowing the stack
* Integrate (non-existent) data provider
* Run clojure.core tests, derive type annotations and compare to
  existing (hand-crafted) annotations.
* Check let-flow macro https://github.com/ztellman/manifold/blob/d2570579f972a6860a4661dba3053cf9d209f1a4/src/manifold/deferred.clj

# DONE

* Replaced datomic with datascript
* Make keys sent to event channel be identical to what goes into datomic
* Use a transducer for event processing
