(ns positano.trace
  (:require [clojure.core.async :as async :refer [>!!]]
            [clojure.pprint :refer :all]
            [clojure.repl :as repl])
  (:require [positano.utils :refer [in-recursive-stack?]]))

(def event-channel (atom nil))

(def stacks (atom {}))

(def ^:dynamic recording? true)

(defmacro without-recording [& body]
  `(binding [recording? false]
     ~@body))

(defmacro with-recording [& body]
  `(binding [recording? true]
     ~@body))

(def ^{:doc "Forms to ignore when tracing forms." :private true}
      ignored-form? '#{def quote var try monitor-enter monitor-exit assert})

(defn ^{:private true} tracer
  "This function is called by trace. Prints to standard output, but
may be rebound to do anything you like. 'name' is optional."
  [name value]
  (println (str "TRACE" (when name (str " " name)) ": " value)))

(defn trace
  "Sends name (optional) and value to the tracer function, then
returns value. May be wrapped around any expression without
affecting the result."
  ([value] (trace nil value))
  ([name value]
     (tracer name (pr-str value))
     value))

(defn record-event [e]
  ;;TODO check events using prismatic schema here
  (when recording?
    (without-recording
     (when (and @event-channel (not (in-recursive-stack?)))
       (>!! @event-channel e)))))

(defn base-trace []
  {:timestamp (java.util.Date.)})

;; stack tracking functions (track who calls what)
(defn- maybe-init-thread-stack! [thread-id]
  (when-not (get @stacks thread-id)
    (swap! stacks assoc thread-id (atom (list)))))

(defn- push-id-to-stack! [thread-id event-id]
  (swap! (get @stacks thread-id) (fn [s] (cons event-id s))))

(defn- pop-stack! [thread-id]
  (swap! (get @stacks thread-id) rest))

(defn- current-caller [thread-id]
  (when-let [stack (get @stacks thread-id)]
    (first @stack)))

;;

(defn trace-fn-call
  "Traces a single call to a function f with args. 'name' is the
  symbol name of the function."
  [name ns f args]
  (without-recording
   (let [id        (gensym "")
         thread-id (.getId (Thread/currentThread))
         event     (merge (base-trace)
                          {:type :fn-call
                           :id (str "c" id)
                           :fn-name name
                           :ns (.name ns)
                           :thread thread-id
                           :fn-caller (when-let [caller (current-caller thread-id)] (str "c" caller))
                           :fn-args (into [] args)})]
     (with-recording (record-event event))
     (maybe-init-thread-stack! thread-id)
     (push-id-to-stack! thread-id id)
     (let [value (with-recording (apply f args))
           event (merge (base-trace)
                        {:type :fn-return
                         :id (str "r" id)
                         :fn-name name
                         :ns (.name ns)
                         :thread thread-id
                         :return-value value})]
       (pop-stack! thread-id)
       (with-recording (record-event event))
       value))))

(defmacro deftrace
  "Use in place of defn; traces each call/return of this fn, including
   arguments."
  [name & definition]
  `(do
     (defn ~@(cons name definition))
     (trace-var* (resolve '~name))))

(declare trace-form)
(defmulti trace-special-form (fn [form] (first form)))

(defn ^{:private true} trace-bindings
  "Trace the forms in the given binding list."
  [bindings]
  (vec (apply concat
              (map (fn [[sym value]]
                     `[~sym (trace-forms ~value)]) (partition 2 bindings)))))

 ;; Trace the let form, its bindings then the forms in its body.
(defmethod trace-special-form
  'let* [[_ bindings & body]]
  `(let* ~(trace-bindings bindings)
     (trace-forms ~@body)))

;; Trace the loop form, its bindings then the forms in its body.
(defmethod trace-special-form 
  'loop* [[_ bindings & body]]
  `(loop* ~(trace-bindings bindings)
     (trace-forms ~@body)))

;; Trace the new form, mainly its arguments.
(defmethod trace-special-form
  'new [[_ name & args]]
  `(new ~name ~@(map trace-form args)))

(defn ^{:private true} trace-fn-body
  "Trace the forms in a function body."
  [body]
  `(~(first body) ~@(map trace-form (rest body))))

;; Trace the fn form.
(defmethod trace-special-form 'fn* [[_ & args]]
  (if (symbol? (first args))
    (if (vector? (second args))
      `(fn* ~(first args) ~@(trace-fn-body (rest args)))
      `(fn* ~(first args) ~@(map trace-fn-body (rest args))))
    (if (vector? (first args))
      `(fn* ~@(trace-fn-body args))
      `(fn* ~@(map trace-fn-body args)))))

(defmethod trace-special-form :default [form] :default)

(defn ^{:private true} trace-value
  "Trace the given data structure by tracing individual values."
  [v]
  (cond
    (vector? v) `(vector ~@(map trace-form v))
    (map? v) `(into {} ~(vec (map trace-value v)))
    (set? v) `(into #{} ~(vec (map trace-form v)))
    :else v))

(defn ^{:private true} recurs?
  "Test if the given form contains a recur call."
  [form]
  (if (and (or (list? form)
               (seq? form))
           (> (count form) 0))
    (condp = (first form)
      'recur true
      'quote false
      (some identity (map recurs? (rest form))))
    false))

(defn ^{:private true} trace-form*
  "Trace the given form body except if it is to be ignored."
  [form]
  (if (and (or (list? form)
               (seq? form))
           (> (count form) 0))
    (if (ignored-form? (first form))
      form
      (let [sform (trace-special-form form)]
        (if (= sform :default)
          (let [mform (macroexpand-1 form)]
            (if (= form mform)
              (cons (first mform) (map trace-form (rest mform)))
              (trace-form mform)))
          sform)))
    (trace-value form)))

(defprotocol ThrowableRecompose
  "Protocol to isolate trace-form from convoluted throwables that 
   do not have a constructor with a single string argument.

   clone-throwable attempts to clone a throwable with a human readable stack trace
   and message :)
   It must return a throwable of the same class. If not clonable, the original
   throwable should be returned. At least this will preserve the original 
   throwable information.

   Cloning should be non-obtrusive hence internal exceptions should be silently
   swallowed and return the original throwable."
  (clone-throwable [this stack-trace args]))

(extend-type java.lang.AssertionError
  ThrowableRecompose
  (clone-throwable [this stack-trace args]
    (try
      (let [ctor (.getConstructor java.lang.AssertionError (into-array [java.lang.Object]))
            arg (first args)]
        (doto (.newInstance ctor (into-array [arg])) (.setStackTrace stack-trace)))
      (catch Exception e# this))))

(extend-type java.nio.charset.CoderMalfunctionError
  ThrowableRecompose
  (clone-throwable [this stack-trace args] 
    (try
      (let [ctor (.getConstructor java.nio.charset.CoderMalfunctionError (into-array [java.lang.Exception]))
            arg (first args)]
        (cond
          (instance? java.lang.Exception arg)      
          (doto (.newInstance ctor (into-array [arg])) (.setStackTrace stack-trace))
          (string? arg)
          (doto (.newInstance ctor (into-array [(Exception. arg)])) (.setStackTrace stack-trace))
          :else this))
      (catch Exception e# this))))

;; The following should be re-enabled when Java 5 support is dropped.

;(extend-type java.io.IOError
;  ThrowableRecompose
;  (clone-throwable [this stack-trace args] 
;    (try
;      (let [ctor (.getConstructor java.io.IOError (into-array [java.lang.Throwable]))
;            arg (first args)]
;        (cond
;          (instance? java.lang.Throwable (first arg))
;          (doto (.newInstance ctor (into-array [arg])) (.setStackTrace stack-trace))
;          
;          (string? arg)
;          (doto (.newInstance ctor (into-array [(Throwable. arg)])) (.setStackTrace stack-trace))
;          :else this))
;      (catch Exception e# this))))  

(extend-type java.lang.ThreadDeath
  ThrowableRecompose
  (clone-throwable [this _ _] this)) ;; No way we can add more info here, this one has no args to its constructor

(extend-type java.lang.Throwable
  ThrowableRecompose
  (clone-throwable [this stack-trace args] 
    (try
      (let [ctor (.getConstructor (class this) (into-array [java.lang.String]))
            arg (first args)]
        (cond
          (string? arg)
          (doto (.newInstance ctor (into-array [arg])) (.setStackTrace stack-trace))
          :else (doto (.newInstance ctor (into-array [(str arg)])) (.setStackTrace stack-trace))))
      (catch Exception e# this))))

(extend-type java.lang.Object
  ThrowableRecompose
  (ctor-select [this _ _] this)) ;; Obviously something is wrong but the trace should not alter processing

(extend-type nil
  ThrowableRecompose
  (ctor-select [this _ _] this)) ;; Obviously something is wrong but the trace should not alter processing

(defn trace-compose-throwable 
  "Re-create a new throwable with a composed message from the given throwable
   and the message to be added. The exception stack trace is kept at a minimum."
  [^Throwable throwable ^String message]
  (let [previous-msg (or (.getMessage throwable) (format ": No message attached to throwable %s" throwable))
        composed-msg (str previous-msg (if-not (.endsWith previous-msg "\n") "\n") message (if-not (.endsWith message "\n") "\n"))
        new-stack-trace (into-array java.lang.StackTraceElement [(aget (.getStackTrace throwable) 0)])
        new-throwable (clone-throwable throwable new-stack-trace [composed-msg])]
    new-throwable))

(defn trace-form
  "Trace the given form avoiding try catch when recur is present in the form."
  [form]
  (if (recurs? form)
    (trace-form* form)
    `(try
       ~(trace-form* form)
       (catch Throwable e#
         (throw (trace-compose-throwable e# (format "  Form failed: %s" (with-out-str (pprint '~form)))))))))

(defmacro trace-forms
  "Trace all the forms in the given body. Returns any underlying uncaught exceptions that may make the forms fail."
  [& body]
  `(do
     ~@(map trace-form body)))

(defn- function->var [fun]
  (some-> fun class .getName repl/demunge symbol resolve))

(defn trace-var* ;;TODO rename this to reflect the fact that it also handles functions
  "If the specified Var holds an IFn and is not marked as a macro, its
  contents are replaced with a version wrapped in a tracing call;
  otherwise nothing happens. Can be undone with untrace-var.

  In the unary case, v should be a Var object or a symbol to be
  resolved in the current namespace, or a function that is named.

  In the binary case, ns should be a namespace object or a symbol
  naming a namespace and s a symbol to be resolved in that namespace."
  ([ns s]
     (trace-var* (ns-resolve ns s)))
  ([s]
   (let [^clojure.lang.Var v (cond (var? s) s
                                   (fn? s) (function->var s)
                                   (symbol? s) (resolve s))
         _ (when-not v
             (throw (ex-info (format "Cannot resolve symbol %s" s) {})))
         ns (.ns v)
         s  (.sym v)]
     (if (and (ifn? @v) (-> v meta :macro not) (-> v meta ::traced not))
       (let [f @v
             vname (symbol (str ns "/" s))]
         (doto v
           (alter-var-root #(fn tracing-wrapper [& args]
                              (trace-fn-call s ns % args)))
           (alter-meta! assoc ::traced f)))))))

(defn untrace-var*
  "Reverses the effect of trace-var / trace-vars / trace-ns for the
  given Var, replacing the traced function with the original, untraced
  version. No-op for non-traced Vars.

  Argument types are the same as those for trace-var."
  ([ns s]
     (untrace-var* (ns-resolve ns s)))
  ([v]
     (let [^clojure.lang.Var v (if (var? v) v (resolve v))
           ns (.ns v)
           s  (.sym v)
           f  ((meta v) ::traced)]
       (when f
         (doto v
           (alter-var-root (constantly ((meta v) ::traced)))
           (alter-meta! dissoc ::traced))))))

(defn traced?
  "Returns true if the given var is currently traced, false otherwise"
  [s]
  (let [^clojure.lang.Var v (if (var? s) s (resolve s))]
    (if-not v
      (throw (ex-info (format "Symbol %s cannot be resolved" s) {}))
      (-> v meta ::traced nil? not))))

(defn all-fn-vars
  ([]
   (mapcat all-fn-vars (all-ns)))
  ([ns]
   (->> ns ns-interns vals (filter (comp fn? var-get)))))

(defn untrace-all
  []
  (without-recording
   (doseq [v (filter traced? (all-fn-vars))]
     (untrace-var* v))))

(defmacro trace-vars
  "Trace each of the specified Vars.
  The arguments may be Var objects or symbols to be resolved in the current
  namespace."
  [vs]
  `(do ~@(for [x vs] `(trace-var* (quote ~x)))))

(defmacro untrace-vars
  "Untrace each of the specified Vars.
  Reverses the effect of trace-var / trace-vars / trace-ns for each
  of the arguments, replacing the traced functions with the original,
  untraced versions."
  [& vs]
 `(do ~@(for [x vs] `(untrace-var* (quote ~x)))))

(defn- ns-starts-with? [ns prefix]
  (.startsWith (-> ns .name str) prefix))

(defn- skip-ns-tracing? [ns]
  (or ('#{clojure.core clojure.core.protocols clojure.tools.trace} (.name ns))
      (ns-starts-with? ns "datomic")
      (ns-starts-with? ns "clojure.tools.analyzer")
      (ns-starts-with? ns "clojure.core.async.")
      (ns-starts-with? ns "refactor-nrepl.")
      (ns-starts-with? ns "clojure.tools.nrepl")
      (ns-starts-with? ns "clojure.repl")
      (ns-starts-with? ns "cider.")
      (ns-starts-with? ns "deps.")
      (ns-starts-with? ns "positano.")))

(defn trace-ns*
  "Replaces each function from the given namespace with a version wrapped
  in a tracing call. Can be undone with untrace-ns. ns should be a namespace
  object or a symbol.

  No-op for clojure.core, clojure.tools.trace and positano.trace."
  [ns]
  (let [ns (the-ns ns)]
    (when-not (skip-ns-tracing? ns)
      (println "Tracing ns" (.name ns))
      (let [ns-fns (all-fn-vars ns)]
        (doseq [f ns-fns]
          (trace-var* f))))))

(defmacro trace-ns
  "Trace all fns in the given name space."
  [ns]
  `(trace-ns* ~ns)) 

(defn untrace-ns*
  "Reverses the effect of trace-var / trace-vars / trace-ns for the
  Vars in the given namespace, replacing each traced function from the
  given namespace with the original, untraced version."
  [ns]
  (let [ns-fns (->> ns the-ns ns-interns vals)]
    (doseq [f ns-fns]
          (untrace-var* f))))

(defmacro untrace-ns
  "Untrace all fns in the given name space."
  [ns]
  `(untrace-ns* ~ns))

(defn trace-all
  "Traces all vars in all namespaces with a few exceptions (see
  private function skip-ns-tracing?). WARNING: This is risky and most
  likely to destabilise your application, generally a bad idea."
  []
  (without-recording
   (doseq [n (all-ns)] (trace-ns* n))))

(defn traceable?
  "Returns true if the given var can be traced, false otherwise"
  [v]
  (let [^clojure.lang.Var v (if (var? v) v (resolve v))]
    (and (ifn? @v) (-> v meta :macro not))))

(defn var-ns-name [v]
  (-> v .ns .name str))

(defn var-full-name [v]
  (str v))

(defn ns-prefix? [v prefix]
  (.startsWith (var-ns-name v) prefix))

(defn full-name-prefix? [v prefix]
  (.startsWith (str v) prefix))
