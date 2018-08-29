(defproject jebediah "0.2.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [metosin/compojure-api "2.0.0-alpha19"]
                 [ring-basic-authentication "1.0.5"]
                 [ring-logger "1.0.1"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-http "3.9.0"]
                 [clj-fuzzy "0.4.1"]]
  :plugins [[lein-ring "0.9.7"]]
  :source-paths ["src"]
  :ring {:init    jebediah.handler/init
         :handler jebediah.handler/app}
  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring/ring-mock "0.3.2"]]}})
