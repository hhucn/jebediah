(ns dialogflow.v2beta.integrations.agent)

(defn speech [speech & {:keys [fulfillmentMessages payload outputContexts source followupEventInput]
                        :or {fulfillmentMessages [] payload {}, outputContexts [], source "", followupEventInput {}}}]
  {:fulfillmentText     (str speech)
   :fulfillmentMessages fulfillmentMessages
   :payload             payload
   :outputContexts      outputContexts
   :source              source
   :followupEventInput  followupEventInput})
