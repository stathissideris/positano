# 2018-05-13

It turns out that clojure.tools.analyzer contains some functionality
for converting ASTs into data structures that can be queried directly
with the datomic datalog (without being transacted, see
[this gist](https://gist.github.com/stuarthalloway/2645453). The
relevant functionality
[is here](https://github.com/clojure/tools.analyzer/blob/master/src/main/clojure/clojure/tools/analyzer/ast/query.clj). This
converts most of the tree to a list of `[ast k v]` triplets, similar
to the gist. The problem is that the
`clojure.tools.analyzer.ast/ast->eav` function only descents into
whatever is listed under `:children` for each node, which means that
you can't really go into the `:meta` or `:env` maps.
