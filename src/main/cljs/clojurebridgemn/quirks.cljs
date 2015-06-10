;; ClojureBridgeMN.org
;; Copyright © 2015 Tom Marble
;; Licensed under the MIT license
;; https://github.com/clojurebridge-minneapolis/clojurebridgemn.org

(ns clojurebridgemn.quirks
    (:require [goog.string :as gstring]))

;; we can do minimal browser detection with the user-agent string
(defonce user-agent (.-userAgent js/navigator))

(def phantomjs? (gstring/contains user-agent "PhantomJS"))
(def mobile? (gstring/contains user-agent "Mobile"))
(def android? (gstring/contains user-agent "Android"))
(def safari? (gstring/contains user-agent "Safari"))
(def android-safari? (and android? safari?))
(def mobile-safari? (and mobile? safari? (not android?)))

;; NOTE: fullscreen MUST only be called from an event handler (e.g. on-click)
;; will NOT work on the REPL!
;; Adapted from https://github.com/sindresorhus/screenfull.js
(def fullscreen-quirks
  {:webkit {:requestFullscreen #(.webkitRequestFullscreen %)
            :exitFullscreenProp "webkitExitFullscreen"
            :exitFullscreen #(.webkitExitFullscreen %)
            :fullscreenElement "webkitFullscreenElement"
            :fullscreenEnabled "webkitFullscreenEnabled"
            :fullscreenchange "webkitfullscreenchange"
            :fullscreenerror "webkitfullscreenerror"}
   :old-webkit {:requestFullscreen #(.webkitRequestFullScreen %)
                :exitFullscreenProp "webkitCancelFullScreen"
                :exitFullscreen #(.webkitCancelFullScreen %)
                :fullscreenElement "webkitCurrentFullScreenElement"
                :fullscreenEnabled "webkitCancelFullScreen"
                :fullscreenchange "webkitfullscreenchange"
                :fullscreenerror "webkitfullscreenerror"}
  :firefox {:requestFullscreen #(.mozRequestFullScreen %)
            :exitFullscreenProp "mozCancelFullScreen"
            :exitFullscreen #(.mozCancelFullScreen %)
            :fullscreenElement "mozFullScreenElement"
            :fullscreenEnabled "mozFullScreenEnabled"
            :fullscreenchange "mozfullscreenchange"
            :fullscreenerror "mozfullscreenerror"}
  :ie {:requestFullscreen #(.msRequestFullscreen %)
       :exitFullscreenProp "msExitFullscreen"
       :exitFullscreen #(.msExitFullscreen %)
       :fullscreenElement "msFullscreenElement"
       :fullscreenEnabled "msFullscreenEnabled"
       :fullscreenchange "MSfullscreenChange"
       :fullscreenerror "MSfullscreenError"}})

;; defines the key (if any) into fullscreen-quirks
(def fullscreen
  (first
    (remove nil?
      (for [browser (keys fullscreen-quirks)]
        (let [exit-fullscreen (get-in fullscreen-quirks
                                [browser :exitFullscreenProp])]
          (if (not (nil? (aget js/document exit-fullscreen)))
            browser))))))

(defn can-fullscreen? []
  (and fullscreen (not android-safari?)))

(defn request-fullscreen [ & [element]]
  (when fullscreen
    (let [element (or element (.-documentElement js/document))
          f (get-in fullscreen-quirks [fullscreen :requestFullscreen])]
      (f element))))

(defn exit-fullscreen []
  (when fullscreen
    (let [element js/document
          f (get-in fullscreen-quirks [fullscreen :exitFullscreen])]
      (f element)
      )))

(defn fullscreen? []
  (and fullscreen
    (not (nil? (aget js/document
                 (get-in fullscreen-quirks [fullscreen :fullscreenElement]))))))

;; element could be (.-documentElement js/document)
;; or passed in?
(defn toggle-fullscreen! [& [e]]
  (when fullscreen
    (let [element (.-documentElement js/document)]
      (if (fullscreen?)
        (exit-fullscreen)
        (request-fullscreen element)
        )))
  (when e
    (.preventDefault e)
    (.stopPropagation e)))

(defn set-fullscreen! [full? & [e]]
  (when fullscreen
    (let [element (.-documentElement js/document)]
      (if (fullscreen?)
        (if (not full?)
          (exit-fullscreen))
        (if full?
          (request-fullscreen element)))))
  (when e
    (.preventDefault e)
    (.stopPropagation e)))

;; swipe handling

(def touch (atom nil))

(defn handleTouchStart [e]
  (let [first-touch (aget (.-touches e) 0)
        v {:x (.-clientX first-touch)
           :y (.-clientY first-touch)}]
    ;; (js/alert (str v))
    (reset! touch v)))

(defn handleTouchMove [left right e]
  (let [{:keys [x y]} @touch]
    (when (and x y)
      (let [last-touch (aget (.-touches e) 0)
            xup (.-clientX last-touch)
            yup (.-clientY last-touch)
            dx (- x xup)
            dy (- y yup)
            absx (.abs js/Math dx)
            absy (.abs js/Math dy)]
        ;; (js/alert (str "dx:" dx " y:" dy))
        (if (> absx absy)
          (if (pos? dx)
            (left)
            (right))
            ;; (js/alert "left")
            ;; (js/alert "right"))
          (if (pos? dy)
            (println "up")
            (println "down")))
            ;; (js/alert "up")
            ;; (js/alert "down")))
        (reset! touch nil)))))

(defn initialize-swipe [left right]
  (.addEventListener js/document "touchstart"
    handleTouchStart false)
  (.addEventListener js/document "touchmove"
    (partial handleTouchMove left right) false))

;; 300ms problem
;; based upon http://cubiq.org/remove-onclick-delay-on-webkit-for-iphone
(defn ^:export nodelay [element]
  (this-as this
    (set! (.-element this) element)
    (if (not (nil? (.-Touch js/window)))
      (.addEventListener (.-element this) "touchstart" this false))))

(set! (.. js/clojurebridgemn.quirks.nodelay -prototype -handleEvent)
  (fn [e]
    (this-as this
      (case (.-type e)
        "touchstart" (.onTouchStart this e)
        "touchmove" (.onTouchMove this e)
        "touchend" (.onTouchEnd this e)))))

(set! (.. js/clojurebridgemn.quirks.nodelay -prototype -onTouchStart)
  (fn [e]
    (this-as this
      (.preventDefault e)
      (set! (.-moved this) false)
      (let [touches (aget (.-targetTouches e) 0)]
        (set! (.-theTarget this)
          (.elementFromPoint js/document (.-clientX touches) (.-clientY touches))))
      (if (= (.-nodeType (.-theTarget this)) 3)
        (set! (.-theTarget this)
          (.-parentNode (.-theTarget this))))
      (.addEventListener (.-element this) "touchmove" this false)
      (.addEventListener (.-element this) "touchend" this false))))

(set! (.. js/clojurebridgemn.quirks.nodelay -prototype -onTouchMove)
  (fn [e]
    (this-as this
      (set! (.-moved this) true))))

(set! (.. js/clojurebridgemn.quirks.nodelay -prototype -onTouchEnd)
  (fn [e]
    (this-as this
      (.removeEventListener (.-element this) "touchmove" this false)
      (.removeEventListener (.-element this) "touchend" this false)
      (if (and (not (.-moved this)) (not (nil? (.-theTarget this))))
        (let [mouse (.createEvent js/document "MouseEvents")]
          (.initEvent mouse "click" true true)
          (.dispatchEvent (.-theTarget this) mouse)))
      (set! (.-theTarget this) nil))))
