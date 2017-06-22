(ns jebediah.actions.dbas
  (:require [apiai.core :as ai :refer [defaction]]
            [clojure.string :as str]))

(def sample-discussions ["Cats or dogs"
                         "Computer science renewal"
                         "Town has to cut spending"
                         "What to eat"])

(ai/update-entities! "discussion-topic" (mapv (fn [v] {:value v :synonyms []}) sample-discussions))

(defaction dbas.start-discussions [request]
    (if (ai/fulfillment-empty? request)
        (ai/simple-speech-response "Ok lets talk about " (get-in request [:result :parameters :discussion-topic]) ".")
        (ai/get-fulfillment request)))

(defaction dbas.list-discussions [_]
  (ai/simple-speech-response "The topics are: " (str/join ", " (take 3 sample-discussions)) "."))

(defaction dbas.list-discussions.more [_]
  (let [more-topics (drop 3 sample-discussions)
        topic-count (count more-topics)]
    (ai/simple-speech-response
     (if (zero? topic-count)
       "Sorry, but there are no more topics."
       (format "Ok, here are %d more topics: %s" topic-count (str/join ", " more-topics))))))
