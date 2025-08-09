(ns ai-dj.ai
  (:require
   [ai-dj.yt :as yt]
   [cheshire.core :as json]
   [clojure.tools.logging :as log]
   [hato.client :as http]))

(def openai-key (System/getenv "OPENAI_API_KEY"))
(def default-model (or (System/getenv "OPENAI_MODEL") "gpt-5-mini"))

(def system-prompt {:role "system" :content "You respond with exact answers to any queries"})

(defn create-chat-completion [req]
  (let [resp (http/post "https://api.openai.com/v1/chat/completions"
                        {:headers {"Authorization" (str "Bearer " openai-key)}
                         :form-params req
                         :content-type :json
                         :as :json})
        body (:body resp)
        result (get-in body [:choices 0 :message :content])]
    result))

(defn interpret-prompt [text]
  (create-chat-completion
   {:model default-model
    :messages [system-prompt
               {:role "user"
                :content (str "Turn this into a YouTube song search query: \"" text "\"")}]
    :temperature 1.0}))

(defn prompt→tracks [text]
  (let [query (interpret-prompt text)
        tracks (yt/search-videos query 5)]
    tracks))

(defn make-commentary [track]
  (let [prompt (str "Write a reflective 2‑sentence DJ insight for song \"" (:title track) "\" by " (:artist track))]
    (create-chat-completion
     {:model default-model
      :messages [system-prompt {:role "user" :content prompt}]
      :temperature 0.7})))

(defn create-playlist [theme]
  (let [resp  (create-chat-completion
               {:model default-model
                :messages [{:role "system"
                            :content (str "You're the best DJ of the universe. "
                                          "You understand what songs users want to listen to and find the best playlist. "
                                          "The playlist should include at least 10 songs, "
                                          "and they should all be available in youtube. "
                                          "Match the user's language."
                                          "Finally, you MUST respond in JSON, using the following schema: \n "
                                          "{\n"
                                          "  \"commentary\": \"A short commentary or quote (string)\",\n"
                                          "  \"songs\": [\n"
                                          "    {\n"
                                          "      \"title\": \"Song Title (string)\",\n"
                                          "      \"artist\": \"Artist Name (string)\"\n"
                                          "      \"youtube_id\": \"YouTube video id (string)\"\n"
                                          "    },\n"
                                          "    ...\n"
                                          "  ]\n"
                                          "}\n")}
                           {:role "user"
                            :content theme}]
                :temperature 1})]
    (json/parse-string resp true)))

;; (try
;;   ;; (System/setProperty "jdk.httpclient.HttpClient.log" "")
;;   (prompt→tracks "nostalgic")
;;   (catch Exception e
;;     (println e)))
;; (create-playlist "a little sad")
