;; ClojureBridgeMN.org
;; Copyright © 2015 Tom Marble
;; Licensed under the MIT license
;; https://github.com/clojurebridge-minneapolis/clojurebridgemn.org

(ns clojurebridgemn.web
  (:require [net.cgrand.enlive-html :refer :all]
            [clojurebridgemn.mode :refer :all]))

(def public "../../resources/public/")

(def app-css-uri  "css/clojurebridgemn.css")

(def app-css (str public app-css-uri))

(def findns-uri "js/findns.js")

(def findns (str public findns-uri))

(def phantomjs-shims "../../src/test/phantomjs/phantomjs-shims.js")

(def app "js/compiled/app.js")

(def title "clojurebridgemn.org")

(def lf "\n")

(def basic-page
  [{:tag :html
    :attrs nil
    :content
    [lf {:tag :head
         :attrs nil
         :content
         [lf {:tag :title
              :attrs nil
              :content [ title ]}
          lf]}
     lf {:tag :body
         :attrs nil
         :content
         [lf]}
     lf]}])

(defn html5dtd [nodes]
  (if (= (:type (first nodes)) :dtd)
    nodes
    (cons {:type :dtd :data ["html" nil nil]} nodes)))

(defn add-meta [nodes meta]
  (at nodes
    [:head] (append
              (html [:meta meta]) lf)))

(defn charset-utf-8 [nodes]
  (add-meta nodes {:charset "utf-8"}))
  ;; (at nodes
  ;;   [:head] (append
  ;;             (html [:meta {:charset "utf-8"}]) lf)))

(defn viewport [nodes]
  (add-meta nodes {:name "viewport"
                   :content "width=device-width,initial-scale=1.0,maximum-scale=1.0,minimum-scale=1.0,user-scalable=0"}))

(defn html-lang [nodes lang]
  (at nodes
    [:head] (set-attr :lang lang)))

(defn append-at [selector nodes new-nodes]
  (at nodes
    [selector] (append new-nodes lf)))

(defn append-head [nodes new-head-nodes]
  (append-at :head nodes new-head-nodes))

(defn append-body [nodes new-body-nodes]
  (append-at :body nodes new-body-nodes))

(defn add-favicon [nodes]
  (append-head nodes
    (html [:link {:rel "icon" :href "/favicon.ico" :type "image/x-icon"}])))

(defn add-css [nodes style-uri]
  (append-head nodes
    (html [:link {:rel "stylesheet" :href style-uri :type "text/css"}])))

(defn add-js [nodes script-uri]
  (append-head nodes
    (html [:script {:type "text/javascript" :src script-uri}])))

(defn add-h1 [nodes heading]
  (append-body nodes
    (html [:h1 heading])))

(defn add-div [nodes id]
  (append-body nodes
    (html [:div {:id id }])))

(defn add-textarea [nodes id]
  (append-body nodes
    (html [:textarea {:id id }])))

(defn render [t]
  (apply str t))

(defn render-snippet [s]
  {:headers {"Content-Type" "text/html; charset=utf-8"}
   :body (apply str (emit* s))})

(def basic-html5 (-> basic-page
                   html5dtd
                   (html-lang "en")
                   charset-utf-8
                   viewport))

(defn create-dev-html [& [req]]
  (-> basic-html5
    (add-favicon)
    (add-css app-css-uri)
    (add-js findns-uri)
    (add-js app)
    (add-div "app")
    render-snippet))

;; for production
(defn create-html [& [req]]
  (-> basic-html5
    (add-favicon)
    (add-css app-css-uri)
    (add-js app)
    (add-div "app")
    render-snippet))

(defn create-test-html [& [req]]
  (-> basic-html5
    (add-favicon)
    (add-css app-css)
    (add-js findns)
    (add-js phantomjs-shims)
    (add-js app)
    (add-div "app")
    (add-textarea "out")
    render-snippet))

(defn write-html [html-file]
  (let [html-str (create-test-html)]
    (println "writing" app "into" html-file)
    (spit html-file html-str)))
