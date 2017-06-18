(ns jebediah.apiaiapi
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]))

(def apiai-base "https://api.api.ai/v1")
(def dev-token (System/getenv "API_AI_DEVTOKEN"))

(defn simple-speech-response
  "Returns a valid api.ai map with 'speech' and 'displayText' set to speech."
  [& speech]
  {:speech (apply str speech)
   :displayText (apply str speech)
   :data []
   :contextOut []
   :source ""})

(defn get-fulfillment
  "Gets the fulfillment part of the api.ai request or nil."
  [request] (get-in request [:result :fulfillment]))

(defn fulfillment-empty? [request]
  "True if speech is empty or there is no fulfillment"
  (= "" (get-in request [:result :fulfillment :speech] "")))

(defmulti dispatch-action
          "Executes an action"
          #(get-in % [:result :action]))

(defmethod dispatch-action :default [r] (if (fulfillment-empty? r)
                                          (simple-speech-response "Action: " (get-in r [:result :action]) " not implemented! Sorry.")
                                          (get-fulfillment r)))

(defmacro defaction
  "Creates and registers a new action. Name this action exactly like you did in api.ai!"
  [name params & body]
  `(defmethod dispatch-action ~(str name) ~params
     ~@body))

(defn update-entities! [name entries]
  "Updates the entities"
  (client/put (str apiai-base "/entities")
            {:headers {:Authorization [(str "Bearer" dev-token)]}
             :content-type :json
             :body (json/write-str [{:name name :entries entries}])}))
