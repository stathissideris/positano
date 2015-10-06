# Related tools

Tools that do something similar to positano, and could potentially be used as dependencies.

## refactor-nrepl

refactor-nrepl has [some code](https://github.com/clojure-emacs/refactor-nrepl/blob/master/src/refactor_nrepl/find/find_symbol.clj#L234) for finding all the usages of a symbol.

For example, do:

```clojure

> (require '[refactor-nrepl.find.find-symbol :as fs])
> (fs/find-symbol
    {:file "s:/devel/positano/src/positano/analyze.clj"
     :line 19
     :column 47
     :name "fun"})
```

And you get:

```clojure
({:line-beg 15,
  :line-end 15,
  :col-beg 4,
  :col-end 7,
  :name "fun",
  :file "S:\\devel\\positano\\src\\positano\\analyze.clj",
  :match "[fun]"}
 {:line-beg 16,
  :line-end 16,
  :col-beg 22,
  :col-end 25,
  :name "fun",
  :file "S:\\devel\\positano\\src\\positano\\analyze.clj",
  :match "(let [var (resolve fun)]"}
 {:line-beg 19,
  :line-end 19,
  :col-beg 46,
  :col-end 49,
  :name "fun",
  :file "S:\\devel\\positano\\src\\positano\\analyze.clj",
  :match "(throw (ex-info (str \"Cannot resolve \" fun) {}))"}
 {:line-beg 26,
  :line-end 26,
  :col-beg 32,
  :col-end 35,
  :name "fun",
  :file "S:\\devel\\positano\\src\\positano\\analyze.clj",
  :match "(let [source (some-> fun source-fn read-string)]"})
```

Also, nrepl-refactor has its
[own analyzer](https://github.com/clojure-emacs/refactor-nrepl/blob/master/src/refactor_nrepl/analyzer.clj)
which looks like it uses `clojure.tools.analyzer.jvm` with some extras.

```clojure
> (require '[refactor-nrepl.analyzer :as ana])
> (def a (ana/ns-ast (slurp "s:/devel/positano/src/positano/analyze.clj")))
> (->> a
       second
       (tree-seq (some-fn sequential? map?) seq)
       (filter #(= :invoke (:op %)))
       (drop 3) first :fn :var meta :name)
```

You can analyze the aliases for a namespace:

```clojure
> (def al (ana/parse-ns (slurp "s:/devel/positano/src/positano/analyze.clj")))

[positano.analyze
 {walk clojure.walk,
  ana clojure.tools.analyzer.jvm,
  e clojure.tools.analyzer.passes.jvm.emit-form}]
```

## ASM

The problem with using ASM to load the modified classes is that you
need access to the class loader that loaded the original
class. Clojure uses a dynamic class loader that can be accessed via:

```clojure
(clojure.lang.RT/baseLoader)
```

## Clojure compiler

### How Clojure classes are compiled in memory

The `clojure.lang.Compiler.ObjExpr` class contains a `bytecode` field
which is populated by the `compile()` method os the same class. The
`getCompiledClass()` method of `ObjExpr` sends the bytecode to the
`DynamicClassLoader` via its `defineClass()` method. This also puts it
in the cache of the `DynamicClassLoader`, but the bytecode is not kept
around.

But! The class loader used by the compiler is the dynamic var
`clojure.lang.Compiler.LOADER`. This is probably because,
[as chouser points out](http://stackoverflow.com/questions/7471316/how-does-clojure-class-reloading-work), each top-level form uses its own class loader:

```clojure
> (deftype T [a b])
> (def x (T. 1 2))
> (deftype T [a b])
> (def y (T. 3 4))
> (-> x class .getClassLoader)
#object[clojure.lang.DynamicClassLoader 0x428e8272 "clojure.lang.DynamicClassLoader@428e8272"]
> (-> y class .getClassLoader)
#object[clojure.lang.DynamicClassLoader 0x4e7886b8 "clojure.lang.DynamicClassLoader@4e7886b8"]
> (cast T x)
ClassCastException Cannot cast dev.T to dev.T  java.lang.Class.cast (Class.java:3369)
```

Looking at the Clojure implementation,
`clojure.lang.RT/makeClassLoader` is called by
`clojure.lang.Compiler/load`, `clojure.lang.Compiler/eval` and
`clojure.lang.Compiler/compile1`.

[This post](http://hum.ph/classloader-swapping-in-clojure) indicates
that it is possible to swap class loaders in Clojure pretty easily, so
it would be possible to make a class loader that intruments the
bytecode passed to it.

## sleight

[sleight](https://github.com/ztellman/sleight) does whole-program
transformations, but has to be used via the lein-sleight plugin. The
transformation is achieved by doing an `alter-var-root` on
`clojure.core/load` and `clojure.core/eval`.

You can start a REPL with sleight plugged in by doing (see
sleight-test project):

```
lein sleight repl
```

The code does indeed get instrumented, but changing the code in a clj
file and running `cider-load-buffer` (`c-c, c-k`) wipes out the
instrumentation and you get un-transformed code.

Next up, I would like to see if sleight survives a
`clojure.tools.namespace/refresh`, but running this really messed up
the state of namespaces etc in the project:

```
lein with-profile dev sleight repl
```

So I moved `org.clojure/tools.namespace` to the main dependencies. Which led to an error:

```
CompilerException java.lang.Exception: namespace 'clojure.tools.namespace.dependency' not found, compiling:(clojure/tools/namespace/track.clj:1:964)
```

The `clojure.tools.namespace.dependency` namespace comes from a
`.cljc` file, which leads me to believe that maybe sleight does not
play well with reader conditionals (sleight's own `project.clj`
mentions clojure `1.5.1`). This
[line](https://github.com/ztellman/sleight/blob/master/src/sleight/rt.clj#L74)
suggests that sleight only looks for `.clj` files.
