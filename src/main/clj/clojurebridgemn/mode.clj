;; ClojureBridgeMN.org
;; Copyright © 2015 Tom Marble
;; Licensed under the MIT license
;; https://github.com/clojurebridge-minneapolis/clojurebridgemn.org

(ns clojurebridgemn.mode
  (:require [environ.core :refer [env]]))

;; The program-mode environment variable is set for each
;; profile in project.clj
(defonce program-mode (atom (or (:program-mode env) :prod)))

(defn development?
  "Returns true if in :dev mode"
  []
  (= @program-mode :dev))

(defn testing?
  "Returns true if in :test mode"
  []
  (= @program-mode :test))

(defn production?
  "Returns true if in :prod mode"
  []
  (= @program-mode :prod))
