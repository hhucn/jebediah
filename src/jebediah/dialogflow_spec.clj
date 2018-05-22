(ns jebediah.dialogflow-spec
  (:require [clojure.spec.alpha :as s]))


(s/def ::session string?)
(s/def ::responseId string?)


(s/def ::name string?)
(s/def ::lifespanCount #(<= 0 %))
(s/def ::parameters (s/map-of keyword? string?))
(s/def ::Context (s/keys :req [::name]
                         :opt [::lifespanCount ::parameters]))

(s/def ::queryText string?)
(s/def ::languageCode #{"en" "en-GB" "en-AU" "en-CA" "en-IN" "en-US"
                        "de"})
(s/def ::speechRecognitionConfidence (s/and double? #(<= 0.0 % 1.0)))
(s/def ::action string?)
(s/def ::allRequiredParamsPresent boolean?)
(s/def ::fulfillmentText string?)
(s/def ::fulfillmentMessages (s/coll-of map?))              ;; TODO Message
(s/def ::webhookSource string?)
(s/def ::webhookPayload map?)
(s/def ::outputContexts (s/coll-of ::context))
(s/def ::intent map?)                                       ;; TODO  Intent
(s/def ::intentDetectionConfidence (s/and double? #(<= 0.0 % 1.0)))
(s/def ::diagnosticInfo map?)
(s/def ::queryResult (s/keys :req [::queryText
                                   ::languageCode
                                   ::speechRecognitionConfidence
                                   ::action
                                   ::parameters
                                   ::allRequiredParamsPresent
                                   ::fulfillmentText
                                   ::fulfillmentMessages
                                   ::webhookSource
                                   ::webhookPayload
                                   ::outputContexts
                                   ::intent
                                   ::intentDetectionConfidence
                                   ::diagnosticInfo]))


(s/def ::Webhook-request (s/keys :req [::session ::responseId ::queryResult])) ;; Optional originaRequestIntent


(s/def ::EventInput (s/keys :req [::name ::languageCode]
                            :opt [::parameters]))

(s/def ::source ::webhookSource)
(s/def ::payload ::webhookPayload)
(s/def ::followupEventInput ::EventInput)
(s/def ::Webhook-response (s/keys :opt [::fulfillmentText
                                        ::fulfillmentMessages
                                        ::source
                                        ::payload
                                        ::outputContexts
                                        ::followupEventInput]))