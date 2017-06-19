(ns jebediah.hello
  (:require [apiai-clj.core :as ai :refer [defaction]]
            [clojure.data.json :as json]))

(defn slurp-json [f & opts]
  `(json/read-str (slurp ~f ~@opts) :key-fn keyword))

(defaction hello_world [_]
  (-> "https://dbas.cs.uni-duesseldorf.de/api/hello"
      (slurp-json)
      (:message)
      (ai/simple-speech-response)))

(defaction echo_name [request-body]
  (ai/simple-speech-response "Hello " (get-in request-body [:result :parameters :given-name])))

(defaction how_long [request-body]
  (ai/simple-speech-response "The word is " (count (get-in request-body [:result :parameters :word])) " characters long."))
