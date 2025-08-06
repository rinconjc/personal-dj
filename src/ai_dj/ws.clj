(ns ai-dj.ws
  (:require
   [ai-dj.ai :as ai]
   [ai-dj.spotify :as spotify]
   [ai-dj.yt :as yt]
   [cheshire.core :as json]
   [clojure.tools.logging :as log]
   [org.httpkit.server :as http]))

(def clients (atom #{}))
(def queue (atom []))
(def current (atom 0))

(defn broadcast! [msg]
  (let [data (json/generate-string msg)]
    (doseq [ch @clients] (http/send! ch data))))

(defn send-commentary []
  (when-let [tk (@queue @current)]
    (let [cmt (ai/make-commentary tk)]
      (broadcast! {:type "commentary" :songId (:id tk) :text cmt}))))

(defn serve-track! [ch id query]
  (let [tracks (yt/search-videos query 1)]
    (http/send! ch (json/generate-string {:type "yt-id" :id id :track-id (-> tracks first :id)}))))

(defn send-queue! [ch prompt]
  (let [tracks (spotify/search-tracks prompt)
        first (first tracks)]
    (http/send! ch (json/generate-string {:type "queue" :queue tracks}))
    (when first
      (serve-track! ch  (:id first) (str (:title first) " by " (:artist first))))
    ;; (future (Thread/sleep 5000) (send-commentary))
    ))

(defn handle-ws [req]
  (http/as-channel
   req
   {:on-open
    (fn [ch]
      (log/info "channel open")
      (swap! clients conj ch)
      (http/send! ch (json/generate-string {:type "queue" :queue @queue})))
    :on-close
    (fn [ch status]
      (log/info "channel closed:" status)
      (swap! clients disj ch))
    :on-receive
    (fn [ch data]
      (log/info "req:" data)
      (let [{:keys [type] :as msg} (json/parse-string data true)]
        (case type
          "trackended"
          (do (swap! current inc) (send-commentary)
              (broadcast! {:type "next" :current @current}))
          "prompt"
          (do
            (log/info "handle prompt")
            (send-queue! ch (:text msg)))
          "track"
          (do
            (log/info "find yt track")
            (serve-track! ch (:id msg) (:query msg)))
          (log/info "unhandled message type:" type))))}))
