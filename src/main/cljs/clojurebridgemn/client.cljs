;; ClojureBridgeMN.org
;; Copyright © 2015 Tom Marble
;; Licensed under the MIT license
;; https://github.com/clojurebridge-minneapolis/clojurebridgemn.org

(ns ^:figwheel-always clojurebridgemn.client
    (:require-macros [cljs.core.async.macros :refer [go go-loop]])
    (:require [clojure.set :refer [difference]]
              [cljs.pprint :refer [pprint]]
              [cljs.core.async :as async
               :refer [<! >! chan pub put! sub take! timeout buffer sliding-buffer dropping-buffer]]
              [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [goog.string :as gstring]
              [goog.dom :as gdom]
              [sablono.core :as html :refer-macros [html]]
              [secretary.core :as secretary :refer-macros [defroute]]
              [goog.events :as events]
              [goog.history.EventType :as EventType]
              [cljs-http.client :as http]
              [clojurebridgemn.data :as data]
              [clojurebridgemn.utils :refer [remove-fn assoc-if vec-index-of program-mode development? make-url]]
              [clojurebridgemn.server :as server]
              [clojurebridgemn.quirks :as quirks])
    (:import goog.History))

(enable-console-print!)

;; Om application ===========================

(defonce app-state (atom nil))
(defonce e-types (atom nil))

(def menu-icons
  {:home "⌂"
   :sponsors "$"
   :donations "✋"
   :blog "✍"
   :calendar "▦"
   :links "λ"
   :board "π"
   :settings "⇄"
   :pix+quotes "☙"})

(def no-arrows-pages #{:menu :pix+quotes})

(def swipe-pages (vec (difference (set (keys menu-icons)) no-arrows-pages)))

(def subdued-pages (difference (set swipe-pages) #{:home :settings :pix+quotes}))

;; debugging helper to inspect app-state on the REPL
(defn ppstate [& [ks]]
  (let [state (if ks
                (get-in @app-state ks)
                @app-state)]
    (pprint (remove-fn state))
    nil))

;; core.async events -----------------------------------------

;; return the events channel (for publishing events)
;; each value published should be a hash map which contains
;; the :id of the component
(defn get-events []
  (get-in @app-state [:channels :events]))

;; this is the pub channel for id events
;; which can be used to subscribe to a specific id
(defn get-id-events []
  (get-in @app-state [:channels :id-events]))

;; put the hashmap m on the events channel with component id
(defn put-event! [cursor]
  (put! (get-events) cursor))

;; global functions -----------------------------------------------

(defn set-subdued! [v]
  (swap! app-state
    #(assoc %
       :subdued v
       :style (str (if v "subdued." "")
                (gstring/remove (:style %) "subdued.")))))

;; set font size
(defn set-size! [size & [e]]
  (put-event! {:id :size :value size})
  (swap! app-state
    #(assoc % :style (str (if (:subdued %) "subdued." "") (name size))))
  (when e
    (.preventDefault e)
    (.stopPropagation e)))

(defn goto-page! [page]
  (let [page (or page :toggle-menu)
        header (get-in @app-state [:elements :header])
        current-page (:page header)
        prev-page (or (:prev-page header) :home)
        new-page (if (= page :toggle-menu)
                   (if (= current-page :menu) prev-page :menu)
                   page)]
    (put-event! {:id :root :page new-page :prev-page current-page})))

;; return the previous swipe-able page
(defn prev-page []
  (let [page (get-in @app-state [:elements :header :page])
        n (count swipe-pages)
        i (dec (vec-index-of swipe-pages page))
        i (if (< i 0) (dec n) i)
        new-page (nth swipe-pages i)]
    new-page))

;; return the next swipe-able page
(defn next-page []
  (let [page (get-in @app-state [:elements :header :page])
        n (count swipe-pages)
        i (inc (vec-index-of swipe-pages page))
        i (if (>= i n) 0 i)
        new-page (nth swipe-pages i)]
    new-page))

;; auto-advance tools -----------------------------------

;; k is component key in the top level of @app-state
;; :i is the current thing
;; :n is the number of things
;; :delay is the delay in seconds before auto-advancing
;; :checkbox is the :id of the checkbox which controls auto-advancing
;; :auto-id is the setInterval or setTimeout id

;; get the current thing :i for k
(defn get-auto [k]
  (get-in @app-state [:elements k :i]))

;; returns true if auto-pix is on, false if off
(defn get-auto-pix []
  (not (not (get-in @app-state [:elements :pictures :auto-id]))))

;; returns true if auto-quotes is on, false if off
(defn get-auto-quotes []
  (not (not (get-in @app-state [:elements :quotes :auto-id]))))

;; set the current thing :i for k
(defn set-auto! [k i]
  (let [n (get-in @app-state [:elements k :n])
        i (if (>= i n) 0 (if (< i 0) (dec n) i))]
    (swap! app-state #(assoc-in % [:elements k :i] i))
    nil))

;; stop auto advancing for k
;; if restart then this is just a timer restart
(defn stop-auto! [k & [restart]]
  (let [component (get-in @app-state [:elements k])
        {:keys [auto-id checkbox]} component]
    (when auto-id
      (js/clearTimeout auto-id)
      (swap! app-state assoc-in [:elements k :auto-id] nil)
      (when (and (not restart) checkbox) ;; tell checkbox STOP
        ;; handle case where checkbox has not yet been mounted
        (let [pending (get-in @app-state [:channels checkbox])]
          (if (vector? pending) ;; NOT yet mounted
            (swap! app-state update-in [:channels checkbox] conj false)
            (put-event! {:id checkbox :checked false})))))))

(declare next-auto!)
(declare goto-uri)

;; start auto advancing for k
;; if continue then restart timer
(defn start-auto! [k & [continue]]
  (let [component (get-in @app-state [:elements k])
        {:keys [auto-id delay checkbox]} component]
    (if (and auto-id (not continue))
      (stop-auto! k true))
    (let [auto-id (js/setTimeout #(next-auto! k) (* delay 1000))]
      (swap! app-state assoc-in [:elements k :auto-id] auto-id)
      (when (and (not continue) checkbox) ;; tell checkbox START
        ;; handle case where checkbox has not yet been mounted
        (let [pending (get-in @app-state [:channels checkbox])]
          (if (vector? pending) ;; NOT yet mounted
              (swap! app-state update-in [:channels checkbox] conj true)
              (put-event! {:id checkbox :checked true})))))))

;; ask to advance to the next thing
(defn next-auto! [k]
  (start-auto! k true) ;; new setTimeout
  (if (= k :pictures)
    (goto-uri :picture +1)
    (goto-uri :quote +1)))

;; page routing --------------------------------------------------------------

(secretary/set-config! :prefix "#")

(defn click-uri [uri]
  (let [base-url (get-in @app-state [:base-url])
        current-uri (.-href (.-location js/window))
        current-uri (if (gstring/startsWith current-uri base-url)
                      (subs current-uri (count base-url))
                      current-uri)
        ;; the following will clear wonky, hand crafted URL's
        uri (if (or (empty? current-uri) (= (first current-uri) "#"))
              uri
              (str base-url uri))]
    (if (not= uri current-uri)
      (set! (.-href (.-location js/window)) uri))))

(defn goto-uri [&{:keys [page picture quote auto-pix auto-quotes] :as opts}]
  (let [current-pg (get-in @app-state [:elements :header :page])
        page (if (integer? page) ;; relative movement?
               (if (pos? page) (next-page) (prev-page))
               page)
        page (or page current-pg)
        pg (keyword page)
        pg (if (get menu-icons pg) pg :home) ;; fix invalid page here
        page (name pg)
        current-p (get-auto :pictures)
        max-p (get-in @app-state [:elements :pictures :n])
        current-auto-pix (get-auto-pix)
        auto-pix (if (nil? auto-pix) current-auto-pix auto-pix)
        p (cond
            (integer? picture) ;; relative movement
            (let [p (+ current-p picture)]
              (if (>= p max-p) 0 (if (< p 0) (dec max-p) p)))
            (string? picture) (int (last (gstring/splitLimit picture "-" 1)))
            :else current-p)
        p (max 0 (min p (dec max-p)))
        picture (str "picture-" p)
        current-q (get-auto :quotes)
        max-q (get-in @app-state [:elements :quotes :n])
        current-auto-quotes (get-auto-quotes)
        auto-quotes (if (nil? auto-quotes) current-auto-quotes auto-quotes)
        q (cond
            (integer? quote) ;; relative movement
            (let [q (+ current-q quote)]
              (if (>= q max-q) 0 (if (< q 0) (dec max-q) q)))
            (string? quote) (int (last (gstring/splitLimit quote "-" 1)))
            :else current-q)
        q (max 0 (min q (dec max-q)))
        quote (str "quote-" q)
        query-params (when (or auto-pix auto-quotes)
                       (str "?"
                         (if auto-pix "auto-pix=1")
                         (if auto-quotes (if auto-pix "&auto-quotes=1"
                                             "auto-quotes=1"))))
        uri (str "#/" page "/" picture "/" quote query-params)]
    (when-not (= pg current-pg) ;; page changed
      (goto-page! pg))
    (when-not (= p current-p) ;; picture changed
      (set-auto! :pictures p))
    (when-not (= auto-pix current-auto-pix) ;; auto-pix changed
      (if auto-pix
        (start-auto! :pictures)
        (stop-auto! :pictures)))
    (when-not (= q current-q) ;; quote chqnged
      (set-auto! :quotes q))
    (when-not (= auto-quotes current-auto-quotes) ;; auto-quotes changed
      (if auto-quotes
        (start-auto! :quotes)
        (stop-auto! :quotes)))
    ;; ensure URI matches our current settings
    (click-uri uri)))

(defn interpret-query-params
  ([m]
   (reduce-kv interpret-query-params {}
     (merge {:auto-pix :default :auto-quotes :default} m)))
  ([m k v]
   (assoc m k
     (let [new-v
           (case v
             "1" true ;; specified as turn-on
             "0" false ;; specified as turn-off
             ;; default -- do not change
             (case k
               :auto-pix (get-auto-pix)
               :auto-quotes (get-auto-quotes)
               ;; default
               false))]
       new-v))))

(defroute page-picture-quote-route "/:page/:picture/:quote" {:keys [page picture quote query-params] :as params}
  (let [{:keys [auto-pix auto-quotes]} (interpret-query-params query-params)]
    (goto-uri :page page :picture picture :quote quote
      :auto-pix auto-pix :auto-quotes auto-quotes)))

(defroute page-picture-route "/:page/:picture" {:keys [page picture query-params] :as params}
  (let [{:keys [auto-pix auto-quotes]} (interpret-query-params query-params)]
    (goto-uri :page page :picture picture
      :auto-pix auto-pix :auto-quotes auto-quotes)))

(defroute page-route "/:page" {:keys [page query-params] :as params}
  (let [{:keys [auto-pix auto-quotes]} (interpret-query-params query-params)]
    (goto-uri :page page
      :auto-pix auto-pix :auto-quotes auto-quotes)))

;; Turn on auto-pix and auto-quotes here
;; HOWEVER if someone starts with a full URL (e.g. a route above) then respect query-params
(defroute home-route "/" [query-params]
  (goto-uri :page :home :auto-pix true :auto-quotes true))

(defroute not-found-route "*" []
  (println "PAGE NOT FOUND")
  (goto-uri :page :home :auto-pix true :auto-quotes true))

(defn start-secretary []
  ;; NOTE using the input and iframe arguments prevents History from dynamically
  ;; writing to the DOM which can cause problems
  ;; FFI see https://github.com/google/closure-library/blob/master/closure/goog/history/history.js#L189
  (let [input (gdom/getElement "history-input")
        iframe (gdom/getElement "history-iframe")
        h (History. false nil input iframe)]
    (events/listen h EventType/NAVIGATE #(secretary/dispatch! (.-token %)))
    (doto h (.setEnabled true))))

;; components -----------------------------------------------------

(defn quotes [cursor _ {:keys [opts-id] :as opts}]
  (reify om/IRender
    (render [_]
      (let [{:keys [i id style]} cursor
            id (if id id (do (om/update! cursor [:id] opts-id) opts-id))
            style (or style "quotes")
            tag (keyword (str "div#" (name id) "." style))
            {:keys [quote source]} (nth data/quotes i)
            thequote (str "\"" quote "\"")
            said (str " --"
                   (if (= source :student)
                     "student" "mentor"))]
        (html [tag thequote [:i said]])))))

(defn pictures [cursor owner {:keys [opts-id] :as opts}]
  (reify
    om/IRender
    (render [_]
      (html
        (let [{:keys [i id]} cursor
              id (if id id (do (om/update! cursor [:id] opts-id) opts-id))
              ;; NOTE mobile-safari? doesn't handle CSS3 properly
              ipad (if quirks/mobile-safari? "i" "")
              ;; ipad ""
              style (str "pictures" ipad ".pictures" i)
              tag (keyword (str "div#" (name id) "." style))]
          [tag])))))

;; cursor here is assumed to have a key
;; :checkbox for checkbox components and
;; :value for other components
;; Optional callback fn will be passed the cursor
(defn input-change!
  "update input"
  [callback cursor e]
  (let [target (.. e -currentTarget)
        input-type (.-type target)]
    (.preventDefault e)
    (.stopPropagation e)
    (if (= input-type "checkbox")
      (let [checked (.-checked target)
            cursor (assoc cursor :checked checked)]
        ;; instead of putting the event here, do it in the callback
        ;; (put-event! cursor)
        (if callback (callback cursor)))
      (let [v (gstring/trim (.-value target))
            value (if (= input-type "radio") (keyword v) v)
            cursor (assoc cursor :value value)]
        ;; assume callback will do (om/update! cursor [:value] value)
        (if callback (callback cursor))))))

;; on-change is an optional function callback which takes the cursor
;; DEFAULT's to publishing the event on the id channel
(defn checkbox-data [& {:keys [id style label disabled checked on-change] :as opts}]
  (-> {:e-type :checkbox}
    (assoc-if :id id)
    (assoc-if :style style)
    (assoc-if :label label)
    (assoc-if :disabled disabled)
    (assoc-if :checked checked)
    (assoc :on-change
      (if on-change on-change
          (fn [cursor] (put-event! cursor))))))

(defn checkbox [cursor owner {:keys [opts-id] :as opts}]
  (reify
    om/IInitState
    (init-state [_]
      (println "init-state checkbox" opts-id "is" (:checked cursor))
      {:is-checked (not (not (:checked cursor)))})
    om/IWillMount
    (will-mount [_]
      (let [{:keys [id]} cursor
            id (keyword (or id opts-id))
            checkbox-events (chan)
            pending (get-in @app-state [:channels id])]
        (when (vector? pending)
          (if-not (empty? pending)
            (om/set-state! owner [:is-checked] (last pending)))
          (swap! app-state assoc-in [:channels id] nil))
        (sub (get-id-events) id checkbox-events)
        (go-loop []
          (let [{:keys [id checked]} (<! checkbox-events)
                checked (not (not checked))]
            (om/set-state! owner [:is-checked] checked)
            (recur)
            ))))
    om/IRenderState
    (render-state [_ {:keys [is-checked]}]
      (let [{:keys [id style label disabled checked on-change]} cursor
            id (or id (do (om/update! cursor [:id] opts-id) opts-id))
            style (or style "checkbox")
            tag (keyword (str "input#" (name id) "." style))
            checked-now (if (= checked is-checked)
                          checked
                          (do
                            (om/update! cursor [:checked] is-checked)
                            is-checked))
            basic-attrs {:type :checkbox
                         :checked checked-now}
            attrs (if disabled basic-attrs
                      (assoc basic-attrs
                        :on-change (partial input-change! on-change cursor)))]
        (html [tag attrs label])))))

(defn button-click!
  "button click"
  [callback cursor e]
  (if callback (callback cursor))
  (.preventDefault e)
  (.stopPropagation e))

(defn button-data [& {:keys [id style label disabled on-click] :as opts}]
  (-> {:e-type :button}
    (assoc-if :id id)
    (assoc-if :style style)
    (assoc-if :label label)
    (assoc-if :disabled disabled)
    (assoc-if :on-click on-click)))

(defn button [cursor owner {:keys [opts-id] :as opts}]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [label disabled on-click id style]} cursor
            id (or id (do (om/update! cursor [:id] opts-id) opts-id))
            style (or style "button")
            tag (keyword (str "button#" (name id) "." style))
            attrs {:disabled disabled
                   :on-click (partial button-click! on-click cursor)}]
        (html [tag attrs label])))))

(defn clickable-data [& {:keys [id style label disabled on-click on-page] :as opts}]
  (-> {:e-type :clickable}
    (assoc-if :id id)
    (assoc-if :style style)
    (assoc-if :label label)
    (assoc-if :disabled disabled)
    (assoc-if :on-click on-click)
    (assoc-if :on-page on-page)))

(defn clickable [cursor owner {:keys [opts-id] :as opts}]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [root-events (chan)
            on-page (:on-page cursor)]
        (sub (get-id-events) :root root-events)
        (go-loop []
          (let [{:keys [id page prev-page]} (<! root-events)]
            (when on-page
              (on-page cursor page))
            (recur)))))
    om/IDidMount
    (did-mount [_]
      (if quirks/mobile-safari?
        (js/clojurebridgemn.quirks.nodelay. (om/get-node owner))))
    om/IRender
    (render [_]
      (let [{:keys [label disabled on-click id style]} cursor
            id (or id (do (om/update! cursor [:id] opts-id) opts-id))
            style (if style (str "clickable." style) "clickable")
            tag (keyword (str "div#" (name id) "." style))
            attrs (if disabled
                    {}
                    {:on-click (partial button-click! on-click cursor)})
            label (if label
                    (if (string? label)
                      label
                      (vec (cons :span (om/value label)))))
            ]
        (html [tag attrs label])))))

(defn radio-data [& {:keys [id style label disabled value options on-change] :as opts}]
  (-> {:e-type :radio}
    (assoc-if :id id)
    (assoc-if :style style)
    (assoc-if :label label)
    (assoc-if :disabled disabled)
    (assoc-if :value value)
    (assoc-if :options options)
    (assoc :on-change
      (if on-change on-change
          (fn [cursor] (put-event! cursor))))))

;; Om radio component
(defn radio [cursor owner {:keys [opts-id] :as opts}]
  (reify
    om/IInitState
    (init-state [_]
      {:is-value (:value cursor)})
    om/IWillMount
    (will-mount [_]
      (let [{:keys [id]} cursor
            id (keyword (or id opts-id))
            radio-events (chan)]
        (sub (get-id-events) id radio-events)
        (go-loop []
          (let [{:keys [id value]} (<! radio-events)]
            ;; (println "radio EVENT" id "value:" value)
            (om/set-state! owner [:is-value] value)
            (recur)
            ))))
    om/IRenderState
    (render-state [_ {:keys [is-value]}]
      (let [{:keys [id style label disabled value options on-change]} cursor
            id (or id (do (om/update! cursor [:id] opts-id) opts-id))
            style (or style "radio")
            tag (keyword (str "div#" (name id) "." style))
            value-now (if (= value is-value)
                        value
                        (do
                          ;; (println "Fixing mismatch for" id)
                          (om/update! cursor [:value] is-value)
                          is-value))]
        (html [tag [:span (or label "")]
               (apply vector :ul
                 (for [i (range 0 (count options) 2)]
                   (let [v (nth options i)
                         radio-tag (keyword (str "input#" (name id) "_" (name v)))
                         checked (= value-now v)
                         basic-attrs {:type :radio
                                      :name id
                                      :value (name v)
                                      :checked checked
                                      :disabled disabled}
                         attrs (if disabled basic-attrs
                                   (assoc basic-attrs
                                     :on-change (partial input-change! on-change cursor)))
                         choice (nth options (inc i))
                         c (if (string? choice) choice
                               (om/value choice))
                         ]
                     [:li [radio-tag attrs] c])))])))))

(declare render-html-elements)

(defn element [cursor owner {:keys [opts-id] :as opts}]
  (reify om/IRender
    (render [_]
      (render-html-elements cursor opts-id))))

;; NOTE: Each element should have an unique :id
;; which will be used as the :react-key
;; FFI: https://github.com/omcljs/om/wiki/Internals:-Instances-are-getting-reused.-How%3F
(defn render-html-elements [cursor opts-id]
  (html
    (let [{:keys [e-order id style debug]} cursor
          id (or id (do (om/update! cursor [:id] opts-id) opts-id))
          style (or style "element")
          tag (keyword (str "div#" (name id) "." style))
          self (if debug
                 (if (string? debug)
                   [:span debug]
                   (om/value debug)))
          elements (if (pos? (count e-order))
                     (for [e e-order]
                       (let [data (get cursor e)
                             e-type (get data :e-type :element)
                             view (get @e-types e-type element)]
                         (om/build view data {:react-key e
                                              :opts {:opts-id e}}))))]
      (vec (cons tag (if self (cons self elements) elements))))))

(defn home-data []
  {:e-order [:intro :logos]
   :intro
   {:debug
    [:div.small.center "Free, beginner-friendly Clojure programming workshops for women"
     [:br]
     [:br]
     ;; [:i [:a {:href "https://www.eventbrite.com/e/clojurebridge-mn-fallwinter-2015-tickets-19159690149"} "Sign up now"]]
     ;;  " for our Fall/Winter workshop November 13-14!"
     ;; "Save the date! Our next ClojureBridgeMN workshop is scheduled for 11/13-14"
     "Stay tuned for our next ClojureBridgeMN workshop (tentatively scheduled for June 25-26)"
     ]
    }
   :logos
   {:debug
    [:div
     [:img.left {:alt "ClojureBridge"
                 :src "images/clojurebridge.png"}]
     [:img.right {:alt "Clojure" :src "images/clojure.png"}]]}
   })

(defn quotes-data []
  {:e-type :quotes
   :delay 19
   :n data/quotes-total
   :i (rand-int data/quotes-total)
   :checkbox :autoq})

(defn pictures-data []
  {:e-type :pictures
   :delay 29
   :n data/pictures-total
   :i (rand-int data/pictures-total)
   :checkbox :autop})

(defn header-data []
  {:e-order [:menu-ctrl :menu-icon :goleft :goright]
   :style "header"
   :menu-ctrl (clickable-data
                :style "dim"
                :on-click #(goto-page! :toggle-menu)
                :on-page (fn [cursor page]
                           (om/update! cursor [:style]
                             (if (= page :menu) "bright" "dim"))))
   :menu-icon (clickable-data
                :style "dim"
                :label (:home menu-icons)
                :on-page (fn [cursor page]
                           (om/update! cursor [:label]
                             (get menu-icons page))))
   :goleft (clickable-data
             :style "go.subtle"
             :on-click #(goto-uri :page -1)
             :on-page (fn [cursor page]
                        (let [new-style (if (contains? no-arrows-pages page)
                                          "go.dark" "go.subtle")]
                          (om/update! cursor [:style] new-style))))
   :goright (clickable-data
              :style "go.subtle"
              :on-click #(goto-uri :page +1)
              :on-page (fn [cursor page]
                         (let [new-style (if (contains? no-arrows-pages page)
                                           "go.dark" "go.subtle")]
                           (om/update! cursor [:style] new-style))))
   :page :home
   :prev-page nil})

(defn menu-item [page]
  (clickable-data
    :style "item"
    :label [(get menu-icons page) [:br] [:span.small (name page)]]
    :on-click #(goto-uri :page page)))

(defn elements-data []
  {:e-order [:header :pictures :quotes :home]
   :style "none"
   :header (header-data)
   :quotes (quotes-data)
   :pictures (pictures-data)
   :settings
   {;; NOTE some browsers do not support fullscreen
    :e-order (if (quirks/can-fullscreen?)
               [:b1 :b2 :b3 :fullscreen :commit]
               [:b1 :b2 :b3 :commit])
    :b1 {:e-order [:prevp :autop :nextp]
         :debug "Pictures "
         :prevp (button-data :label "⇐" :on-click
                  #(goto-uri :picture -1 :auto-pix false))
         :autop (checkbox-data
                  :label " auto "
                  :checked false
                  :on-change
                  (fn [cursor]
                    (let [{:keys [id checked]} cursor]
                      (goto-uri :auto-pix checked))))
         :nextp (button-data :label "⇒" :on-click
                  #(goto-uri :picture +1 :auto-pix false))}
    :b2 {:e-order [:prevq :autoq :nextq]
         :debug "Quotes "
         :prevq (button-data :label "⇐" :on-click
                  #(goto-uri :quote -1 :auto-quotes false))
         :autoq (checkbox-data
                  :label " auto "
                  :checked false
                  :on-change
                  (fn [cursor]
                    (let [{:keys [id checked]} cursor]
                      (goto-uri :auto-quotes checked))))
         :nextq (button-data :label "⇒" :on-click
                  #(goto-uri :quote +1 :auto-quotes false))}
    :b3 {:e-order [:size]
         :size (radio-data :label "Size"
                 :value :medium
                 :options [:small [:span.small {:on-click (partial set-size! :small)} "A"]
                           :medium [:span {:on-click (partial set-size! :medium)} "A"]
                           :large [:span.large {:on-click (partial set-size! :large)} "A"]]
                 :on-change (fn [cursor]
                              (set-size! (:value cursor))))
         }
    :fullscreen {:debug "Screen"
                 :e-order [:autof]
                 :autof (checkbox-data
                          :label " fullscreen"
                          :on-change
                          (fn [cursor]
                            (quirks/set-fullscreen! (:checked cursor))
                            (put-event! cursor)))}
    :commit {:style "small.dim"
             :debug ""}
    }
   :menu
   {:e-order [:m1 :m2 :m3 :m4 :m5 :m6 :m7 :m8 :m9 :clear]
    :m1 (menu-item :home)
    :m2 (menu-item :sponsors)
    :m3 (menu-item :donations)
    :m4 (menu-item :blog)
    :m5 (menu-item :calendar)
    :m6 (menu-item :links)
    :m7 (menu-item :board)
    :m8 (menu-item :settings)
    :m9 (menu-item :pix+quotes)
    :clear {:style "clear"}
    }
   :home (home-data)
   :sponsors
   {:debug
    [:div "ClojureBridge has been made possible locally through the generous support of our sponsors and individual donors"
     [:ul
      [:li "November 13-14 2015: Vidku, Harbinger Partners" [:br]
       [:img {:alt "Vidku"
              :src "images/sponsors/vidku_400x400.png"}]
       [:img {:alt "Harbinger Partners"
              :src "images/sponsors/HP.png"}]]
      [:li "September 11-12 2015: Vidku" [:br]
       [:img {:alt "Vidku"
              :src "images/sponsors/vidku_400x400.png"}]]
      [:li "June 26-27 2015: BridgeFoundry, Clockwork, O'Reilly" [:br]
       [:img {:alt "Bridge Foundry"
              :src "images/clojurebridge.png"}]
       [:img {:alt "Clockwork"
              :src "images/sponsors/cw_logo.png"}]
       [:a {:href "http://www.oreilly.com/pub/cpc/9557"}
        [:img {:alt "O'Reilly"
               :src "http://www.oreilly.com/partner_file/ORM_logo_box75_hex.jpg"}]]]
      [:li "March 6-7 2015: UMSEC, Trivent, Adobe, O'Reilly"  [:br]
       [:img {:alt "UMSEC"
              :src "images/sponsors/umseclogo.jpg"}]
       [:img {:alt "Thrivent"
              :src "images/sponsors/Thrivent.png"}]
       [:img {:alt "Adobe"
              :src "images/sponsors/Adobe.png"}]
       [:a {:href "http://www.oreilly.com/pub/cpc/9557"}
        [:img {:alt "O'Reilly"
               :src "http://www.oreilly.com/partner_file/ORM_logo_box75_hex.jpg"}]]]
      [:li
       "May 16-17 2014: DevJam Studios, Code 42, Brick Alloy, Lispcast" [:br]
       [:img {:alt "DevJam Studios"
              :src "images/sponsors/DevJam-Studios.png"}]
       [:img {:alt "Code 42"
              :src "images/sponsors/Code42.png"}]
       [:img {:alt "Brick Alloy"
              :src "images/sponsors/BrickAlloy.png"}]
       [:img {:alt "Lispcast"
              :src "images/sponsors/Lispcast.png"}]]
      ]
     ]
    }
   :donations
   {:debug
    [:div "Our ClojureBridge efforts are part of the national "
     [:a {:href "http://clojurebridge.org"} "ClojureBridge"]
     " organization which benefits from 501(c)3 non-profit status (via affiliation with School Factory)"
     [:br] [:br]
     "To learn more about corporate sponsorship opportunities please send e-mail to info@ClojureBridgeMN.org. You can also donate "
     [:i "right now"] " via PayPay here:"
     [:form {:action "https://www.paypal.com/cgi-bin/webscr"
             :method "post"
             :target="_top"}
      [:input {:type "hidden"
               :name "cmd"
               :value "_s-xclick"}]
      [:input {:type "hidden"
               :name "hosted_button_id"
               :value "HW3JXRX3FADY8"}]
      [:input.paypal {:type "image"
                      :src "https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif"
                      :border "0"
                      :name "submit"
                      :alt "PayPal - The safer, easier way to pay online!"}]
      [:img {:alt ""
             :border "0"
             :src "https://www.paypalobjects.com/en_US/i/scr/pixel.gif"
             :width "1"
             :height "1"}]]
     ]
    }
   :blog
   {:debug
    [:div
     [:br]
     ;; [:i [:a {:href "https://www.eventbrite.com/e/clojurebridge-mn-fallwinter-2015-tickets-19159690149"} "Sign up now"]]
     ;;  " for our Fall/Winter workshop November 13-14!"
     ;; "Save the date! Our next ClojureBridgeMN workshop is scheduled for 11/13-14"
     ;; "Stay tuned for more information about our upcoming workshop at "
     ;; [:a {:href "http://vidku.com"} "Vidku"]
     ;; " on September 11-12!"
     "Stay tuned for our next ClojureBridgeMN workshop (tentatively scheduled for June 25-26)"
     [:br]
     [:br]
     "Want to connect with the local Clojure community?"
     [:br]
     [:br]
     "Join us for our next "
     [:a {:href "http://www.meetup.com/clojuremn/"} "clojure.mn"]
     " meeting on April 13th!"
     [:br]
     [:br]
     ]
    }
   :calendar
   {:debug
    [:div
     "ClojureBridgeMN Events Calendar"
     [:br]
     [:iframe {:src "https://www.google.com/calendar/embed?src=clojurebridgemn%40gmail.com&ctz=America/Chicago"}]
     ]
    }
   :pix+quotes
   {;; :debug "blank page to view pictures and quotes"
    }
   :links
   {:debug
    [:div
     [:br]
     "Clojure related resources"
     [:br]
     [:br]
     [:ul
      [:li "Global " [:a {:href "http://clojurebridge.org"} "ClojureBridge"] " organization"]
      [:li "Contact us at info@ClojureBridgeMN.org"]
      [:li "Local " [:a {:href "http://www.meetup.com/clojuremn/"} "clojure.mn"] " user group (meets every 2nd Wednesday)"]
      [:li "ClojureBridge "
       [:a {:href "http://bit.ly/cb-mn"} "installfest"] " and "
       [:a {:href "https://github.com/ClojureBridge/getting-started/blob/master/nightcode.md"} "Nightcode"]
       " guides"]
      [:li "Our " [:a {:href "https://github.com/clojurebridge-minneapolis/track1-chatter"} "Track 1"] " guide"]
      [:li "Our " [:a {:href "https://github.com/clojurebridge-minneapolis/track2-functional"} "Track 2"] " guide"]
      ]]
    }
   :board
   {:debug
    [:div
     [:br]
     "Meet the ClojureBridgeMN Board"
     [:br]
     [:br]
     [:div [:img {:alt "Annie" :src "images/board/annie.png"}]
      [:div "Annie Engmark is new to coding -- Clojure is her first exposure to all things code. She enjoys working with passionate and progressive individuals and has found ClojureBridge is a great place to facilitate just that. When she's not racking her brain to learn code, she is most likely to be found spending time with her friends and family, exploring Minneapolis' nature scene, or running around completing random projects she started months before."]]
     [:div [:img {:alt "Antoinette" :src "images/board/antoinette.jpg"}]
      [:div "Antoinette Smith is a fan of concise, yet understandable technical documentation and also, equality for all people. Antoinette is also most likely to send you an e-mail about ClojureBridge. She posts on Twitter as "
      [:a {:href "http://twitter.com/ant_auth"} "@ant_auth"]
       ", all opinions are hers."]]
     [:div [:img {:alt "Chris" :src "images/board/chris.jpg"}]
      [:div "Chris Koehnen is an old lisp enthusiast.  He likes programming and coding and writing software, despite being a professional developer."]]
     [:div [:img {:alt "Millie" :src "images/board/millie.jpg"}]
      [:div "Millicent Walsh is a programming artist or an artistic programer depending on the day. She also enjoys human and computer languages, travel, Spain, international news, dancing, yoga, tango, flamenco, gardening, cooking, wine, food, organizing people, and making ideas reality. She is a ClojureBridge logistics guru, frequent track two student, and networker extraordinaire. She sometimes tweets at "
       [:a {:href "http://twitter.com/mh_walsh"} "@mh_walsh"] " ."]]
     [:div [:img {:alt "Nicky" :src "images/board/nicky.jpg"}]
      [:div "Nicky Stein-Grohs had previously forgotten how to code but is now learning Clojure and is enjoying being on the board of such a welcoming organization. She loves technology, people, food, and tiny dogs. She participates in various types of dance, aerial arts, and yoga. When she’s feeling sassy she tweets over at "
      [:a {:href "http://twitter.com/formica_dinette"} "@formica_dinette"]
       " ."]]
     [:div [:img {:alt "Tom" :src "images/board/tmarble.jpg"}]
      [:div "Tom Marble is passionate about Free/Libre Open Source Software, FLOSS legal issues and increasing diversity. He enjoys being a hardware and software maker. Often tweets cryptic geek things at "
       [:a {:href "http://twitter.com/tmarble"} "@tmarble"] " ."]]]}
   })

(defn app [cursor owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [root-events (chan)]
        (sub (get-id-events) :root root-events)
        (go-loop []
          (let [{:keys [id page prev-page]} (<! root-events)]
            (om/transact! cursor [:elements]
              #(-> %
                 (assoc-in [:header :prev-page] prev-page)
                 (assoc-in [:header :page] page)
                 (assoc-in [:e-order] [:header :pictures :quotes page])))
            (set-subdued! (contains? subdued-pages page))
            (recur)))))
    om/IRender
    (render [_]
      (render-html-elements cursor :root))))

;; initialization =============================

;; add element type
(defn add-e-type! [e-type view]
  (swap! e-types assoc e-type view))

;; add element at ks
(defn add-e! [ks data]
  (if (pos? (count ks))
    (let [parents (vec (butlast ks))
          parent-e-order (conj parents :e-order)
          k (last ks)]
      (swap! app-state update-in parent-e-order conj k)
      (swap! app-state assoc-in ks data)
      )))

(defn initialize
  "Called to initialize the app when the DOM is ready or to re-initialize during development"
  []
  (let [first-initialization? (nil? @app-state)
        prev-size (get-in @app-state [:style])
        prev-base-url (or (get-in @app-state [:base-url]) (make-url))
        events (chan)
        buf-fn (fn [id]
                 (case id
                   :root (buffer 6)
                   (buffer 4)))
        id-events (pub events :id buf-fn)
        prev-pg (get-in @app-state [:elements :header :page])
        prev-picture (str "picture-" (get-auto :pictures))
        prev-quote (str "quote-" (get-auto :quotes))
        prev-auto-pix (get-auto-pix)
        prev-auto-quotes (get-auto-quotes)]
    (println "program-mode:" program-mode)
    (println "user-agent:" quirks/user-agent)
    (stop-auto! :pictures)
    (stop-auto! :quotes)
    (reset! app-state
      {:e-order []
       :channels {:events events
                  :id-events id-events
                  :autop [] ;; hack to handle put-event! before checkbox mounted
                  :autoq []}
       :base-url prev-base-url})
    (reset! e-types nil)
    (add-e-type! :element element)
    (add-e-type! :quotes quotes)
    (add-e-type! :pictures pictures)
    (add-e-type! :button button)
    (add-e-type! :checkbox checkbox)
    (add-e-type! :radio radio)
    (add-e-type! :clickable clickable)
    (add-e! [:elements] (elements-data))
    (om/root app app-state {:target (gdom/getElement "app")})
    ;; NOTE no longer starting auto here.. see defroute "/" above
    ;; (start-auto! :pictures)
    ;; (start-auto! :quotes)
    (set-size! (or prev-size :medium))
    (quirks/initialize-swipe #(goto-uri :page +1) #(goto-uri :page -1))
    ;; ask the server for last commit info, then update the settings page
    (server/info-commit
      #(swap! app-state (fn [a]
                          (assoc-in a [:elements :settings :commit :debug]
                            [:span [:i "website last updated by "]
                             %1 [:i " at "] %2]))))
    (if first-initialization?
      (start-secretary)
      (goto-uri :page prev-pg :picture prev-picture :quotes prev-quote
        :auto-pix prev-auto-pix :auto-quotes prev-auto-quotes))
    ))

(if-not quirks/phantomjs?
  (set! (.-onload js/window) initialize))

(defn figwheel-reload []
  (println "Figwheel reload...")
  (initialize))
