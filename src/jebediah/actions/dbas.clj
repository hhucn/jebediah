(ns jebediah.actions.dbas
  (:require [apiai.core :as ai :refer [defaction]]
            [apiai.integrations.agent :as agent]
            [apiai.integrations.facebook :as fb]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [jebediah.dbas-adapter.core :as dbas]
            [clj-fuzzy.metrics :as fuzzy-metrics]
            [clojure.data.json :as json]))

(defn update-topics! []
  (ai/update-entity! "discussion-topic"
                     (mapv (fn [{:keys [value synonym]}] {:value value :synonyms [synonym]})
                           (:issues (dbas/query "query{issues{synonym:title, value:slug}}")))))

(defaction dbas.start-discussions [{{{topic :topic} :parameters} :result}]
  (let [issues (:issues (dbas/query "query{issues{uid, title, slug}}"))]
    (if-let [corrected-topic (dbas/corrected-topic issues topic)]
      (agent/speech (str "Ok, lets talk about " (:title corrected-topic) ". What do you think about it?")
                    :contextOut [{:name       "topic"
                                  :parameters corrected-topic
                                  :lifespan   5}])
      (let [suggested-title (->> issues
                                 (sort-by #(fuzzy-metrics/levenshtein topic (:title %))) ; TODO replace with elastic search
                                 (first)
                                 (:title))]
        (agent/speech (format "Sorry there is no such topic, but we can talk about %s" suggested-title)
                      :contextOut [{:name "Letustalkabouttopic-followup"
                                    :parameters {:suggested-title suggested-title}
                                    :lifespan 2}])))))


(defn fb-list-entry-button [entry]
  {:title (:title entry)
   :subtitle (:subtitle entry)
   :buttons [{:type "postback"
              :title "This!"
              :payload (format "Let us talk about %s" (:title entry))}]})

(defn fb-list-entry [entry]
  {:title (:title entry)
   :subtitle (:subtitle entry)})

(defaction dbas.list-discussions [request]
  (let [topics (:issues (dbas/query "query{issues{title, subtitle:info}}"))]
    (case (ai/get-service request)
      :facebook (fb/simple-list-response true (map fb-list-entry-button (take 3 topics)))
      (agent/speech (format "The topics are: %s." (str/join ", " (take 3 (map :title topics))))))))


(defaction dbas.list-discussions.more [request]
  (let [more-topics (drop 3 (:issues (dbas/query "query{issues{title, subtitle:info}}")))
        topic-count (count more-topics)]
    (case (ai/get-service request)
      :facebook (cond
                  (>= topic-count 2) (fb/simple-list-response false (map fb-list-entry-button more-topics))
                  (= topic-count 1) (fb/text-response (first more-topics))
                  :default (fb/text-response "There are no more topics!"))
      (agent/speech
       (if (zero? topic-count)
         "Sorry, but there are no more topics."
         (format "Ok, here are %d more topics: %s" topic-count (str/join ", " (map :title more-topics))))))))


(defaction dbas.info-discussion [{{{topic :topic} :parameters} :result}]
  (let [issues (:issues (dbas/query "query{issues{uid, title, slug, info}}"))]
    (if-let [corrected-topic (dbas/corrected-topic issues topic)]
      (agent/speech (format "Here are more informations about %s: %s" (:title corrected-topic) (:info corrected-topic))
                    :contextOut [{:name       "topic"
                                  :parameters (select-keys corrected-topic [:uid :title :slug])
                                  :lifespan   5}])
      (agent/speech (format "Sorry there is no such topic" topic)))))

(defaction dbas.info-discussion-with-topic [{{{slug :slug} :parameters} :result}]
  (let [topic (log/spy :info (:issues (dbas/query (str "query{issue(uid:" slug "){title, slug, info}}"))))]
    (agent/speech (format "Here are more informations about %s: %s" (:title topic) (:info topic)))))


; I - Context: topic
; O - Context: topic
(defaction dbas.show-positions-with-topic
  [request]
  (let [topic (:parameters (:topic (ai/get-contexts request)))
        positions (map #(get-in % [:textversions :content]) (dbas/get-positions-for-issue (:slug topic)))]
    (case (ai/get-service request)
      :facebook (fb/simple-list-response false (map #(fb-list-entry {:title % :subtitle " "}) (take 3 positions)))
      (agent/speech (str "Some positions for " (:title topic) " are: " (str/join ", " (take 3 positions)))))))


(defaction dbas.search-for-issue [{{{topic :discussion-topic} :parameters} :result}]
  (let [issues (:issues (dbas/query "query{issues{uid, title, slug}}"))]
    (if-let [corrected-topic (dbas/corrected-topic issues topic)]
      (agent/speech "Yes!" :contextOut {:name       "topic"
                                        :parameters corrected-topic
                                        :lifespan   5})
      (agent/speech (format "No, but you can discuss about %s."
                            (->> issues
                              (sort-by #(fuzzy-metrics/levenshtein topic (:title %))) ; TODO replace with elastic search
                              (first)
                              (:title)))))))


(defaction dbas.thoughts-about-topic [request]
  (let [topic (:discussion (ai/get-contexts request))
        slug (get-in request [:result :parameters :discussion-topic])
        statement (first (dbas/get-positions-for-issue slug))]
    (agent/speech (format "Others think, that %s" (str statement))
                  :contextOut {:name "position"
                               :parameters {:position-full statement}
                               :lifespan   3})))


(defaction dbas.opinion-about-topic [request]
    (let [topic (:discussion (ai/get-contexts request))
          slug (get-in topic [:parameters :discussion-topic])
          position (get-in topic [:parameters :position])
          reason (get-in topic [:parameters :reason])
          positions (set (dbas/get-positions-for-issue (log/spy :info slug)))]
      (if (contains? positions position) ; TODO check if contains position but not with the reason
        (agent/simple-speech-response "cool!")
        (do
          (log/info slug position reason)
          (dbas/add-position slug position reason)))))
