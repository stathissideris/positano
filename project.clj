(defproject positano "0.3.0-SNAPSHOT"
  :description "Provenace system for Clojure"
  :url "https://github.com/stathissideris/positano"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [com.datomic/datomic-free "0.9.5697"]
                 [refactor-nrepl "2.3.1"]
                 ^:source-dep [datascript "0.16.5"]
                 ^:source-dep [org.clojure/core.async "0.4.474"]
                 ^:source-dep [org.clojure/tools.analyzer.jvm "0.7.2"]]

  ;;:pedantic? :abort

  :plugins [[thomasa/mranderson "0.4.8"]]
  :profiles {:dev {:source-paths ["src" "dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [spec-provider "0.4.14"]
                                  [com.clojure-goes-fast/clj-memory-meter "0.1.0"]]}})
