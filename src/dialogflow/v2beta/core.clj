(ns dialogflow.v2beta.core
  (:require [dialogflow.v2beta.integrations.agent :as agent]
            [clojure.spec.alpha :as s]
            [jebediah.dialogflow-spec]
            [clojure.string :as str]))

(defmulti dispatch-action
          "Executes an action"
          #(get-in % [:queryResult :action]))

(defmethod dispatch-action :default [r]
  (agent/speech (str "Action: " (get-in r [:queryResult :action]) " not implemented! Sorry.")))

#_(defmacro defaction
    "Creates and registers a new action. Name this action exactly like you did in api.ai!"
    [name params & body]
    `(do
      (defn ~name ~params ~@body
       (defmethod dispatch-action ~(str name) ~params (~name ~@params)))))

(defn gen-context-name [request context]
  (str (:session request) "/contexts/" (name context)))

(defn context [request name parameters lifespan]
  {:name          (gen-context-name request name)
   :parameters    parameters
   :lifespanCount lifespan})

(defn reset-context [request name]
  (context request name {} 0))

(defn get-context [request context]
  (let [cs (get-in request [:queryResult :outputContexts])]
    (first (filter #(= (gen-context-name request context) (:name %)) cs))))

(defn reset-all-contexts [{{cs :outputContexts} :queryResult :as request}]
  (->> cs
       (map :name)
       (map #(last (str/split % #"/")))
       (mapv #(reset-context request %))))

(defn get-parameter [request parameter]
  (-> request :queryResult :parameters parameter))

(s/def ::args (s/cat :parameters (s/* (s/cat :opt keyword? :val any?)) :body (s/+ any?)))

(defmacro defaction
  "Creates and registers a new action. Name this action exactly like you did in dialogflow!"
  [name & args]
  (let [{:keys [body]:as conformed} (s/conform ::args args)
        params (apply hash-map (mapcat vals (:parameters conformed)))
        contexts (:contexts params)
        parameters (:parameters params)
        request (:request params)
        language (:language params)]
    `(defmethod dispatch-action ~(str name) [request#]
       (let ~(vec (concat
                    (interleave contexts (for [context contexts]
                                           `(get-context request# ~(keyword context))))
                    (interleave parameters (for [parameter parameters]
                                            `(get-parameter request# ~(keyword parameter))))
                    (when request [request `request#])
                    (when language [language `(get-in request# [:queryResult :languageCode])])))
         ~@body))))




(comment
 (macroexpand-1 '(defaction bla :contexts [user position] :parameters [test] :request {qr :queryResult} (print user) (print user))))

;;;; Specs

(s/fdef dispatch-action
        :args :jebediah.dialogflow-spec/Webhook-request
        :ret :jebediah.dialogflow-spec/Webhook-response)

(s/fdef defaction
        :args (s/cat :name simple-symbol? :args ::args))