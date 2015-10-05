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
