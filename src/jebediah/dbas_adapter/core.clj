(ns jebediah.dbas-adapter.core
  (:require [clojure.string :as str]
            [clj-http.client :as client]
            [clj-fuzzy.metrics :as fuzzy-metrics]
            [taoensso.timbre :as log]
            [clojure.data.json :as json]
            [jebediah.config :refer [dbas-url dbas-api-token]])
  (:import (java.net ConnectException)))

(def graphql-base (str dbas-url "/api/v2/query?q="))
(def api-base (str dbas-url "/api"))

(defn dbas-available? []
  (try
    (boolean (client/head (str api-base "/issues") {:throw-exceptions false}))
    (catch ConnectException e false)))

(defn query [& qs]
  (:body (client/get (str graphql-base (str/replace (str/join qs) #"[\s\r\n]+" "")) {:as :auto})))

(defn- prepend-slash
  "Prepends a slash, if there isn't. Else justs returns the input"
  [path]
  (if-not (= \/ (first path))
    (str \/ path)
    path))

(defn- merge-paths
  "Merges multiple paths behind a base."
  [base & paths]
  (str base (str/join (map prepend-slash paths))))

(defn api-query!
  ([path]
   (:body (client/get (merge-paths api-base path) {:as :auto})))
  ([path nickname]
   (log/debug "GET from:" path "with nickname" nickname)
   (:body (client/get (merge-paths api-base path) (merge {:as :auto}
                                                         (when nickname
                                                           {:headers {:X-Authentication (json/write-str {:nickname nickname :token dbas-api-token})}}))))))


(defn api-post!
  ([path nickname body]
   (log/debug "POST to: " path " with " body)
   (let [response (client/post (merge-paths api-base path) {:headers      {:X-Authentication (json/write-str {:nickname nickname :token dbas-api-token})}
                                                            :as           :auto
                                                            :content-type :json
                                                            :body         (json/write-str body)})]
     (merge (:body response)
            (select-keys response [:trace-redirects])))))   ; this is needed to detect if the POST results in a 'finish' url

(defn create-dbas-oauth-account! [fb-user]
  (let [{:keys [first_name last_name id email locale gender] :or {email  "jeb@dbas.cs.uni-duesseldorf.de"
                                                                  locale "de_DE"
                                                                  gender "n"}} fb-user
        nickname (log/spy :debug (str first_name last_name id))]
    (try
      (api-post! "/users" "Tobias" {:firstname first_name
                                    :lastname  last_name
                                    :nickname  nickname
                                    :service   "jeb"
                                    :locale    locale
                                    :email     email
                                    :gender    (str (first gender))
                                    :id        (Long/parseLong id)})
      (catch Exception e (log/error e)))
    nickname))



(defn get-position-texts-for-issue [slug]
  (let [response (api-query! (str "/" slug))]
    (log/debug response)
    (->> response :items (map (comp first :texts)))))

(defn get-issues []
  (api-query! "/issues"))

(defn get-positions [slug]
  (api-query! (format "/%s" slug)))

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

(defn sent-similarity
  "How similar is s2 to s1?
  Returns a value bewteen -Infinity and 1. Were 1 denotes equality."
  [s1 s2]
  (let [l (count s1)
        d (fuzzy-metrics/levenshtein s1 s2)]
    (if (zero? l)
      0
      (- 1 (/ d l)))))

(defn similarities [coll free-form & {:keys [key-fn] :or {key-fn identity}}]
  (map #(hash-map :entity %
                  :confidence (sent-similarity (.toLowerCase free-form)
                                               (.toLowerCase (key-fn %))))
       coll))

(defn natural-topic->nearest-topics [free-form-topic language]
  (as-> (get-issues) $
        (filter #(= language (:language %)) $)
        (similarities $ free-form-topic :key-fn :title)
        (sort-by :confidence > $)))
