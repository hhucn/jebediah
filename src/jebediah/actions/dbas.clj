(ns jebediah.actions.dbas
  (:require [apiai.core :as ai :refer [defaction]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [jebediah.dbas-adapter.core :as dbas]))

;; (ai/update-entities! "discussion-topic" (mapv (fn [v] {:value v :synonyms []}) sample-discussions))

(defaction dbas.start-discussions [request]
    (if (ai/fulfillment-empty? request) ;; this is just a test for slot filling with the webhook
        (ai/simple-speech-response "Ok lets talk about " (get-in request [:result :parameters :discussion-topic]) ".")
        (ai/get-fulfillment request)))

(defaction dbas.list-discussions [_]
  (ai/simple-speech-response "The topics are: " (str/join ", " (take 3 (map :title (dbas/get-issues)))) "."))

(defaction dbas.list-discussions.more [_]
  (let [more-topics (drop 3 (map :title (dbas/get-issues)))
        topic-count (count more-topics)]
    (ai/simple-speech-response
     (if (zero? topic-count)
       "Sorry, but there are no more topics."
       (format "Ok, here are %d more topics: %s" topic-count (str/join ", " more-topics))))))

(defaction dbas.info-discussion [request]
  (let [requested-topic (get-in request [:result :parameters :discussion-topic])
        topic (first (filter #(= (:title %) requested-topic) (dbas/get-issues)))]
    (ai/simple-speech-response
      (if (some? topic)
        (format "Here are more informations about %s: %s" (:title topic) (:info topic))
        (str "Sorry, but there is no topic: " requested-topic)))))
