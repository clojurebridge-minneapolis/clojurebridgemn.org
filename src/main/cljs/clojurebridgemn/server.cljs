;; ClojureBridgeMN.org
;; Copyright © 2015 Tom Marble
;; Licensed under the MIT license
;; https://github.com/clojurebridge-minneapolis/clojurebridgemn.org

(ns clojurebridgemn.server
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [goog.string :as gstring]
            [clojurebridgemn.utils :refer [make-url]]))

(defn info-commit
  "As server for the last commit info, and, upon success
  call the provided callback function
  (update-info committer timestamp)."
  {:added "0.2.2"}
  [update-info]
  (let [url (make-url :uri "/info/commit")]
    (if (gstring/startsWith url "file")
      (println "unable to get last commit info when loaded from the local filesystem")
      (go (let [response (<! (http/get url))
                {:keys [success status body]} response]
            (if (and success (= status 200))
              (let [{:keys [user timestamp]} body]
                (println "info-commit:" body)
                (update-info user timestamp))
              (println "get-info FAILED:" url)))))))
