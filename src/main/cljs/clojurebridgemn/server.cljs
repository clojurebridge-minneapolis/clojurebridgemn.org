;; ClojureBridgeMN.org
;; Copyright © 2015 Tom Marble
;; Licensed under the MIT license
;; https://github.com/clojurebridge-minneapolis/clojurebridgemn.org

(ns clojurebridgemn.server
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))

(defn info-commit
  "As server for the last commit info, and, upon success
  call the provided callback function
  (update-info committer timestamp)."
  {:added "0.2.2"}
  [update-info]
  (let [location (.-location js/document)
        protocol (.-protocol location)
        hostname (.-hostname location)
        port (.-port location)
        server (str hostname (if (pos? (count port)) (str ":" port)))
        uri "/info/commit"
        url (str protocol "//" server uri)]
    ;; (println "get-info from" url "...")
    (go (let [response (<! (http/get url))
              {:keys [success status body]} response]
          (if (and success (= status 200))
            (let [{:keys [user timestamp]} body]
              (println "info-commit:" body)
              (update-info user timestamp))
            (println "get-info FAILED"))))))
