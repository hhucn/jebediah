(ns jebediah.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.logger :as logger]
            [ring.util.response :refer [response]]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [jebediah.apiaiapi :as ai]
            [jebediah.hello :refer :all]))

(defroutes app-routes
  (GET "/" [] "Hello World")
  (POST "/" request (response (ai/dispatch-action (log/spy :info (:body request)))))
  (route/not-found "Not Found"))


(def app
  (-> app-routes
      (logger/wrap-with-logger)
      (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))
      (wrap-json-response)
      (wrap-json-body {:keywords? true :pretty? true})))

