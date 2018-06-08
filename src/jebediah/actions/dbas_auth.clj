(ns jebediah.actions.dbas-auth
  (:require [dialogflow.v2beta.core :refer [defaction]]
            [dialogflow.v2beta.integrations.agent :as agent]
            [jebediah.dbas-adapter.auth :as auth]))


(defaction dbas-auth.logged-in [{{service :source
                                  {{app-id :id}  :recipient
                                   {user-id :id} :sender} :payload}
                                 :originalDetectIntentRequest}]
  (agent/speech
    (if (= service "facebook")
      (if-let [nickname (auth/query-for-nickname service app-id user-id)]
        (str "Yes. You are logged in as " nickname \.)
        "No, you are not logged in.")
      "By now, you can only link your D-BAS Account in Facebook!")))


