(ns dialogflow.v2beta.core
  (:require [dialogflow.v2beta.integrations.agent :as agent]
            [clojure.spec.alpha :as s]
            [jebediah.dialogflow-spec]))

(defmulti dispatch-action
          "Executes an action"
          #(get-in % [:queryResult :action]))

(defmethod dispatch-action :default [r]
  (agent/speech (str "Action: " (get-in r [:queryResult :action]) " not implemented! Sorry.")))

(defmacro defaction
  "Creates and registers a new action. Name this action exactly like you did in api.ai!"
  [name params & body]
  `(defmethod dispatch-action ~(str name) ~params
     ~@body))

(defn gen-context [request context]
  (str (:session request) "/contexts/" (name context)))

(defn get-context [request context]
  (let [cs (get-in request [:queryResult :outputContexts])]
    (first (filter #(= (gen-context request context) (:name %)) cs))))


;;;; Specs

(s/fdef dispatch-action
        :args :jebediah.dialogflow-spec/Webhook-request
        :ret :jebediah.dialogflow-spec/Webhook-response)