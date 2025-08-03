(ns ai-dj.spotify
  (:require
   [clojure.tools.logging :as log]
   [hato.client :as http])
  (:import
   (java.time Duration LocalDateTime)))

(defonce client-id (System/getenv "SPOTIFY_CLIENT_ID"))
(defonce client-secret (System/getenv "SPOTIFY_CLIENT_SECRET"))

(defonce token (atom nil))

(defn get-access-token []
  (let [res (http/post "https://accounts.spotify.com/api/token"
                       {:form-params {:grant_type "client_credentials"
                                      :client_id client-id
                                      :client_secret client-secret}
                        :as :json})]
    (-> res :body
        (update :expires_in
                (fn [secs] (. (LocalDateTime/now)
                              (plus (Duration/ofSeconds (- secs 30)))))))))

;; (get-access-token)

(defn ensure-token []
  (when (or (nil? @token) (.isBefore (:expires_in @token) (LocalDateTime/now)))
    (reset! token (get-access-token)))
  (:access_token @token))

(defn search-tracks [prompt]
  (let [url "https://api.spotify.com/v1/search"
        params {:headers {"Authorization" (str "Bearer " (ensure-token))}
                :query-params {:q prompt
                               :type "track"
                               :limit 10}
                :as :json}
        response (http/get url params)
        items (get-in response [:body :tracks :items])]
    (mapv (fn [track]
            {:id (:id track)
             :title (:name track)
             :artist (-> track :artists first :name)
             :album (get-in track [:album :name])
             :preview-url (:preview_url track)
             :spotify-url (get-in track [:external_urls :spotify])})
          items)))
;; (search-tracks "at work")
