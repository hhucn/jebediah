(ns jebediah.handler-test
  (:require [clojure.test :refer :all]
            [clojure.string :refer [starts-with?]]
            [ring.mock.request :as mock]
            [jebediah.handler :refer [app]]))

(deftest test-GET
  (testing "GET"
    (testing "main route"
      (let [response (app (mock/request :get "/"))]
        (is (= 200 (:status response)))
        (is (starts-with? (:body response) "<iframe"))))))

(deftest test-POST
  (testing "POST"
    (testing "spec")))
