(defproject positano "0.3.0-SNAPSHOT"
  :description "Provenace system for Clojure"
  :url "https://github.com/stathissideris/positano"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [datascript "0.13.1"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/tools.analyzer.jvm "0.6.6"]]

  :pedantic? :abort
  
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [fipp "0.6.2"]]}})

