(ns jebediah.actions.dbas_test
  (:require [clojure.test :refer :all]
            [jebediah.actions.dbas :as actions]))

(deftest finish-url
  (let [finish-url? (var actions/finish-url?)]
    (testing "finish url is detected correctly"
      (is (finish-url? "https://web.dbas.coruscant.cs.uni-duesseldorf.de/api/cat-or-dog/finish/82?history=/attitude/4-/reaction/79/rebut/8")
          "false-negative with fully qualified urls")

      (is (finish-url? "/cat-or-dog/finish/82?history=/attitude/4-/reaction/79/rebut/8")
          "false-negative with partial urls")

      (is (not (finish-url? "https://web.dbas.coruscant.cs.uni-duesseldorf.de/api/cat-or-dog/reaction/79/rebut/8?history=attitude/4"))
          "false-negative with fully qualified urls")

      (is (not (finish-url? "/cat-or-dog/reaction/79/rebut/8?history=attitude/4"))
          "false-positive with partial urls")

      (is (finish-url? "http://0.0.0.0:4284/api/make-the-world-better/finish/93")
          "url without parameters"))))