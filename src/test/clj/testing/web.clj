;; ClojureBridgeMN.org
;; Copyright © 2015 Tom Marble
;; Licensed under the MIT license
;; https://github.com/clojurebridge-minneapolis/clojurebridgemn.org

(ns testing.web
  (:require [clojure.test :refer :all]
            [clojure.java.io :refer :all]
            [environ.core :refer [env]]
            [clojurebridgemn.web :refer :all] ))

(defonce html-test-file "target/test/index.html")

(deftest testing-web
  (testing "testing-web"
    (let [html-file html-test-file]
      (write-html html-file)
      (is (.exists (as-file html-file))))))
