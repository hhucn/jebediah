(ns jebediah.dbas-adapter.core
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [clj-http.client :as client]
            [clj-fuzzy.metrics :as fuzzy-metrics]
            [clojure.tools.logging :as log]))

(def base "http://0.0.0.0:4284")
;(def base (or (System/getenv "DBAS_BASE") "https://dbas.cs.uni-duesseldorf.de"))
(def dbas-base (str base "/api/v2/query?q="))
(def api-base (str base "/api"))

(defn slurp-json [f]
  (-> f
      (slurp)
      (json/read-str :key-fn keyword)))

(defn query [& qs]
  (slurp-json (str dbas-base (str/replace (str/join qs) #"\s" ""))))

(defn get-issues
  "Return all issues from dbas or nil." []
  (let [response (query "query{issues{uid, title, slug}}")]
    (sort-by :uid (:issues response))))

(defn get-positions-for-issue [slug]
  (let [response (query "query{issue(slug:\"" slug "\"){statements(isStartpoint:true,isDisabled:false){uid,isStartpoint,isDisabled,textversions{content}}}}")]
    (get-in response [:issue :statements])))

(defn issue? [issues topic]
  (let [slugs (map :slug issues)]
    (some #{topic} slugs)))

(defn add-position [slug statement reason]
  (let [route "/add/start_statement"]
    (client/post (str api-base route) {:body (json/write-str {:statement statement
                                                              :reason reason
                                                              :slug slug})})))

(defn corrected-topic
  "If there is a matching topic, then this topic will be returned, else nil"
  [issues topic]
  (let [threshold 4
        most-likely-topic (->> issues
                               (map #(hash-map :topic % :distance (fuzzy-metrics/levenshtein topic (:title %))))
                               (sort-by :distance)
                               (log/spy :info)
                               (first))]
    (when (>= threshold (:distance most-likely-topic))
      (:topic most-likely-topic))))
