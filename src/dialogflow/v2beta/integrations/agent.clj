(ns dialogflow.v2beta.integrations.agent)

(defrecord Response [fulfillmentText fulfillmentMessages payload outputContexts source followupEventInput])

(defn speech [speech & {:keys [fulfillmentMessages payload outputContexts source followupEventInput]
                        :or   {fulfillmentMessages [] payload {}, outputContexts [], source "", followupEventInput {}}}]
  (Response. (str speech) fulfillmentMessages payload outputContexts source followupEventInput))
