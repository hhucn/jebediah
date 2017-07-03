(ns jebediah.actions.dbas
  (:require [apiai.core :as ai :refer [defaction]]
            [apiai.integrations.agent :as agent]
            [apiai.integrations.facebook :as fb]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [jebediah.dbas-adapter.core :as dbas]))

;; (ai/update-entities! "discussion-topic" (mapv (fn [v] {:value v :synonyms []}) sample-discussions))

(defaction dbas.start-discussions [request]
  (agent/simple-speech-response "Ok, lets talk about " (get-in (log/spy :info (ai/get-contexts request)) [:discussion :parameters :discussion-topic.original])
                                ". Maybe ask for some positions, while I'm developing new skills?"))

(defn fb-list-entry-button [entry]
  {:title (:title entry)
   :subtitle (:subtitle entry)
   :buttons [{:type "postback"
              :title "This!"
              :payload (str "Let us talk about " (:title entry))}]})

(defn fb-list-entry [entry]
  {:title (:title entry)
   :subtitle (:subtitle entry)})

(defaction dbas.list-discussions [request]
  (case (ai/get-service request)
    :facebook (fb/simple-list-response true (map fb-list-entry-button
                                              (take 3 (:issues (dbas/slurp-dbas "query{issues{title, subtitle:info}}")))))
    (agent/simple-speech-response "The topics are: " (str/join ", "
                                                            (take 3 (map :title (dbas/get-issues)))) ".")))


(defaction dbas.list-discussions.more [request]
  (let [more-topics (drop 3 (:issues (dbas/slurp-dbas "query{issues{title, subtitle:info}}")))
        topic-count (count more-topics)]
    (case (ai/get-service request)
      :facebook (cond
                  (>= topic-count 2) (fb/simple-list-response false (map fb-list-entry-button more-topics))
                  (= topic-count 1) (fb/text-response (first more-topics))
                  :default (fb/text-response "There are no more topics!"))
      (agent/simple-speech-response
       (if (zero? topic-count)
         "Sorry, but there are no more topics."
         (format "Ok, here are %d more topics: %s" topic-count (str/join ", " (map :title more-topics))))))))

(defaction dbas.info-discussion [request]
  (let [requested-topic (get-in request [:result :parameters :discussion-topic])
        topic (first (filter #(= (:slug %) requested-topic) (:issues (dbas/slurp-dbas "query{issues{title, slug, info}}"))))]
    (agent/simple-speech-response
      (if (some? topic)
        (format "Here are more informations about %s: %s" (:title topic) (:info topic))
        (str "Sorry, but there is no topic: " requested-topic)))))

(defaction dbas.show-positions-with-discussion [request]
  (let [topic (:discussion (ai/get-contexts request))]
    (case (ai/get-service request)
      :facebook
      (fb/simple-list-response false (map #(fb-list-entry {:title % :subtitle " "}) (take 3 (dbas/get-positions-for-issue (get-in topic [:parameters :discussion-topic])))))
      (agent/simple-speech-response
        "Some positions for "
        (get-in topic [:parameters :discussion-topic.original]) " are: "
        (str/join ", " (take 3 (dbas/get-positions-for-issue (get-in topic [:parameters :discussion-topic]))))))))