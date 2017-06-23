(ns jebediah.handler
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.logger :as logger]
            [ring.util.response :refer [response]]
            [clojure.tools.logging :as log]
            [apiai.core :as ai]
            [jebediah.hello]
            [jebediah.actions.dbas]))

(defroutes app-routes
  (GET "/" [] "Hello World")
  (POST "/" request (response (log/spy :info (ai/dispatch-action (log/spy :info (:body request))))))
  (route/not-found "Not Found"))


(def app
  (-> app-routes
      (logger/wrap-with-logger)
      (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))
      (wrap-json-response)
      (wrap-json-body {:keywords? true :pretty? true})))

