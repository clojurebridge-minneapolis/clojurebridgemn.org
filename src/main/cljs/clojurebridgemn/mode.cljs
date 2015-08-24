;; ClojureBridgeMN.org
;; Copyright © 2015 Tom Marble
;; Licensed under the MIT license
;; https://github.com/clojurebridge-minneapolis/clojurebridgemn.org

(ns clojurebridgemn.mode)

(defonce program-mode (atom nil))

(defn set-program-mode! []
  (reset! program-mode
    (if (find-ns 'testing.clojurebridgemn.client) :test
        (if (find-ns 'production.prod) :prod :dev))))

(defn get-program-mode []
  (when-not @program-mode
    (set-program-mode!))
  @program-mode)

(defn development?
  "Returns true if in :dev mode"
  []
  (when-not @program-mode
    (set-program-mode!))
  (= @program-mode :dev))

(defn testing?
  "Returns true if in :test mode"
  []
  (when-not @program-mode
    (set-program-mode!))
  (= @program-mode :test))

(defn production?
  "Returns true if in :prod mode"
  []
  (when-not @program-mode
    (set-program-mode!))
  (= @program-mode :prod))
