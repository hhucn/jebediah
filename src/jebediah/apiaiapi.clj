(ns jebediah.apiaiapi)

(defmulti dispatch-action #(get-in % [:result :action]))

(defmacro defaction [name params & body]
  `(defmethod dispatch-action ~(str name) ~params
     ~@body))

(defn simple-apiai-response [speech]
   {:speech speech
    :displayText speech
    :data []
    :contextOut []
    :source ""})