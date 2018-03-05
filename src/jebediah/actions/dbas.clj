(ns jebediah.actions.dbas
  (:require [dialogflow.v2beta.core :as dialogflow :refer [defaction]]
            [dialogflow.v2beta.integrations.agent :as agent]
            [dialogflow.v2beta.integrations.facebook :as fb]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [jebediah.dbas-adapter.core :as dbas]
            [clj-fuzzy.metrics :as fuzzy-metrics]
            [clojure.data.json :as json]))

(defaction dbas.start-discussions [{{{topic :topic} :parameters} :queryResult session :session}]
  (let [issues (:issues (dbas/query "{issues{title, slug, positions{uid, text}}}"))]
    (if-let [corrected-topic (dbas/corrected-topic issues topic)]
      (let [topic-detail (first (filter #(= (:slug corrected-topic) (:slug %)) issues))
            position (rand-nth (:positions topic-detail))]
        (agent/speech (str "Ok, let us talk about " (:title corrected-topic) ". "
                           "Do you think " (:text position) "?")
                      :outputContexts [{:name          (str session "/contexts/" "topic")
                                        :parameters    corrected-topic
                                        :lifespanCount 5}
                                       {:name          (str session "/contexts/" "position")
                                        :parameters    position
                                        :lifespanCount 5}]))
      (let [suggested-title (->> issues
                                 (map :title)
                                 (sort-by #(fuzzy-metrics/levenshtein topic %)) ; TODO replace with elastic search
                                 (first))]
        (agent/speech (format "Sorry there is no such topic, but we can talk about %s" suggested-title)
                      :outputContexts [{:name          (str session "/contexts/" "Letustalkabouttopic-followup")
                                        :parameters    {:suggested-title suggested-title}
                                        :lifespanCount 2}])))))


(defaction dbas.list-discussions [request]
  (let [topics (:issues (dbas/query "query{issues{title, subtitle:info}}"))]
    (agent/speech
      (format "The topics are: %s." (str/join ", " (take 3 (map :title topics))))
      :fulfillmentMessages
      [{:platform "FACEBOOK"
        :payload  (fb/rich-list
                    (mapv #(fb/list-entry-postback-button (:title %) (:subtitle %) (format "Let us talk about %s" (:title %)))
                          (take 3 topics))
                    true)}])))


(defaction dbas.list-discussions.more [request]
  (let [more-topics (drop 3 (:issues (dbas/query "query{issues{title, subtitle:info}}")))]
    (case (count more-topics)
      0 (agent/speech "Sorry, but there are no more topics.")
      1 (agent/speech (first more-topics))
      (agent/speech
        (format "Ok, here are %d more topics: %s" (count more-topics) (str/join ", " (map :title more-topics)))
        :fulfillmentMessages
        [{:platform "FACEBOOK"
          :payload  (fb/rich-list
                      (mapv #(fb/list-entry-postback-button
                               (:title %)
                               (:subtitle %)
                               (format "Let us talk about %s" %))
                            (take 3 more-topics)))}]))))


(defaction dbas.info-discussion [{{{topic :topic} :parameters} :queryResult :as request}]
  (let [issues (:issues (dbas/query "query{issues{uid, title, slug, info}}"))]
    (if-let [corrected-topic (dbas/corrected-topic issues topic)]
      (agent/speech (str (format "Here are more informations about %s: " (:title corrected-topic))
                         (:info corrected-topic))
                    :outputContexts [{:name          (dialogflow/gen-context request "topic")
                                      :parameters    (select-keys corrected-topic [:uid :title :slug])
                                      :lifespanCount 5}])
      (agent/speech (str "Sorry there is no such topic" topic)))))


(defaction dbas.info-discussion-with-topic [{{{slug :slug} :parameters} :queryResult}]
  (let [topic (:issues (dbas/query (str "query{issue(uid:" slug "){title, slug, info}}")))]
    (agent/speech (str (format "Here are more informations about %s: " (:title topic))
                       (:info topic)))))


(defaction dbas.show-positions-with-topic [request]
  (let [topic (:parameters (dialogflow/get-context request "topic"))
        positions (map #(get-in % [:textversions :content]) (dbas/get-positions-for-issue (:slug topic)))]
    (agent/speech
      (str (format "Some positions for %s are: " (:title topic)) (str/join ", " (take 3 positions)))
      :fulfillmentMessages
      [{:platform "FACEBOOK"
        :payload  (fb/rich-list (map #(fb/list-entry {:title % :subtitle " "}) (take 3 positions)))}])))


(defaction dbas.search-for-issue [{{{topic :topic} :parameters} :queryResult :as request}]
  (let [issues (:issues (dbas/query "query{issues{uid, title, slug}}"))]
    (if-let [corrected-topic (dbas/corrected-topic issues topic)]
      (agent/speech "Yes!"
                    :outputContexts {:name          (dialogflow/gen-context request "topic")
                                     :parameters    corrected-topic
                                     :lifespanCount 5})
      (agent/speech (format "No, but you can discuss about %s."
                            (->> issues
                                 (sort-by #(fuzzy-metrics/levenshtein topic (:title %))) ; TODO replace with elastic search
                                 first :title))))))


(defaction dbas.thoughts-about-topic [{{{topic :topic} :parameters} :queryResult :as request}]
  (let [topic (dialogflow/get-context request "discussion")
        slug (get-in request [:result :parameters :discussion-topic])
        statement (first (dbas/get-positions-for-issue slug))]
    (agent/speech (format "Others think, that %s" (str statement))
                  :outputContexts {:name          (dialogflow/gen-context request "position")
                                   :parameters    {:position-full statement}
                                   :lifespanCount 3})))


(defaction dbas.opinion-about-topic [{{parameters :parameters} :queryResult :as request}]
  (let [topic (dialogflow/get-context request :topic)
        position (:parameters (dialogflow/get-context request :position))
        opinion (= (:opinion parameters) "agree")
        reason (:reason parameters)
        attacks (fn [position opinion]
                  (->> (dbas/query
                         "{statement(uid: " (:uid position) ") {
                                            text,
                                            arguments(isSupportive: " opinion ") {
                                              premisegroups {
                                                statements {
                                                  text
                                                }
                                              }
                                            }
                                          }}")

                       :statement
                       :arguments
                       (map #(->> %
                                  :premisegroups
                                  :statements
                                  (map :text)
                                  (str/join " and ")))))]

    (agent/speech (str "Another user thinks that " (rand-nth (log/spy :info (attacks position opinion))) ". "
                       "What do you think about this?"))))

