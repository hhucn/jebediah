(ns dialogflow.v2beta.integrations.facebook)

(defrecord Response [text attachment quick_replies])



(defn response
  ([text_or_attachment]
   (response text_or_attachment nil))
  ([text attachment]
   {:platform "FACEBOOK"
    :payload  {:facebook (merge text attachment)}}))

(defn response-with-quick-replies
  ([text-or-attachment quick-replies]
   (response-with-quick-replies text-or-attachment nil quick-replies))
  ([text attachment quick-replies]
   (update-in (response text attachment) [:payload :facebook] merge quick-replies)))

(defn text [text]
  {:text text})

(defn button
  ([title] (button title title))
  ([title payload]
   {:type    "postback"
    :title   title
    :payload payload}))

(defn list-entry-postback-button
  ([title subtitle & buttons]
   {:title    title
    :subtitle subtitle
    :buttons  buttons}))


(defn list-entry [entry]
  {:title    (:title entry)
   :subtitle (:subtitle entry)})

(defn rich-list-with-text
  ([text entries more?]
   {:attachment {:type "template"
                 :payload
                       (merge
                         {:template_type     "list"
                          :top_element_style "compact"
                          :elements          (cons {:title text :subtitle " "} (vec entries))}
                         (when more?
                           {:buttons [{:title   "View More"
                                       :type    "postback"
                                       :payload "more"}]}))}}))

(defn rich-list
  ([entries] (rich-list entries false))
  ([entries more?]
   {:attachment {:type "template"
                 :payload
                       (merge
                         {:template_type     "list"
                          :top_element_style "compact"
                          :elements          (vec entries)}
                         (when more?
                           {:buttons [{:title   "View More"
                                       :type    "postback"
                                       :payload "more"}]}))}}))

(defn- quick-reply [reply]
  (if (map? reply)
    reply
    {:content_type "text"
     :title        reply
     :payload      reply}))


(defn quick-replies [& replies]
  (assert (< (count replies) 12) "Only up to 11 quick replies are supported by facebook!")
  {:quick_replies (mapv quick-reply replies)})



