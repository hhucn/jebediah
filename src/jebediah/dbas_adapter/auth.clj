(ns jebediah.dbas-adapter.auth
  (:require [clojure.data.json :as json]))

(def eauth-url (or (System/getenv "EAUTH") "https://eauth.dbas.coruscant.cs.uni-duesseldorf.de"))

(defn query-for-nickname! [service app-id user-id]
  (-> (str eauth-url "/resolve-user?service=" service "&app_id=" app-id "&user_id=" user-id) ; TODO sanitize
      (slurp)
      (json/read-str)
      (get-in ["data" "nickname"])))