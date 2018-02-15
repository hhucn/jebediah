(ns jebediah.actions.dbas-auth
  (:require [apiai.core :as ai :refer [defaction]]
            [apiai.integrations.agent :as agent]
            [apiai.integrations.facebook :as fb]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [jebediah.dbas-adapter.core :as dbas]
            [clj-fuzzy.metrics :as fuzzy-metrics]
            [clojure.data.json :as json]
            [clj-http.client :as client]))


(def eauth-url (or (System/getenv "EAUTH") "https://eauth.coruscant.cs.uni-duesseldorf.de"))

(defn query-for-nickname [service app-id user-id]
  (-> (str eauth-url "/resolve-user?service=" service "&app_id=" app-id "&user_id=" user-id)
      (slurp)
      (json/read-str)
      (get-in ["data" "nickname"])))


(defaction dbas-auth.logged-in [{{service :source
                                  {{app-id :id } :recipient
                                   {user-id :id} :sender}
                                  :data}
                                 :originalRequest :as request}]
  (agent/speech
    (if (= service "facebook")
      (if-let [nickname (log/spy :info (query-for-nickname service app-id user-id))]
        (str "Yes. You are logged in as " nickname \.)
        "No, you are not logged in.")
      "By now, you can only link your D-BAS Account in Facebook!")))


