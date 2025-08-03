(ns ai-dj.ws
  (:require
   [ai-dj.ai :as ai]
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

(defn handle-prompt! [text]
  (let [tracks (ai/prompt→tracks text)]
    (reset! queue tracks)
    (reset! current 0)
    (broadcast! {:type "queue" :queue @queue})
    (future (Thread/sleep 5000) (send-commentary))))

(defn handle-ws [req]
  (http/with-channel req channel
    (swap! clients conj channel)
    (http/on-close channel #(swap! clients disj channel))
    (http/send! channel (json/generate-string {:type "queue" :queue @queue}))
    (http/send! channel (json/generate-string {:type "play" :current @current}))
    (http/on-receive channel
                     (fn [data & more]
                       (log/info "req:" data more)
                       (let [{:keys [type text]} (json/parse-string data true)]
                         (case type
                           "trackended" (do (swap! current inc) (send-commentary)
                                            (broadcast! {:type "next" :current @current}))
                           "prompt" (do
                                      (log/info "handle prompt")
                                      (handle-prompt! text))
                           nil))))))
