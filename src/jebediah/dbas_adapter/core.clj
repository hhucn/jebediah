(ns jebediah.dbas-adapter.core
  (:require [clojure.data.json :as json]))

(def dbas-base "http://0.0.0.0:4284/api/")

(defn slurp-json [f]
  (-> f
      (slurp)
      (json/read-str :key-fn keyword)))

(defn get-issues
  "Return all issues from dbas or nil." []
  (let [response (slurp-json (str dbas-base "issues"))]
    (sort-by :uid (get-in response [:data :issues]))))

