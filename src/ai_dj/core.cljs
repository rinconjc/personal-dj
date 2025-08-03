(ns ai-dj.core
  (:require [reagent.core :as r]
            [reagent.dom.client :as domc]
            [oops.core :refer [ocall]]))

;; ------------------------------
;; State

(defonce app-state
  (r/atom {:queue []         ;; [{:id "abc", :title "...", :artist "..."} ...]
           :current nil      ;; current playing track
           :ws nil           ;; WebSocket conn
           :input ""}))      ;; prompt input

;; ------------------------------
;; WebSocket

(defn connect! []
  (let [ws (js/WebSocket. "ws://localhost:3000/ws")]
    (set! (.-onmessage ws)
          (fn [e]
            (let [msg (js/JSON.parse (.-data e))]
              (js/console.log "msg:" msg)
              (case (.-type msg)
                "queue"     (swap! app-state assoc :queue (js->clj (.-queue msg) :keywordize-keys true))
                "play"      (swap! app-state assoc :current (.-current msg))
                "comment"   (js/console.log "Commentary:" (.-text msg))
                (js/console.warn "Unhandled msg:" msg)))))

    (set! (.-onopen ws)
          #(js/console.log "WebSocket connected"))

    (swap! app-state assoc :ws ws)))

(defn send! [msg]
  (when-let [ws (:ws @app-state)]
    (.send ws (js/JSON.stringify (clj->js msg)))))

;; ------------------------------
;; Components

(defn prompt-box []
  [:div.prompt-bar
   [:input {:type "text"
            :placeholder "Try: upbeat latino 80s"
            :value (:input @app-state)
            :class "border p-2 flex-1"
            :on-change #(swap! app-state assoc :input (.. % -target -value))}]
   [:button {:on-click #(do
                          (send! {:type "prompt"
                                  :text (:input @app-state)})
                          (swap! app-state assoc :input ""))
             :class "bg-blue-500 text-white px-4 py-2 rounded"}
    "Play"]])

(defn player []
  [:div#player])

(defn player-v1 []
  (let [{:keys [current queue]} @app-state]
    (when-let [current (and current (seq queue) (nth queue current))]
      [:div
       [:h2.text-xl (:title current)]
       [:iframe {:width "560"
                 :height "315"
                 :src (str "https://www.youtube.com/embed/" (:id current) "?autoplay=1")
                 :frameBorder "0"
                 :allow "autoplay; encrypted-media"
                 :allowFullScreen true}]])))

(defn queue-list []
  (let [queue (:queue @app-state)]
    (when (seq queue)
      [:div.track-info
       [:h2 "Up Next:"]
       (for [{:keys [id title]} queue]
         ^{:key id} [:p (str "• " title)])])))

(defn current-track []
  (js/console.log "current-track")
  (let [{:keys [current queue]} @app-state]
    (when current
      (some-> queue (nth current) :id))))

;; ---------- player ---------------
(defonce yt-player (r/atom nil))

(defn on-player-state-change [event]
  (let [state (.-data event)]
    (js/console.log "Player state changed to:" state (= state js/YT.PlayerState.UNSTARTED))
    (case state
      -1 ;;js/YT.PlayerState.UNSTARTED
      (do
        (js/console.log "starting...")
        (ocall (.-target event) "playVideo"))

      0 ;; js/YT.PlayerState.ENDED
      (js/console.log "▶️ Track ended, trigger next or commentary")

      1 ;; js/YT.PlayerState.PLAYING
      (js/console.log "🎵 Track started")

      (js/console.log "other state:" state))))

(defn play-next [player]
  (when-let [t @(r/track current-track)]
    (js/console.log "track changed: " t)
    (ocall player "loadVideoById" t)))

(defn on-player-ready [event]
  (js/console.log "Player ready")
  (r/track! play-next (.-target event)))

(defn on-yt-ready []
  (reset! yt-player
          (js/YT.Player.
           "player"  ;; ID of the div
           #js {:height "390"
                :width "640"
                ;; :videoId "none"  ;; initial video ID
                :events #js {:onReady on-player-ready
                             :onStateChange on-player-state-change}}))
  ;; (r/track! play-next)
  )

(defn init-yt-api []
  (set! js/onYouTubeIframeAPIReady on-yt-ready))

;; ------------------------------
;; Main UI

(defn app []
  [:div.app-container
   [:h1.text-2xl.font-bold "🎧 AI DJ"]
   [prompt-box]
   [player]
   [queue-list]])

;; ------------------------------
;; Entry
(defonce root
  (delay
    (domc/create-root (.getElementById js/document "app"))))

(defn ^:dev/after-load start []
  (js/console.log "start...")
  (domc/render @root [app]))

(defn ^:export init []
  (js/console.log "init...")
  (connect!)
  (start)
  (init-yt-api))
