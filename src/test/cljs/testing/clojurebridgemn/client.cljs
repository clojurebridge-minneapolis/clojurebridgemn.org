;; ClojureBridgeMN.org
;; Copyright © 2015 Tom Marble
;; Licensed under the MIT license
;; https://github.com/clojurebridge-minneapolis/clojurebridgemn.org

(ns testing.clojurebridgemn.client
  (:require-macros [cljs.test :refer [is deftest testing]])
  (:require [cljs.test]
            [clojurebridgemn.mode :as mode]
            [clojurebridgemn.client :refer [app-state menu-icons]]))

;; here cljs can be tested with or without the server running

(deftest testing-clojurebridgemn-client
  (testing "testing-clojurebridgemn-client"
    (is (not (mode/development?)))
    (is (mode/testing?))
    (is (not (mode/production?)))
    (is (= (:e-order @app-state) [:elements]))
    (is (= (:home menu-icons) "⌂"))))
