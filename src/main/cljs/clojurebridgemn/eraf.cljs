;; ClojureBridgeMN.org
;; Copyright © 2015 Tom Marble
;; Licensed under the MIT license
;; https://github.com/clojurebridge-minneapolis/clojurebridgemn.org

;; WORK IN PROGRESS to use requestAnimationFrame

(ns clojurebridgemn.eraf)

(def ^{:dynamic true} *eraf-delay* 16.6)

(defn ersatzAnimationFrame [callback]
  ;; coerce to float as the real raf
  (callback (float (.now js/Date))))

(defn ersatzRequestAnimationFrame [callback]
  (js/setTimeout #(ersatzAnimationFrame callback) *eraf-delay*))

(def ^{:dynamic true} request-animation-frame
  (cond
    (exists? js/requestAnimationFrame) js/requestAnimationFrame
    true ersatzRequestAnimationFrame))

;; this function is exceedingly useful to debug raf
;; events in slow motion
(defn slow-raf! [&[delay]]
  (set! request-animation-frame ersatzRequestAnimationFrame)
  (let [d (if delay delay 996)]
    (set! *eraf-delay* d)))

(def ^{:dynamic true} run-raf true)
(def ^{:dynamic true} last-raf nil)

(defn request-new-animation-loop [callback]
  (request-animation-frame
    (fn [timestamp]
      (callback timestamp)
      (if run-raf
        (request-new-animation-loop callback))
      )))

(defn stop-raf! []
  (set! run-raf false))

(defn myraf [timestamp]
  (when (nil? last-raf)
    (set! last-raf timestamp))
  (let [elapsed (- timestamp last-raf)]
    (when (> elapsed 3000.0)
      (println "RAF @" timestamp elapsed)
      (set! last-raf timestamp)
      )))

(defn start-raf! []
  (set! run-raf true)
  (request-new-animation-loop myraf))
