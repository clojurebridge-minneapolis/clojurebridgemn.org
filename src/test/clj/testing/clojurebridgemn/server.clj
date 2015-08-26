;; ClojureBridgeMN.org
;; Copyright © 2015 Tom Marble
;; Licensed under the MIT license
;; https://github.com/clojurebridge-minneapolis/clojurebridgemn.org

(ns testing.clojurebridgemn.server
  (:require [clojure.test :refer :all]
            [clojurebridgemn.server :refer :all]
            [clojurebridgemn.utils :refer :all]
            [environ.core :refer [env]]))

;; here clj can be tested without the frontend

(deftest testing-clojurebridgemn-server
  (testing "testing-clojurebridgemn-server"
    (do
      (println "program-mode" program-mode)
      (println "selenium-browser" (:selenium-browser env))
      (println "selenium-profile" (:selenium-profile env))
      (is (not (development?)))
      (is (testing?))
      (is (not (production?)))
      (is (not (running?))))))
