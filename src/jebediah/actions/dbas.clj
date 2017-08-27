(ns jebediah.actions.dbas
  (:require [apiai.core :as ai :refer [defaction]]
            [apiai.integrations.agent :as agent]
            [apiai.integrations.facebook :as fb]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [jebediah.dbas-adapter.core :as dbas]
            [clj-fuzzy.metrics :as fuzzy-metrics]))

(defn update-topics! []
  (ai/update-entity! "discussion-topic"
                     (mapv (fn [{:keys [value synonym]}] {:value value :synonyms [synonym]})
                           (:issues (dbas/query "query{issues{synonym:title, value:slug}}")))))

(defaction dbas.start-discussions [request]
  (agent/simple-speech-response "Ok, lets talk about " (get-in (log/spy :info (ai/get-contexts request)) [:discussion :parameters :discussion-topic-name])
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
                                                 (take 3 (:issues (dbas/query "query{issues{title, subtitle:info}}")))))
    (agent/simple-speech-response "The topics are: " (str/join ", "
                                                            (take 3 (map :title (:issues (dbas/query "query{issues{title}}"))))) ".")))


(defaction dbas.list-discussions.more [request]
  (let [more-topics (drop 3 (:issues (dbas/query "query{issues{title, subtitle:info}}")))
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
        topic (first (filter #(= (:slug %) requested-topic) (:issues (dbas/query "query{issues{title, slug, info}}"))))]
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
        (get-in topic [:parameters :discussion-topic-original]) " are: "
        (str/join ", " (take 3 (dbas/get-positions-for-issue (get-in topic [:parameters :discussion-topic]))))))))

(defaction dbas.start-without-topic [request]
  (agent/simple-speech-response "blabla"))

(defaction dbas.search-for-issue [request]
  (let [topic (get-in request [:result :parameters :discussion-topic])
        issues (:issues (dbas/query "query{issues{uid, title, slug}}"))]
    (if (dbas/is-issue? topic issues)
      (agent/simple-speech-response "Yes!")
      (agent/simple-speech-response "No, but you can discuss about "
                                    (->> issues
                                         (sort-by #(fuzzy-metrics/levenshtein topic (:title %)))
                                         (log/spy :info)
                                         (first)
                                         (log/spy :info)
                                         (:title)) "."))))


(defaction dbas.start-invalid-discussion [request]
  (let [topic (get-in request [:result :parameters :invalid-discussion-topic])
        issues (:issues (dbas/query "query{issues{uid, title, slug}}"))
        nearest-topic (->> issues
                           (sort-by #(fuzzy-metrics/levenshtein topic (:title %)))
                           (log/spy :info)
                           (first))]
    (assoc (agent/simple-speech-response (format "There is no issue %s. Would you like to talk about %s?" topic (:title nearest-topic)))
        :contextOut
        [{:name "letstalkaboutinvalidtopic-followup"
          :parameters {:discussion-topic (:slug nearest-topic)
                       :discussion-topic-name (:title nearest-topic)}
          :lifespan 2}])))
