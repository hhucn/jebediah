(ns jebediah.handler
  (:require [compojure.api.sweet :refer :all]
            [clojure.pprint :refer [pprint]]
            [ring.middleware.basic-authentication :refer [wrap-basic-authentication]]
            [ring.logger :as logger]
            [ring.util.http-response :refer :all]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as appenders]
            [clojure.string :refer [join]]
            [clojure.data.json :as json]
            [dialogflow.v2beta.core :as dialogflow]
            [jebediah.dbas-adapter.auth :as auth]
            [jebediah.dbas-adapter.core :as dbas]
            [jebediah.actions.dbas]
            [jebediah.actions.dbas-auth]
            [jebediah.config :refer [jebediah-test-page-access-token]]
            [clj-http.client :as client]))



(log/merge-config!
  {:level      :debug
   :middleware [(fn [data] (update data :vargs (partial mapv #(if (string? %) % (with-out-str (pprint %))))))]
   :appenders  {:spit (appenders/spit-appender {:fname "./jeb.log"})}})

(defonce basic-auth {:name (System/getenv "AUTH_USER")
                     :pass (System/getenv "AUTH_PASS")})

(defn authenticated? [name pass]
  (if (and (:name basic-auth) (:pass basic-auth))
    (and (= name (:name basic-auth))
         (= pass (:pass basic-auth)))
    true))

(defn map-keys [f m]
  (into {}
        (for [[k v] m]
          [(f k) v])))

(defn resolve-fb-user [fb-user-id]
  (map-keys keyword (json/read-str (:body (client/get (format "https://graph.facebook.com/v3.0/%s?fields=first_name,last_name,id,gender,locale&access_token=%s" fb-user-id jebediah-test-page-access-token)
                                                      {:as :auto})))))

(defmulti authenticate-user #(get-in % [:originalDetectIntentRequest :source]))
(defmethod authenticate-user :default [_] nil)
(defmethod authenticate-user "facebook" [{{{{{page-id :id} :recipient
                                             {user-id :id} :sender} :data} :payload}
                                          :originalDetectIntentRequest :as request}]
  {:service  "facebook"
   :nickname (if-let [nickname (auth/query-for-nickname! "facebook" page-id user-id)]
               nickname
               (let [fb-data (resolve-fb-user user-id)
                     nickname (dbas/create-dbas-oauth-account! fb-data)]
                 (future (auth/add-eauth-user! "facebook" page-id user-id nickname))
                 nickname))
   :user-id  user-id
   :page-id  page-id})


(defn wrap-with-user-resolving [handler]
  "Wraps the handler with user resolving.
   Tries to resolve the user against the eauth backend.
   If it succeeds, the inforamtions in eauth are stored in the user context.

   Skips if the user context is already available"
  (fn [request]
    (if (dialogflow/get-context (:body-params request) "user")
      (handler request)
      (if-let [auth-user (authenticate-user (log/spy :debug (:body-params request)))]
        (let [user-context (dialogflow/context (:body-params request) "user" auth-user 20)]
          (-> request
              (update-in [:body-params :queryResult :outputContexts] conj user-context)
              handler
              (update-in [:body :outputContexts] conj user-context)))
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
           (#(do (log/spy :info (into {} %)) %))
           ok))))


(when-not (and (:name basic-auth) (:name basic-auth))
  (log/warn "You didn't define any authentication!"))

(log/infof "Enabled actions:\n%s" (join \newline (keys (methods dialogflow/dispatch-action))))

(def app (logger/wrap-with-logger app-routes))
