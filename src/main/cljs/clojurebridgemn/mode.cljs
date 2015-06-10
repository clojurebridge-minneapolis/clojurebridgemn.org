;; ClojureBridgeMN.org
;; Copyright © 2015 Tom Marble
;; Licensed under the MIT license
;; https://github.com/clojurebridge-minneapolis/clojurebridgemn.org

(ns clojurebridgemn.mode)

;; by default the program-mode is :prod
;; if we have loaded the JavaScript function findns
;; then we can determine which namespaces have been loaded and
;; switch to the correct mode as needed
(defonce program-mode (atom :prod))

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

(defn find-ns
  "Returns JavaScript object corresponding to the namespace ns if it exists. Argument may be a symbol or a string"
  [ns]
  (when (exists? js/findns)
    (let [ns (if (symbol? ns) (name ns) ns)]
      (js/findns ns))))

(defn ns-exists?
  "Returns true if the given ns exists. Argument may be a symbol or a string"
  [ns]
  (not (nil? (find-ns ns))))
