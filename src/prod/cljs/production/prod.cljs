;; ClojureBridgeMN.org
;; Copyright © 2015 Tom Marble
;; Licensed under the MIT license
;; https://github.com/clojurebridge-minneapolis/clojurebridgemn.org

(ns production.prod)

(defn ^:export debug []
  (println "This namespace is for :prod mode debugging only."))
