;; ClojureBridgeMN.org
;; Copyright © 2015 Tom Marble
;; Licensed under the MIT license
;; https://github.com/clojurebridge-minneapolis/clojurebridgemn.org

(ns clojurebridgemn.server
  (:gen-class) ;; for :uberjar
  (:require [clojure.java.io :as io]
            [compojure.core :refer [GET ANY defroutes]]
            [compojure.route :refer [resources not-found]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.params :as params]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [cheshire.core :refer [generate-string]]
            [environ.core :refer [env]]
            [aleph.http :refer [start-server]]
            [clojurebridgemn.mode :refer :all]
            [clojurebridgemn.web :as web]))

;; The server is the http server which will serve the application
(defonce server (atom nil))

(defn commit-info [req]
  (let [user (or (:server-committer env) "anonymous")
        timestamp (or (:server-timestamp env) "now")
        info {:user user :timestamp timestamp}
        json (generate-string info)]
    {:headers {"Content-Type" "application/json; charset=utf-8"}
     :body json}))

(defn web-html [& [req]]
  (if (production?)
    (web/create-html req)
    (web/create-dev-html req)))

(def web-html-memo (memoize web-html))

(defroutes routes
  (GET "/info/commit" req (commit-info req))
  (GET "/" req web-html-memo)
  (resources "/")
  (ANY "*" []
    (not-found (slurp (io/resource "404.html")))))

;; https://github.com/ring-clojure/ring-defaults
(def http-handler
  (wrap-gzip (wrap-defaults routes api-defaults)))

;; uniquely during development (w/figwheel)
(defroutes info-routes
  (GET "/info/commit" req (commit-info req)))

;; uniquely during development (w/figwheel)
(def info-handler
  (wrap-defaults info-routes api-defaults))

(defn running? []
  (not (nil? @server)))

(defn server-start [port]
  (if (running?)
    (println "server already running")
    (do
      (println "Starting web server on port" port)
      (reset! server (start-server http-handler {:port port}))
      @server)))

(defn server-stop []
  (if (running?)
    (do
      (println "Stopping web server")
      (.close @server)
      (reset! server nil))
    (println "server already stopped")))

(defn run [& [port]]
  (let [port (or port 8080)]
    (server-start port)))

(defn sleep
  "sleeps for the given number of seconds (may be fractional)"
  [s]
  (Thread/sleep (* 1000 s)))

;; BUG: needs proper logging!
(defn -main [& args]
  (let [arg1 (first args)
        port-str (or arg1 (:port env))
        port (or (and port-str (Integer. port-str)) 8080)]
    (println "program-mode" @program-mode)
    (run port)
    (while true
      (print ".")
      (flush)
      (sleep 10))
    (System/exit 0)))
