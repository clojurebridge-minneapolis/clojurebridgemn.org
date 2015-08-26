;; ClojureBridgeMN.org
;; Copyright © 2015 Tom Marble
;; Licensed under the MIT license
;; https://github.com/clojurebridge-minneapolis/clojurebridgemn.org

(ns clojurebridgemn.utils
  (:require [goog.string :as gstring]
            [goog.object :as gobject]
            [goog.array :as garray])
  (:import [goog.string StringBuffer]))

;; program-mode

;; http://www.martinklepsch.org/posts/parameterizing-clojurescript-builds.html
(goog-define program-mode "uninitialized")

(defn development?
  "Returns true if in :dev mode"
  []
  (= program-mode "dev"))

(defn testing?
  "Returns true if in :test mode"
  []
  (= program-mode "test"))

(defn production?
  "Returns true if in :prod mode"
  []
  (= program-mode "prod"))

;; printing helpers ---------------------------------

;; replaces any (fn? v) in the map m with "<fn>", recursively
(defn remove-fn [ & args ]
  (case (count args)
    1 (let [v (first args)]
        (cond
          (fn? v) "<fn>"
          (vector? v) (mapv remove-fn v)
          (map? v) (reduce-kv remove-fn {} v)
          :else v))
    3 (let [[m k v] args]
        (assoc m k
          (cond
            (fn? v) "<fn>"
            (vector? v) (mapv remove-fn v)
            (map? v) (reduce-kv remove-fn {} v)
            :else v)))
    "<unknown>"))

;; associate k v in m iff v
(defn assoc-if [m k v]
  (if v
    (assoc m k v)
    m))

;; find the index of o in v
(defn vec-index-of [v o]
  (first
    (remove nil?
      (for [i (range (count v))]
        (if (= (get v i) o) i)))))
