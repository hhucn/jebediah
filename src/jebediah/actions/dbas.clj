(ns jebediah.actions.dbas
  (:require [jebediah.apiaiapi :refer :all]
            [clojure.string :as str]))

(def sample-discussions ["Cats or dogs"
                         "Computer science renewal"
                         "Town has to cut spending"
                         "What to eat"])

(update-entities! "discussion-topic" (mapv (fn [v] {:value v :synonyms []}) sample-discussions))

(defaction dbas.start-discussions [request]
    (if (fulfillment-empty? request)
        (simple-speech-response "Ok lets talk about " (get-in request [:result :parameters :discussion-topic]) ".")
        (get-fulfillment request)))

(defaction dbas.list-discussions [_]
  (simple-speech-response "The topics are: " (str/join ", " (take 3 sample-discussions)) "."))

(defaction dbas.list-discussions.more [_]
  (let [more-topics (drop 3 sample-discussions)
        topic-count (count more-topics)]
    (simple-speech-response
     (if (zero? topic-count)
       "Sorry, but there are no more topics."
       (format "Ok, here are %d more topics: %s" topic-count (str/join ", " more-topics))))))
