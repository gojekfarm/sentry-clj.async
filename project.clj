(defproject farm.gojek/sentry-clj.async "0.0.1"
  :description "Sentry client to report error events asynchronously"
  :url "https://github.com/gojekfarm/sentry-clj.async"
  :license {:name "Apache License, Version 2.0"
            :url  "https://www.apache.org/licenses/LICENSE-2.0"}
  :repl-options {:host "0.0.0.0"
                 :port 1337}
  :dependencies [[com.google.guava/guava "23.0"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.logging "0.4.1"]
                 [raven-clj "1.5.2"]]
  :profiles {:dev {:plugins [[jonase/eastwood "0.2.5"
                              :exclusions [org.clojure/clojure]]
                             [lein-ancient "0.6.15"]
                             [lein-cljfmt "0.5.7"]
                             [lein-kibit "0.1.6"]]}})
