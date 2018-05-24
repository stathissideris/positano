(ns positano.analyze.spec
  (:require [clojure.spec.alpha :as s]))

;;(s/def ::ns ns?)

(s/def
 ::tag
 (s/nilable
  #{clojure.lang.Symbol clojure.lang.Keyword
    clojure.lang.AFunction java.lang.Thread java.lang.Class
    java.lang.Boolean clojure.lang.AReference
    clojure.lang.PersistentVector java.lang.Object
    java.lang.Number
    java.lang.String java.lang.ClassLoader
    clojure.lang.IPersistentMap clojure.lang.ISeq
    clojure.lang.PersistentArrayMap clojure.lang.Associative
    java.lang.Long java.util.Date clojure.lang.Var
    java.util.concurrent.Callable}))

(s/def
 ::class
 (s/or
  :map
  (s/keys
   :req-un
   [::env ::form ::literal? ::o-tag ::op ::tag ::type ::val])
  :simple
  (s/or :any any? :symbol symbol?)))

(s/def ::end-column int?)
(s/def ::end-line int?)
(s/def ::column int?)
(s/def ::line int?)
(s/def ::max-fixed-arity integer?)
(s/def ::once boolean?)
(s/def ::body? boolean?)
(s/def ::var var?)
(s/def ::loop-id symbol?)
(s/def ::loop-locals int?)
(s/def ::no-recur boolean?)
(s/def ::field symbol?)
(s/def ::variadic? boolean)
(s/def ::fixed-arity integer?)
(s/def ::arg-id integer?)
(s/def ::alias-info (s/map-of symbol? symbol?))
(s/def ::ignore-tag boolean?)
(s/def ::static boolean?)
(s/def ::exprs (s/coll-of any?))
(s/def ::return-tag any?)
(s/def ::private boolean?)
(s/def ::maybe-arity-mismatch boolean?)
(s/def ::rettag (s/nilable #{})) ;;???
(s/def ::context #{:ctx/return :ctx/expr :ctx/statement})
(s/def ::literal? boolean?)
(s/def ::in-try boolean)
(s/def ::added string?)
(s/def ::catches (s/coll-of any?)) ;;???
(s/def ::o-tag (s/nilable any?))
(s/def ::file string?)
(s/def ::doc string?)
(s/def ::type #{:number :symbol :string :vector :keyword :class :nil :map})


(s/def ::locals
  (s/or :empty (s/and empty? map?)
        :non-keyword-map (s/map-of
                          symbol?
                          (s/keys :req-un [::form ::local ::name ::op]
                                  :opt-un [::arg-id ::children ::init ::variadic?]))))

(s/def ::env (s/keys
              :req-un [::column
                       ::context
                       ::end-column
                       ::end-line
                       ::file
                       ::line
                       ::locals
                       ::ns]
              :opt-un [::in-try ::loop-id ::loop-locals ::no-recur ::once]))


(s/def
 ::params
 (s/coll-of
  (s/keys
   :req-un
   [::arg-id ::env ::form ::local ::name ::op ::variadic?]
   :opt-un
   [::atom ::o-tag ::tag])))

(s/def ::fn (s/keys :req-un [::env ::form ::op]
                    :opt-un [::arg-id ::arglists ::assignable? ::atom ::children
                             ::local ::max-fixed-arity ::meta ::methods ::name
                             ::o-tag ::once ::raw-forms ::return-tag ::tag ::var ::variadic?]))

(s/def ::arglist (s/coll-of symbol?))

(s/def ::arglists
  (s/coll-of
   (s/coll-of
    (s/or
     :collection
     ::arglist))))

(s/def ::method symbol?)

(s/def
 ::children
 (s/coll-of
  #{:args :finally :ret :fn :meta :instance :init :exprs :else
    :bindings :params :vals :catches :keys :methods :expr :class :then
    :target :body :local :test :statements}))

(s/def ::statements
  (s/coll-of (s/keys
              :req-un
              [::args ::children ::env ::form ::o-tag ::op]
              :opt-un
              [::class
               ::fn
               ::instance
               ::method
               ::raw-forms
               ::result
               ::tag
               ::top-level
               ::validated?])))

(s/def
 ::body
 (s/keys
  :req-un
  [::body? ::children ::env ::form ::op]
  :opt-un
  [::args
   ::assignable?
   ::bindings
   ::body
   ::class
   ::else
   ::fn
   ::instance
   ::loop-id
   ::m-or-f
   ::meta
   ::method
   ::o-tag
   ::raw-forms
   ::ret
   ::statements
   ::tag
   ::target
   ::test
   ::then
   ::validated?]))

(comment
 (s/def
   ::val
   (s/nilable
    (s/or
     :collection
     (s/coll-of (s/or :keyword keyword? :symbol symbol?))
     :map
     (s/keys
      :opt-un
      [::arglists
       ::column
       ::doc
       ::end-column
       ::end-line
       ::file
       ::line
       ::private
       ::rettag])
     :simple
     #{0 :timed-out 1 :done :else java.lang.Thread "/" clojure.core 3 2
       positano.utils java.util.Date "$"}))))

(s/def
 ::vals
 (s/coll-of
  (s/keys
   :req-un
   [::args
    ::children
    ::class
    ::env
    ::form
    ::instance
    ::m-or-f
    ::method
    ::o-tag
    ::op
    ::raw-forms
    ::tag
    ::validated?])))

(s/def ::args
  (s/coll-of
   (s/keys :req-un [::env ::form ::op]
           :opt-un [::arg-id ::arglists ::args ::assignable? ::atom ::body? ::children ::class
                    ::expr ::field ::fn ::instance ::keys ::literal? ::local ::m-or-f
                    ::max-fixed-arity ::meta ::method ::methods ::name ::o-tag ::once
                    ::raw-forms ::return-tag ::tag ::target ::type ::val ::validated? ::vals
                    ::var ::variadic?])))

(s/def ::instance
  (s/keys :req-un [::env ::form ::o-tag ::op ::tag]
          :opt-un [::arg-id ::args ::assignable? ::atom ::children ::class ::expr ::instance
                   ::literal? ::local ::m-or-f ::method ::name ::raw-forms ::validated? ::variadic?]))

(s/def ::ret (s/keys :req-un [::env ::form ::op]
                     :opt-un [::args ::assignable? ::body ::catches ::children
                              ::else ::exprs ::finally ::fn ::ignore-tag ::literal?
                              ::loop-id ::m-or-f ::o-tag ::raw-forms ::result ::tag
                              ::target ::test ::then ::top-level ::type ::val]))

(s/def ::expr
  (s/keys :req-un [::env ::form ::o-tag ::op ::tag]
          :opt-un [::arglists ::children ::literal? ::max-fixed-arity ::meta ::methods ::once
                   ::return-tag ::type ::val ::variadic?]))

(s/def ::name symbol?)
(s/def ::assignable? boolean?)
(s/def ::atom any?)

(s/def
 ::keys
 (s/coll-of
  (s/keys
   :req-un
   [::assignable?
    ::class
    ::env
    ::field
    ::form
    ::o-tag
    ::op
    ::raw-forms
    ::tag])))

(s/def
 ::methods
 (s/coll-of
  (s/keys
   :req-un
   [::body
    ::children
    ::env
    ::fixed-arity
    ::form
    ::loop-id
    ::op
    ::params
    ::variadic?]
   :opt-un
   [::arglist ::local ::o-tag ::tag])))


(s/def ::local
  (s/or :map (s/keys :req-un [::form ::local ::name ::op]
                     :opt-un [::atom ::env ::o-tag ::tag])
        :simple #{:let :fn :arg}))

(s/def ::finally (s/keys :req-un [::args ::body? ::children ::class ::env ::form ::method ::o-tag
                                  ::op ::raw-forms ::tag ::validated?]))


;;???
;; (s/def ::m-or-f #{getStackTrace CHAR_MAP getClass getClassLoader getTime getClassName
;;                   currentThread length})

(s/def
 ::init
 (s/keys
  :req-un
  [::children ::env ::form ::op]
  :opt-un
  [::arg-id
   ::arglists
   ::args
   ::assignable?
   ::atom
   ::class
   ::expr
   ::fn
   ::local
   ::m-or-f
   ::maybe-arity-mismatch
   ::meta
   ::method
   ::name
   ::o-tag
   ::raw-forms
   ::return-tag
   ::tag
   ::target
   ::validated?
   ::variadic?]))

(s/def
 ::test
 (s/keys
  :req-un
  [::env ::form ::o-tag ::op]
  :opt-un
  [::args
   ::children
   ::class
   ::fn
   ::instance
   ::literal?
   ::meta
   ::method
   ::raw-forms
   ::tag
   ::type
   ::val
   ::validated?]))

(s/def
 ::then
 (s/keys
  :req-un
  [::env ::form ::o-tag ::op]
  :opt-un
  [::children
   ::ignore-tag
   ::literal?
   ::ret
   ::statements
   ::tag
   ::type
   ::val]))

(s/def
 ::else
 (s/keys
  :req-un
  [::env ::form ::o-tag ::op]
  :opt-un
  [::children
   ::else
   ::literal?
   ::raw-forms
   ::ret
   ::statements
   ::tag
   ::test
   ::then
   ::type
   ::val]))

(s/def
 ::target
 (s/keys
  :req-un
  [::env ::form ::op]
  :opt-un
  [::arg-id
   ::args
   ::assignable?
   ::atom
   ::children
   ::class
   ::fn
   ::literal?
   ::local
   ::m-or-f
   ::name
   ::o-tag
   ::raw-forms
   ::target
   ::type
   ::val
   ::variadic?]))

(s/def
 ::bindings
 (s/coll-of
  (s/keys
   :req-un
   [::atom
    ::children
    ::env
    ::form
    ::init
    ::local
    ::name
    ::o-tag
    ::op
    ::tag])))

(s/def
 ::finally
 (s/keys
  :req-un
  [::args
   ::body?
   ::children
   ::class
   ::env
   ::form
   ::method
   ::o-tag
   ::op
   ::raw-forms
   ::tag
   ::validated?]))

(s/def
 ::meta
 (s/keys
  :opt-un
  [::added
   ::arglists
   ::column
   ::doc
   ::end-column
   ::end-line
   ::env
   ::file
   ::form
   ::line
   ::literal?
   ::name
   ::ns
   ::o-tag
   ::op
   ::private
   ::static
   ::tag
   ::type
   ::val]))

(s/def ::op #{:let :fn :do :maybe-class :instance-call :if :fn-method :new :recur
              :with-meta :loop :binding :const :var :quote :host-interop :invoke
              :def :try :host-call :local :the-var :map :static-field
              :static-call})

(s/def ::ast (s/keys :req-un [::env ::form ::op ::raw-forms ::result ::top-level]
                     :opt-un [::alias-info ::arglists ::children ::init ::literal?
                              ::meta ::name ::o-tag ::ret ::return-tag ::statements ::tag ::type ::val ::var]))
