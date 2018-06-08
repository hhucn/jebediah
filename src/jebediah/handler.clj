(ns jebediah.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.middleware.basic-authentication :refer [wrap-basic-authentication]]
            [ring.logger :as logger]
            [ring.util.http-response :refer :all]
            [clojure.tools.logging :as log]
            [clojure.string :refer [join]]
            [dialogflow.v2beta.core :as dialogflow]
            [jebediah.dbas-adapter.auth :as auth]
            [jebediah.hello]
            [jebediah.actions.dbas]
            [jebediah.actions.dbas-auth]))

(defonce basic-auth {:name (System/getenv "AUTH_USER")
                     :pass (System/getenv "AUTH_PASS")})

(defn authenticated? [name pass]
  (if (and (:name basic-auth) (:pass basic-auth))
    (and (= name (:name basic-auth))
         (= pass (:pass basic-auth)))
    true))

(defmulti authenticate-user #(get-in (log/spy :info %) [:originalDetectIntentRequest :source]))
(defmethod authenticate-user :default [_] nil)
(defmethod authenticate-user "facebook" [{{{{page-id :id}  :recipient
                                            {user-id :id} :sender} :payload}
                                          :originalDetectIntentRequest}]
  (when-let [nickname (auth/query-for-nickname "facebook" page-id user-id)]
   {:service "facebook"
    :nickname nickname
    :user-id user-id
    :page-id page-id}))


(defn wrap-with-user-resolving [handler]
  (fn [request]
    (if (dialogflow/get-context (:body-params request) "user")
      (handler request)
      (if-let [auth-user (authenticate-user (:body-params request))]
        (let [user-context (dialogflow/context (:body-params request) "user" auth-user 20)
              new-request (update-in request [:body-params :queryResult :outputContexts] conj user-context)]
          (update (handler new-request) :outputContexts conj user-context))
        (handler request)))))


(def app-routes
  (api
    (GET "/" [] (ok "<iframe style=\"position:fixed; top:0px; left:0px; bottom:0px; right:0px; width:100%; height:100%; border:none; margin:0; padding:0; overflow:hidden; z-index:999999;\" src=\"https://console.dialogflow.com/api-client/demo/embedded/jebediah\"></iframe>"))
    (POST "/" request
      :middleware [[wrap-basic-authentication authenticated?]
                   [wrap-with-user-resolving]]
      (->> (:body-params request)
           (log/spy :info)
           (dialogflow/dispatch-action)
           (log/spy :info)
           ok))))


(when-not (and (:name basic-auth) (:name basic-auth))
  (log/warn "You didn't define any authentication!"))

(log/infof "Enabled actions:\n%s" (join \newline (keys (methods dialogflow/dispatch-action))))

(def app (logger/wrap-with-logger app-routes))
