(ns jebediah.dbas-adapter.core
  (:require [clojure.string :as str]
            [clj-http.client :as client]
            [clj-fuzzy.metrics :as fuzzy-metrics]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]))

(def base (or (System/getenv "DBAS_BASE") "https://web.dbas.coruscant.cs.uni-duesseldorf.de"))
(def dbas-api-token (or (System/getenv "DBAS_TOKEN") "e76f7:07e063b1b28437704a1177a41ec271cb3a72ead5b1fc28c7802cac3ccb18d9c0"))
(def graphql-base (str base "/api/v2/query?q="))
(def api-base (str base "/api"))

(defn query [& qs]
  (:body (client/get (str graphql-base (str/replace (str/join qs) #"[\s\r\n]+" "")) {:as :auto})))

(defn api-query
  ([path]
   (:body (client/get (str api-base path) {:as :auto})))
  ([path nickname]
   (log/info "GET from:" path "with nickname" nickname)
   (:body (client/get (str api-base path) {:headers {:X-Authentication (json/write-str {:nickname nickname :token dbas-api-token})}
                                           :as      :auto}))))

(defn api-post
  ([path nickname body]
   (log/info "GET to: " path " with " body)
   (client/post (str api-base path) {:headers      {:X-Authentication (json/write-str {:nickname nickname :token dbas-api-token})}
                                     :as           :auto
                                     :content-type :json
                                     :body         body})))


(defn get-positions-for-issue [slug]
  (let [response (query "query{issue(slug:\"" slug "\"){statements(isStartpoint:true,isDisabled:false){uid,isStartpoint,isDisabled,textversions{content}}}}")]
    (get-in response [:issue :statements])))

(defn get-issues []
  (api-query "/issues"))

(defn get-positions [slug]
  (api-query (format "/%s" slug)))

(defn corrected-topic
  "If there is a matching topic, then this topic will be returned, else nil"
  [issues topic]
  (let [threshold 4
        most-likely-topic (->> issues
                               (map #(hash-map :topic % :distance (fuzzy-metrics/levenshtein topic (:title %))))
                               (sort-by :distance)
                               (first))]
    (when (>= threshold (:distance most-likely-topic))
      (:topic most-likely-topic))))

(defn sent-similarity [s1 s2]
  "How similar is s2 to s1?
  Returns a value bewteen -Infinity and 1. Were 1 denotes equality."
  (let [l (count s1)
        d (fuzzy-metrics/levenshtein s1 s2)]
    (if (zero? l)
      0
      (- 1 (/ d l)))))

(defn natural-topic->nearest-topics [free-form-topic language]
  (->> (get-issues)
       (filter #(= language (:language %)))
       (map #(hash-map :topic % :confidence (sent-similarity (.toLowerCase (:title %)) (.toLowerCase free-form-topic))))
       (sort-by :confidence >)))
