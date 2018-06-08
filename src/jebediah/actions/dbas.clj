(ns jebediah.actions.dbas
  (:require [dialogflow.v2beta.core :as dialogflow :refer [defaction]]
            [dialogflow.v2beta.integrations.agent :as agent]
            [dialogflow.v2beta.integrations.facebook :as fb]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [jebediah.dbas-adapter.core :as dbas]
            [clj-fuzzy.metrics :as fuzzy-metrics]))

(def strings {:talk-about               "Ok, let us talk about %s. Do you think %s?"
              :no-topic-but             "Sorry, there is no such topic, but we could talk about %s."
              :no-topic                 "Sorry, there is no such topic."
              :list-topics              "The topics are: %s."
              :no-more-topics           "Sorry, but there are no more topics."
              :n-more-topics            "Ok, here are %d more topics: %s."
              :search-topic-yes         "Yes!"
              :search-topic-no          "No, but you can discuss about %s."
              :more-informations        "Here are more informations about %s: %s"
              :position-list            "Some positions for %s are: %s"
              :others-think             "Others think, that %s."
              :another-thinks           "Another user thinks that %s. What do you think about this?"
              :button-let-us-talk-about "Let us talk about %s"
              :first-one-in-topic       "You are actually the first one in this topic and I'm can't handle this.. yet"
              :conjunction              "and"})

(defaction dbas.start-discussions
  :parameters [topic]
  :request request
  :language lang
  (let [{confidence    :confidence
         nearest-topic :topic} (first (dbas/natural-topic->nearest-topics topic lang))]
    (if (> confidence 0.9)
      (let [positions (:items (dbas/get-positions (:slug nearest-topic)))]
        (if (pos? (count positions))
          (let [position (rand-nth positions)]
            (agent/speech (format (strings :talk-about) (:title nearest-topic) (str/join (str \space (strings :conjunction) \space) (:texts position)))
                          :outputContexts [(dialogflow/context request "topic" nearest-topic 5)
                                           (dialogflow/context request "position" position 5)]))
          (agent/speech (strings :first-one-in-topic))))
      (agent/speech (format (strings :no-topic-but) (:title nearest-topic))
                    :outputContexts [(dialogflow/context request "suggested-topic" {:suggested-title (:title nearest-topic)} 2)
                                     (dialogflow/reset-context request "topic")
                                     (dialogflow/reset-context request "position")]))))


(defaction dbas.list-discussions
  :language lang
  (let [topics-to-show 3
        topics (filter #(= lang (:language %)) (dbas/api-query "/issues"))]
    (agent/speech
      (format (strings :list-topics) (str/join ", " (take topics-to-show (map :title topics))))
      :fulfillmentMessages
      [{:platform "FACEBOOK"
        :payload  (fb/rich-list
                    (mapv #(fb/list-entry-postback-button (:title %) (:summary %) (format (strings :button-let-us-talk-about) (:title %)))
                          (take topics-to-show topics))
                    (> (count topics) topics-to-show))}])))


(defaction dbas.list-discussions.more
  :language lang
  (let [more-topics (->> (dbas/api-query "/issues")
                         (filter #(= lang (:language %)))
                         (drop 3))]
    (case (count more-topics)
      0 (agent/speech (strings :no-more-topics))
      1 (agent/speech (first more-topics))
      (agent/speech
        (format (strings :n-more-topics) (count more-topics) (str/join ", " (map :title more-topics)))
        :fulfillmentMessages
        [{:platform "FACEBOOK"
          :payload  (fb/rich-list
                      (mapv #(fb/list-entry-postback-button
                               (:title %)
                               (:summary %)
                               (format (strings :button-let-us-talk-about) %))
                            (take 3 more-topics)))}]))))


(defaction dbas.info-discussion
  :request request
  :parameters [topic]
  :language lang
  (let [issues (:issues (dbas/query "query{issues{uid, title, slug, info}}"))]
    (if-let [corrected-topic (dbas/corrected-topic issues topic)]
      (agent/speech (str (format (strings :more-informations) (:title corrected-topic))
                         (:info corrected-topic))
                    :outputContexts [(dialogflow/context request "topic" (select-keys corrected-topic [:uid :title :slug]) 5)])
      (agent/speech (strings :no-topic)))))


(defaction dbas.info-discussion-with-topic
  :parameters [slug]
  (let [topic (:issues (dbas/query (str "query{issue(uid:" slug "){title, slug, info}}")))]
    (agent/speech (str (format (strings :more-informations) (:title topic))
                       (:info topic)))))


(defaction dbas.show-positions-with-topic
  :contexts [topic]
  (let [positions (map #(get-in % [:textversions :content]) (dbas/get-positions-for-issue (:slug topic)))]
    (agent/speech
      (format (strings :position-list) (:title topic) (str/join ", " (take 3 positions)))
      :fulfillmentMessages
      [{:platform "FACEBOOK"
        :payload  (fb/rich-list (map #(fb/list-entry {:title % :subtitle " "}) (take 3 positions)))}])))


(defaction dbas.search-for-issue
  :contexts [topic]
  :request request
  (let [issues (:issues (dbas/query "query{issues{uid, title, slug}}"))]
    (if-let [corrected-topic (dbas/corrected-topic issues topic)]
      (agent/speech (strings :search-topic-yes) :outputContexts [(dialogflow/context request "topic" corrected-topic 5)])
      (agent/speech (format (strings :search-topic-no)
                            (->> issues
                                 (sort-by #(fuzzy-metrics/levenshtein topic (:title %))) ; TODO replace with elastic search
                                 first :title))))))


(defaction reset
  :request request
  (agent/speech "Ok, let us start anew."
                :outputContexts (dialogflow/reset-all-contexts request)))


(defaction dbas.opinion-about-topic
  :contexts [user position]
  :parameters [opinion reason]
  (let [nickname (get user :nickname "anonymous")
        justification-url (->> position :url
                               #(dbas/api-query % nickname) :attitudes
                               #(% (keyword opinion)) :url)
        justifications (:items (dbas/api-query justification-url nickname))
        nearest (->> justifications
                     (remove #(= (:url %) "add"))
                     (map #(hash-map :statement %
                                     :confidence (dbas/sent-similarity (str/join (str \space (strings :conjunction) \space) (:texts %)) reason)))
                     (first))
        new-statement? (> (:confidence nearest) 0.90)]      ;; do something clever here

    (if new-statement?
      (let [answer (-> (dbas/api-post justification-url nickname {:reason reason}) :bubbles last :text)]
        (agent/speech answer))
      (let [answer (-> (dbas/api-query (log/spy :info (get-in nearest [:statement :url])) nickname) :bubbles last :text)]
        (agent/speech answer)))))