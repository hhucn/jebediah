(ns dialogflow.v2beta.integrations.facebook)

(defn list-entry-postback-button
  [title subtitle payload]
  {:title    title
   :subtitle subtitle
   :buttons  [{:type    "postback"
               :title   "This!"
               :payload payload}]})


(defn list-entry [entry]
  {:title    (:title entry)
   :subtitle (:subtitle entry)})

(defn rich-list
  ([entries] (rich-list entries false))
  ([entries more?]
   {:facebook
    {:attachment {:type "template"
                  :payload
                        (merge
                          {:template_type     "list"
                           :top_element_style "compact"
                           :elements          (vec entries)}
                          (when more?
                            {:buttons [{:title   "View More"
                                        :type    "postback"
                                        :payload "more"}]}))}}}))
