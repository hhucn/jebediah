(ns jebediah.hello
  (:require [jebediah.apiaiapi :refer :all]
            [clojure.data.json :as json]))

(defmacro slurp-json [f & opts]
  `(json/read-str (slurp ~f ~@opts) :key-fn keyword))

(defaction hello_world [_] (-> "https://dbas.cs.uni-duesseldorf.de/api/hello"
                               (slurp-json)
                               (:message)
                               (simple-apiai-response)))

(defaction echo_name [request-body] (simple-apiai-response (str "Hello " (get-in request-body [:result :parameters :given-name]))))