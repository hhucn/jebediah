(ns jebediah.hello
  (:require [apiai.core :as ai :refer [defaction]]
            [apiai.integrations.agent :as agent]
            [clojure.data.json :as json]))

(defn slurp-json [f]
  (json/read-str (slurp f) :key-fn keyword))

(defaction hello_world [_]
  (-> (slurp-json "https://dbas.cs.uni-duesseldorf.de/api/hello")
      (:message)
      (agent/simple-speech-response)))

(defaction echo_name [request-body]
  (agent/simple-speech-response "Hello " (get-in request-body [:result :parameters :given-name])))

(defaction how_long [request-body]
  (agent/simple-speech-response "The word is " (count (get-in request-body [:result :parameters :word])) " characters long."))