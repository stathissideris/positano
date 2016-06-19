(defproject positano "0.3.0-SNAPSHOT"
  :description "Provenace system for Clojure"
  :url "https://github.com/stathissideris/positano"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [datascript "0.15.0"]
                 [org.clojure/core.async "0.2.385"]
                 [org.clojure/tools.analyzer.jvm "0.6.10"]]

  ;;:pedantic? :abort

  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]]}})
