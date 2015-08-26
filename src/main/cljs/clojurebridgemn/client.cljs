;; ClojureBridgeMN.org
;; Copyright © 2015 Tom Marble
;; Licensed under the MIT license
;; https://github.com/clojurebridge-minneapolis/clojurebridgemn.org

(ns ^:figwheel-always clojurebridgemn.client
    (:require-macros [cljs.core.async.macros :refer [go go-loop]])
    (:require [clojure.set :refer [difference]]
              [cljs.pprint :refer [pprint]]
              [cljs.core.async :as async
               :refer [<! >! chan pub put! sub take! timeout sliding-buffer dropping-buffer]]
              [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [goog.string :as gstring]
              [goog.dom :as gdom]
              [sablono.core :as html :refer-macros [html]]
              ;; secretary not used yet
              ;; [secretary.core :as secretary :refer-macros [defroute]]
              ;; [goog.events :as events]
              ;; [goog.history.EventType :as EventType]
              [cljs-http.client :as http]
              [clojurebridgemn.data :as data]
              [clojurebridgemn.utils :refer [remove-fn assoc-if vec-index-of program-mode development?]]
              [clojurebridgemn.server :as server]
              [clojurebridgemn.quirks :as quirks])
    (:import goog.History))

(enable-console-print!)

;; secretary not used yet

;; (secretary/set-config! :prefix "#")

;; (defroute page-route "/:page" [page query-params]
;;   (println "PAGE" page "QUERY" (pr-str query-params)))

;; (let [h (History.)]
;;   (events/listen h EventType/NAVIGATE #(secretary/dispatch! (.-token %)))
;;   (doto h (.setEnabled true)))

;; Om application ===========================

(defonce app-state (atom nil))
(defonce e-types (atom nil))

(def menu-icons
  {:home "⌂"
   :sponsors "$"
   :donations "✋"
   :blog "✍"
   :calendar "▦"
   :links "▻"
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
        ;; _ (println "GOTO" page "current-page" current-page "prev-page" prev-page)
        new-page (if (= page :toggle-menu)
                   (if (= current-page :menu) prev-page :menu)
                   page)]
    ;; (println "goto-page!" new-page "prev-page" current-page
    (put-event! {:id :root :page new-page :prev-page current-page})
    ))

(defn prev-page! []
  (let [page (get-in @app-state [:elements :header :page])
        n (count swipe-pages)
        i (dec (vec-index-of swipe-pages page))
        i (if (< i 0) (dec n) i)
        new-page (nth swipe-pages i)]
    (goto-page! new-page)))

(defn next-page! []
  (let [page (get-in @app-state [:elements :header :page])
        n (count swipe-pages)
        i (inc (vec-index-of swipe-pages page))
        i (if (>= i n) 0 i)
        new-page (nth swipe-pages i)]
    (goto-page! new-page)))

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

;; set the current thing :i for k
(defn set-auto! [k i]
  (let [n (get-in @app-state [:elements k :n])
        i (if (>= i n) 0 (if (< i 0) (dec n) i))]
    ;; (println "auto" k "i:" i)
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
      ;; (println "stopped auto" k "[" auto-id "]"))
      (if (and (not restart) checkbox) ;; tell checkbox STOP
        (put-event! {:id checkbox :checked false})))))

(declare next-auto!)

;; start auto advancing for k
;; if continue then restart timer
(defn start-auto! [k & [continue]]
  (let [component (get-in @app-state [:elements k])
        {:keys [auto-id delay checkbox]} component]
    (if (and auto-id (not continue))
      (stop-auto! k true))
    (let [auto-id (js/setTimeout #(next-auto! k) (* delay 1000))]
      (swap! app-state assoc-in [:elements k :auto-id] auto-id)
      ;; (println "started auto" k "[" auto-id "]")
      (if (and (not continue) checkbox) ;; tell checkbox START
        (put-event! {:id checkbox :checked true})))))

(defn toggle-auto! [k & [e]]
  (let [component (get-in @app-state [:elements k])
        {:keys [auto-id]} component]
    (if auto-id
      (stop-auto! k)
      (start-auto! k))
    (when e
      (.preventDefault e)
      (.stopPropagation e))))

;; ask to advance to the next thing
;; if stop then STOP auto advancing after you move (button)
;; else restart the timer (automatic)
(defn next-auto! [k & [stop]]
  (if stop
    (stop-auto! k)
    (start-auto! k true)) ;; new setTimeout
  (set-auto! k (inc (get-auto k))))

;; ask to return to the previous thing
;; if stop then STOP auto advancing after you move (button)
(defn prev-auto! [k & [stop]]
  (if stop (stop-auto! k))
  (set-auto! k (dec (get-auto k))))

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
      {:is-checked (:checked cursor)})
    om/IWillMount
    (will-mount [_]
      (let [{:keys [id]} cursor
            id (keyword (or id opts-id))
            checkbox-events (chan)]
        (sub (get-id-events) id checkbox-events)
        (go-loop []
          (let [{:keys [id checked]} (<! checkbox-events)]
            ;; (println "checkbox EVENT" id "checked:" checked)
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
                            ;; (println "Fixing mismatch for" id)
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
  ;; (println "button-click! for:" (:label cursor))
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
              ;; (println "clickable EVENT" id "page" page "prev-page" prev-page)
              (on-page cursor page))
            ;; (om/set-state! owner [:page] page)
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
     [:i [:a {:href "http://www.eventbrite.com/e/clojurebridge-mn-fall-2015-tickets-18237438670"} "Sign up now"]]
     " for our fall workshop September 11-12!"
     ;; "Save the date! Our next ClojureBridgeMN workshop is scheduled for 9/11-12 !"
     ]}
   :logos
   {:debug
    [:div
     [:img.left {:alt "ClojureBridge"
                 :src "images/clojurebridge.png"}]
     [:img.right {:alt "Clojure" :src "images/clojure.png"}]]}
   })

(defn quotes-data []
  {:e-type :quotes
   :delay 10
   :n data/quotes-total
   :i (rand-int data/quotes-total)
   :checkbox :autoq})

(defn pictures-data []
  {:e-type :pictures
   :delay 25
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
             :on-click #(prev-page!)
             :on-page (fn [cursor page]
                        (let [new-style (if (contains? no-arrows-pages page)
                                          "go.dark" "go.subtle")]
                          (om/update! cursor [:style] new-style))))
   :goright (clickable-data
              :style "go.subtle"
              :on-click #(next-page!)
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
    :on-click #(goto-page! page)))

(defn elements-data []
  {:e-order [:header :pictures :quotes :home]
   :style "none"
   :header (header-data)
   :quotes (quotes-data)
   :pictures (pictures-data)
   :settings
   {;; :debug "add note about bookmarks"
    ;; NOTE some browsers do not support fullscreen
    :e-order (if (quirks/can-fullscreen?)
               [:b1 :b2 :b3 :fullscreen :commit]
               [:b1 :b2 :b3 :commit])
    :b1 {:e-order [:prevp :autop :nextp]
         :debug "Pictures "
         :prevp (button-data :label "⇐" :on-click #(prev-auto! :pictures true))
         :autop (checkbox-data
                  :label " auto "
                  :checked true
                  :on-change
                  (fn [cursor]
                    (let [{:keys [id checked]} cursor]
                      (if checked (start-auto! :pictures) (stop-auto! :pictures)))))
         :nextp (button-data :label "⇒" :on-click #(next-auto! :pictures true))}
    :b2 {:e-order [:prevq :autoq :nextq]
         :debug "Quotes "
         :prevq (button-data :label "⇐" :on-click #(prev-auto! :quotes true))
         :autoq (checkbox-data
                  :label " auto "
                  :checked true
                  :on-change
                  (fn [cursor]
                    (let [{:keys [id checked]} cursor]
                      (if checked (start-auto! :quotes) (stop-auto! :quotes)))))
         :nextq (button-data :label "⇒" :on-click #(next-auto! :quotes true))}
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
                            ;; (quirks/toggle-fullscreen!)))}
                            (quirks/set-fullscreen! (:checked cursor))))}
    :commit {:style "small.dim"
             :debug ""}
    }
   :menu
   {:e-order [:m1 :m2 :m3 :m4 :m5 :m6 :m7 :m8 :clear]
    :m1 (menu-item :home)
    :m2 (menu-item :sponsors)
    :m3 (menu-item :donations)
    :m4 (menu-item :blog)
    :m5 (menu-item :calendar)
    :m6 (menu-item :links)
    :m7 (menu-item :settings)
    :m8 (menu-item :pix+quotes)
    :clear {:style "clear"}
    }
   :home (home-data)
   :sponsors
   {:debug
    [:div "ClojureBridge has been made possible locally through the generous support of our sponsors and individual donors"
     [:ul
      [:li "September 11-12 2015: Vidku"
       [:br]
       [:img {:alt "Vidku"
              :src "images/sponsors/vidku_400x400.png"
              }]
       ]
      [:li "June 26-27 2015: BridgeFoundry, Clockwork, O'Reilly"
       [:br]
       [:img {:alt "Bridge Foundry"
              :src "images/clojurebridge.png"
              }]
       [:img {:alt "Clockwork"
              :src "images/sponsors/cw_logo.png"
              }]
       [:img {:alt "O'Reilly"
              :src "images/sponsors/ORM.jpg"
              }]
       ]
      [:li "March 6-7 2015: UMSEC, Trivent, Adobe, O'Reilly"
       [:br]
       [:img {:alt "UMSEC"
              :src "images/sponsors/umseclogo.jpg"
              }]
       [:img {:alt "Thrivent"
              :src "images/sponsors/Thrivent.png"
              }]
       [:img {:alt "Adobe"
              :src "images/sponsors/Adobe.png"
              }]
       [:img {:alt "O'Reilly"
              :src "images/sponsors/ORM.jpg"
              }]
       ]
      [:li
       "May 16-17 2014: DevJam Studios, Code 42, Brick Alloy, Lispcast"
       [:br]
       [:img {:alt "DevJam Studios"
              :src "images/sponsors/DevJam-Studios.png"
              }]
       [:img {:alt "Code 42"
              :src "images/sponsors/Code42.png"
              }]
       [:img {:alt "Brick Alloy"
              :src "images/sponsors/BrickAlloy.png"
              }]
       [:img {:alt "Lispcast"
              :src "images/sponsors/Lispcast.png"
              }]
       ]
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
     "Stay tuned for more information about our upcoming workshop at "
     [:a {:href "http://vidku.com"} "Vidku"]
     " on September 11-12!"
     [:br]
     [:br]
     "Want to connect with the local Clojure community?"
     [:br]
     [:br]
     "Join us for our next "
     [:a {:href "http://clojure.mn/"} "clojure.mn"]
     " meeting on September 9th!"
     [:br]
     [:br]
     ;; "Save the date! Our next ClojureBridgeMN workshop is scheduled for 9/11-12"
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
   {;; :debug "TBD just blank page for quotes"
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
      [:li "Local " [:a {:href "http://clojure.mn"} "clojure.mn"] " user group (meets every 2nd Wednesday)"]
      [:li "ClojureBridge "
       [:a {:href "http://bit.ly/cb-mn"} "installfest"] " and "
       [:a {:href "https://github.com/ClojureBridge/getting-started/blob/master/nightcode.md"} "Nightcode"]
       " guides"]
      [:li "Our " [:a {:href "https://github.com/clojurebridge-minneapolis/track1-chatter"} "Track 1"] " guide"]
      [:li "Our " [:a {:href "https://github.com/clojurebridge-minneapolis/track2-functional"} "Track 2"] " guide"]
      ]]
    }
   })

(defn app [cursor owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (go (let [response (<! (http/get "https://api.github.com/users" {:with-credentials? false}))]
      (prn (:status response))
      (prn (map :login (:body response)))))
      (let [root-events (chan)]
        (sub (get-id-events) :root root-events)
        (go-loop []
          (let [{:keys [id page prev-page]} (<! root-events)]
            ;; (println "app EVENT" id "page" page "prev-page" prev-page)
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
  (let [prev-size (get-in @app-state [:style])
        events (chan)
        buf-fn (fn [id]
                 (case id
                   :root (dropping-buffer 4)
                   (sliding-buffer 3)))
        id-events (pub events :id buf-fn)]
    (println "program-mode:" program-mode)
    (println "user-agent:" quirks/user-agent)
    (stop-auto! :pictures)
    (stop-auto! :quotes)
    (reset! app-state
      {:e-order []
       :channels {:events events :id-events id-events}})
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
    (start-auto! :pictures)
    (start-auto! :quotes)
    (set-size! (or prev-size :medium))
    (quirks/initialize-swipe next-page! prev-page!)
    ;; ask the server for last commit info, then update the settings page
    (server/info-commit
      #(swap! app-state (fn [a]
                          (assoc-in a [:elements :settings :commit :debug]
                            [:span [:i "website last updated by "]
                                   %1 [:i " at "] %2]))))
    ))

;; Normally we'll call initialize as soon as the JavaScript
;; window.onload function fires... For testing with PhantomJS however
;; we'll call initialize from testing/runner.cljs
(if-not quirks/phantomjs?
  (set! (.-onload js/window) initialize))

;; If we are in :dev mode we will re-initialize automatically on reload
(when (and (development?) (gdom/getElement "app"))
  (initialize))
