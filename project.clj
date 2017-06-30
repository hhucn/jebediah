(defproject jebediah "0.1.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                 [org.clojure/tools.logging "0.4.0"]
                 [compojure "1.5.1"]
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-json "0.4.0"]
                 [ring-logger "0.7.7"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-http "3.6.1"]]
  :plugins [[lein-ring "0.9.7"]
            [lein-kibit "0.1.5"]]
  :source-paths ["src" "lib/apiai-clj/src"]
  :ring {:handler jebediah.handler/app}
  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring/ring-mock "0.3.0"]]}})
