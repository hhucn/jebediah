(ns jebediah.config)


(def jebediah-test-page-access-token (or (System/getenv "PAGE_ACCESS_TOKEN") "SECRET_TOKEN"))
(def dbas-url (or (System/getenv "DBAS_BASE") "http://0.0.0.0:4284" "https://web.dbas.coruscant.cs.uni-duesseldorf.de"))
(def dbas-api-token (or (System/getenv "DBAS_TOKEN") "b6951:a9a0b834d2zzzzzzzzzz358a1f45" "29a7b:d2f9c60fed7e722f9d9zzzzzz8bcxxxxxxxxx8cb762a9b92c4388c0"))
(def eauth-url (or (System/getenv "EAUTH") "http://localhost:1236" "https://eauth.coruscant.cs.uni-duesseldorf.de"))
(def dialogflow-user (System/getenv "DIALOGFLOW_AUTH_USER"))
(def dialogflow-pw (System/getenv "DIALOGFLOW_AUTH_PW"))