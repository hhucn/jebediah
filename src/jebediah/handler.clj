(ns jebediah.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.middleware.basic-authentication :refer [wrap-basic-authentication]]
            [ring.logger :as logger]
            [ring.util.http-response :refer :all]
            [clojure.tools.logging :as log]
            [clojure.string :refer [join]]
            [dialogflow.v2beta.core :as dialogflow]
            [jebediah.hello]
            [jebediah.actions.dbas]
            [jebediah.actions.dbas-auth]))

(defonce basic-auth {:name (System/getenv "AUTH_USER")
                     :pass (System/getenv "AUTH_PASS")})

(defn authenticated? [name pass]
  (and (= name (:name basic-auth))
       (= pass (:pass basic-auth))))

(def app-routes
  (api
    (GET "/" [] (ok "<iframe style=\"position:fixed; top:0px; left:0px; bottom:0px; right:0px; width:100%; height:100%; border:none; margin:0; padding:0; overflow:hidden; z-index:999999;\" src=\"https://console.dialogflow.com/api-client/demo/embedded/jebediah\"></iframe>"))
    (POST "/" request
      :middleware [[wrap-basic-authentication authenticated?]]
      (->> (:body-params request)
           (log/spy :info)
           (dialogflow/dispatch-action)
           (log/spy :info)
           ok))))


(when-not (and (:name basic-auth) (:name basic-auth))
  (log/warn "You didn't define any authentication!"))

(log/infof "Enabled actions:\n%s" (join \newline (keys (methods dialogflow/dispatch-action))))

(def app (logger/wrap-with-logger app-routes))
