(ns ai-dj.core
  (:require [reagent.core :as r]
            [reagent.dom.client :as domc]
            [oops.core :refer [ocall]]))

(goog-define BACKEND_URL "ws://localhost:3000/ws")
;; ------------------------------
;; State

(defonce app-state
  (r/atom {:queue []         ;; [{:id "abc", :title "...", :artist "..."} ...]
           :current nil      ;; current playing track
           :ws nil           ;; WebSocket conn
           :input ""  ;; prompt input
           :yt-ids {}    ;; youtube track ids
           }))

;; ------------------------------
;; WebSocket

(defn send! [msg]
  (when-let [ws (:ws @app-state)]
    (.send ws (js/JSON.stringify (clj->js msg)))))

(defn connect! []
  (let [ws (js/WebSocket. (or BACKEND_URL
                              (str (if (= js/location.protocol "https:") "wss:" "ws:")
                                   js/location.hostname
                                   js/location.port
                                   "/ws")))]
    (set! (.-onmessage ws)
          (fn [e]
            (let [msg (js->clj (js/JSON.parse (.-data e)) :keywordize-keys true)]
              ;; (js/console.log "msg:" msg)
              (case (:type msg)
                "playlist" (let [{:keys [commentary songs]} msg]
                             (swap! app-state assoc
                                    :queue songs
                                    :commentary commentary
                                    :loading false))
                "queue"     (let [queue (:queue msg)]
                              (swap! app-state assoc :queue queue))
                "yt-id"      (swap! app-state update :yt-ids assoc (:id msg) (:track-id msg))
                "comment"   (js/console.log "Commentary:" (:text msg))
                (js/console.warn "Unhandled msg:" msg)))))

    (set! (.-onopen ws)
          #(js/console.log "WebSocket connected"))

    (swap! app-state assoc :ws ws)))

(defn send-prompt! []
  (when-let [prompt (:input @app-state)]
    (swap! app-state assoc :loading true)
    (send! {:type "prompt" :text prompt})))

(defn next-track! []
  (swap! app-state update :queue rest))
;; ------------------------------
;; Components

(defn prompt-box []
  [:form {:on-submit (fn [e]
                       (.preventDefault e)
                       (send-prompt!))}
   [:div.prompt-bar
    [:input {:type "text"
             :placeholder "Try: upbeat latino 80s"
             :value (:input @app-state)
             :on-change #(swap! app-state assoc :input (.. % -target -value))}]
    (when (:loading @app-state)
      [:div.spinner])
    [:button "Play"]]])

(defn quote []
  (when-let [commentary (:commentary @app-state)]
    [:h2.commentary-box commentary]))

(defn player []
  [:div#player])

(defn player-controls []
  [:div.controls
   [:button {:on-click next-track!} "⏭ Skip (n)"]])

(defn playing-track []
  (if-let [track (some-> @app-state :queue first)]
    [:div (:title track) " - " (:artist track)]
    [:div]))

(defn queue-list []
  (let [queue (rest (:queue @app-state))]
    (when (seq queue)
      [:div.track-info
       [:h2 "Up Next:"]
       (for [{:keys [id title artist]} queue]
         ^{:key id} [:p "• " title " - " artist])])))

(defn current-track []
  (when-let [current (some-> @app-state :queue first)]
    (or (:youtube_id current)
        (some-> @app-state :yt-ids (get (:id current))))))

;; ---------- player ---------------
(defonce yt-player (r/atom nil))

(defn play-next [player]
  (when-let [t @(r/track current-track)]
    (js/console.log "track changed: " t)
    (ocall player "loadVideoById" t)))

(defn on-player-state-change [event]
  (let [state (.-data event)]
    (js/console.log "Player state changed to:" state (= state js/YT.PlayerState.UNSTARTED))
    (case state
      -1 ;;js/YT.PlayerState.UNSTARTED
      (do
        (js/console.log "starting...")
        (ocall (.-target event) "playVideo"))

      0 ;; js/YT.PlayerState.ENDED (let [[x & xs] (list 1 2 3)] xs) (seq [])
      (do
        (js/console.log "▶️ Track ended, trigger next or commentary")
        (next-track!))

      1 ;; js/YT.PlayerState.PLAYING
      (do
        (js/console.log "🎵 Track started")
        (let [{:keys [yt-ids queue]} @app-state
              next (some #(when-not (or (:youtube_id %) (yt-ids (:id %))) %) queue)]
          (when next
            (send! {:type "track"
                    :id (:id next)
                    :query (str (:title next) " by " (:artist next))}))))

      (js/console.log "other state:" state))))

(defn on-player-ready [event]
  (js/console.log "Player ready")
  (r/track! play-next (.-target event)))

(defn on-yt-ready []
  (js/console.log "on-yt-ready")
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
   [:h1 "🎧 AI DJ"]
   [prompt-box]
   [quote]
   [playing-track]
   [player]
   [queue-list]])

;; ------------------------------
;; Keybindings

(defn handle-keydown [e]
  (let [tag (.. e -target -tagName)]
    (when (and (= (.-key e) "n") (not (#{"INPUT" "TEXTAREA"} tag)))
      (js/console.log "next track via keyboard")
      (play-next @yt-player)
      (next-track!))))

;; ------------------------------
;; Entry
(defonce root
  (delay
    (domc/create-root (.getElementById js/document "app"))))

(defn ^:dev/after-load start []
  (js/console.log "start...")
  (domc/render @root [app])
  (when (some? @yt-player)
    (.destroy @yt-player)
    (on-yt-ready))
  (.addEventListener js/document "keydown" handle-keydown))

(defn ^:export init []
  (js/console.log "init...")
  (connect!)
  (start)
  (init-yt-api))
