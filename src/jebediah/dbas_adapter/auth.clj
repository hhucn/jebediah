(ns jebediah.dbas-adapter.auth
  (:require [clojure.data.json :as json]
            [clj-http.client :as client]
            [taoensso.timbre :as log]
            [jebediah.config :refer [eauth-url]]))

(defn query-for-nickname! [service app-id user-id]
  (-> (str eauth-url "/resolve-user?service=" service "&app_id=" app-id "&user_id=" user-id) ; TODO sanitize
      (slurp)
      (json/read-str)
      (get-in ["data" "nickname"])))

(defn add-eauth-user! [service app-id user-id nickname]
  (log/debug "Add:" service app-id user-id nickname)
  (log/spy :debug (client/post (str eauth-url "/add-user")
                               {:as           :auto
                                :content-type :json
                                :body         (json/write-str {:service service
                                                               :app_id app-id
                                                               :user_id user-id
                                                               :nickname nickname})})))