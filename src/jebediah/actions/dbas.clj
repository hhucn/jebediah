(ns jebediah.actions.dbas
  (:require [dialogflow.v2beta.core :as dialogflow :refer [defaction]]
            [dialogflow.v2beta.integrations.agent :as agent]
            [dialogflow.v2beta.integrations.facebook :as fb]
            [clojure.string :as str]
            [taoensso.timbre :as log]
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

(defn- get-nickname [request]
  (get (:parameters (dialogflow/get-context request :user)) :nickname))

(defaction dbas.start-discussions [{{{natural-topic :topic} :parameters lang :languageCode} :queryResult :as request}]
  (let [{confidence :confidence
         topic      :topic} (first (dbas/natural-topic->nearest-topics natural-topic lang))]
    (if (> confidence 0.9)
      (let [positions (:items (dbas/get-positions (:slug topic)))]
        (if (pos? (count positions))
          (let [position (rand-nth positions)
                text (format (strings :talk-about) (:title topic) (str/join (str \space (strings :conjunction) \space) (:texts position)))]
            (agent/speech text
                          :outputContexts [(dialogflow/context request "topic" topic 5)
                                           (dialogflow/context request "position" position 5)]
                          :fulfillmentMessages
                          [(fb/response-with-quick-replies (fb/text text) (fb/quick-replies "Yes" "No"))])) ; "I don't know"))]))
          (agent/speech (strings :first-one-in-topic))))
      (agent/speech (format (strings :no-topic-but) (:title topic))
                    :outputContexts [(dialogflow/context request "suggested-topic" {:suggested-title (:title topic)} 2)
                                     (dialogflow/reset-context request "topic")
                                     (dialogflow/reset-context request "position")]))))


(defaction dbas.list-discussions [{{lang :languageCode} :queryResult}]
  (let [topics-to-show 3
        topics (filter #(= lang (:language %)) (dbas/api-query! "/issues"))]
    (agent/speech
      (format (strings :list-topics) (str/join ", " (take topics-to-show (map :title topics))))
      :fulfillmentMessages
      [(fb/response (fb/rich-list
                      (mapv #(fb/list-entry-postback-button (:title %) (:summary %) (format (strings :button-let-us-talk-about) (:title %)))
                            (take topics-to-show topics))
                      (> (count topics) topics-to-show)))])))


(defaction dbas.list-discussions.more [{{lang :languageCode} :queryResult}]
  (let [more-topics (->> (dbas/api-query! "/issues")
                         (filter #(= lang (:language %)))
                         (drop 3))]
    (case (count more-topics)
      0 (agent/speech (strings :no-more-topics))
      1 (agent/speech (first more-topics))
      (agent/speech
        (format (strings :n-more-topics) (count more-topics) (str/join ", " (map :title more-topics)))
        :fulfillmentMessages
        [(fb/response (fb/rich-list
                        (mapv #(fb/list-entry-postback-button
                                 (:title %)
                                 (:summary %)
                                 (format (strings :button-let-us-talk-about) %))
                              (take 3 more-topics))))]))))


(defaction dbas.info-discussion [{{{topic :topic} :parameters lang :languageCode} :queryResult :as request}]
  (let [issues (:issues (dbas/query "query{issues{uid, title, slug, info}}"))]
    (if-let [corrected-topic (dbas/corrected-topic issues topic)]
      (agent/speech (str (format (strings :more-informations) (:title corrected-topic))
                         (:info corrected-topic))
                    :outputContexts [(dialogflow/context request "topic" (select-keys corrected-topic [:uid :title :slug]) 5)])
      (agent/speech (strings :no-topic)))))


(defaction dbas.info-discussion-with-topic [{{{slug :slug} :parameters} :queryResult}]
  (let [topic (:issues (dbas/query (str "query{issue(uid:" slug "){title, slug, info}}")))]
    (agent/speech (str (format (strings :more-informations) (:title topic))
                       (:info topic)))))


(defaction dbas.show-positions-with-topic [request]
  (let [topic (:parameters (dialogflow/get-context request "topic"))
        positions (map #(get-in % [:textversions :content]) (dbas/get-positions-for-issue (:slug topic)))]
    (agent/speech
      (format (strings :position-list) (:title topic) (str/join ", " (take 3 positions)))
      :fulfillmentMessages
      [(fb/response (fb/rich-list (map #(fb/list-entry {:title % :subtitle " "}) (take 3 positions))))])))


(defaction dbas.search-for-issue [{{{topic :topic} :parameters} :queryResult :as request}]
  (let [issues (:issues (dbas/query "query{issues{uid, title, slug}}"))]
    (if-let [corrected-topic (dbas/corrected-topic issues topic)]
      (agent/speech (strings :search-topic-yes) :outputContexts [(dialogflow/context request "topic" corrected-topic 5)])
      (agent/speech (format (strings :search-topic-no)
                            (->> issues
                                 (sort-by #(fuzzy-metrics/levenshtein topic (:title %))) ; TODO replace with elastic search
                                 first :title))))))


; TODO
(defaction dbas.thoughts-about-topic [{{{topic :topic} :parameters} :queryResult :as request}]
  (let [slug (get-in request [:result :parameters :discussion-topic])
        statement (first (dbas/get-positions-for-issue slug))]
    (agent/speech (format (strings :others-think) (str statement))
                  :outputContexts [(dialogflow/context request :position {:position-full statement} 3)])))

(defaction reset [request]
  (agent/speech "Ok, let us start anew."
                :outputContexts (dialogflow/reset-all-contexts request)))

(defn- calculate-similaritys [justifications reason]
  (map #(hash-map
          :statement %
          :confidence (dbas/sent-similarity (str/join (str \space (strings :conjunction) \space) (:texts %)) reason))
       justifications))

(defn- get-choices [{items :items}]
  (->> items
       (map #(select-keys % [:url :texts]))
       (remove #(#{"add" "login"} (:url %)))))


(defaction dbas.opinion-about-topic [{{{:keys [opinion reason]} :parameters lang :languageCode} :queryResult :as request}]
  (let [nickname (get-nickname request)
        justification-url (->> (dialogflow/get-context request :position)
                               :parameters :url
                               (#(dbas/api-query! % nickname)) :attitudes
                               (#(% (keyword opinion))) :url)
        justification-data (dbas/api-query! justification-url nickname)]
    (agent/speech "bla" :followupEventInput {:name "justification-event" :parameters {:reason reason} :languageCode lang}
                  :outputContexts [(dialogflow/context request :justification-step {:add justification-url
                                                                                    :justifications
                                                                                         (get-choices justification-data)} 3)])))
(defn- finish-url? [url]
  (some? (re-find #"^\/\w+[\w|-]*\/(finish)" url)))

(defaction dbas.justify [{{{:keys [justification]} :parameters} :queryResult :as request}]
  (let [nickname (get-nickname request)
        {:keys [justifications add]} (:parameters (dialogflow/get-context request :justification-step))
        nearest (if (empty? justifications)
                  {:confidence 0}                           ;; no justificaitons -> no problemo
                  (->> justification
                       (calculate-similaritys justifications) ;; do something clever here
                       (sort-by :confidence >)
                       first))
        new-statement? (< (:confidence nearest) 0.90)]

    (let [reaction-data (if (and new-statement? nickname)   ;; TODO add some logic to log in
                          (do
                            (log/info "New statement:" (log/color-str :yellow justification))
                            (dbas/api-post! add nickname {:reason justification}))
                          (do
                            (log/info "Matched statement:" (log/color-str :yellow justification) "as" nearest "with a confidence of" (:confidence nearest))
                            (dbas/api-query! (get-in nearest [:statement :url]) nickname)))
          answer (-> reaction-data :bubbles last :text)]
      (agent/speech answer
                    :outputContexts [(dialogflow/context request :reaction-step
                                                         (into {} (for [[k v] (:attacks reaction-data)] [k (:url v)]))
                                                         3)]
                    :fulfillmentMessages [(fb/response-with-quick-replies (fb/text answer)
                                                                          (fb/quick-replies "This convinced me" ; support
                                                                                            "Right, but..." ; rebut
                                                                                            "Thats not the point" ; undercut
                                                                                            "I have a counter"))])))) ; undermine
; "I don't know"))]))))


(defaction dbas.reaction [{{{reaction :reaction} :parameters} :queryResult :as request}]
  (let [nickname (get-nickname request)
        url ((keyword reaction) (:parameters (dialogflow/get-context request :reaction-step)))
        justification-data (dbas/api-query! url nickname)]
    (agent/speech (->> (dbas/api-query! url nickname) (log/spy :debug) :bubbles last :text)
                  :outputContexts
                  [(dialogflow/context request
                                       :justification-step
                                       {:add            url
                                        :justifications (get-choices justification-data)} 3)])))
