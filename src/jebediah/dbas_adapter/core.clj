(ns jebediah.dbas-adapter.core
  (:require [clojure.data.json :as json]
            [clojure.string :as str]))

(def dbas-base "http://0.0.0.0:4284/api/v2/query?q=")

(defn slurp-json [f]
  (-> f
      (slurp)
      (json/read-str :key-fn keyword)))

(defn slurp-dbas [& qs]
  (slurp-json (str dbas-base (str/replace (str/join qs) #"\s" ""))))

(defn get-issues
  "Return all issues from dbas or nil." []
  (let [response (slurp-dbas "query{issues{uid, title}}")]
    (sort-by :uid (get-in response [:issues]))))

(defn get-positions-for-issue [slug]
  (let [response (slurp-dbas "query{issue(slug:\"" slug "\"){statements(isStartpoint: true){textversions{content}}}}")]
    (map #(get-in % [:textversions :content]) (get-in response [:issue :statements]))))

