(ns ai-dj.yt
  (:require
   [hato.client :as http]
   [clojure.tools.logging :as log]))

(def api-key (System/getenv "YOUTUBE_API_KEY"))

(defn search-videos [query max-results]
  (let [url "https://www.googleapis.com/youtube/v3/search"
        opts {:query-params {:part "snippet"
                             :q query
                             :type "video"
                             :maxResults max-results}
              :as :json
              :headers {"X-goog-api-key" api-key}}
        response (http/get url opts)
        items (:items (:body response))]
    (map (fn [item]
           (let [id (get-in item [:id :videoId])
                 snippet (get item :snippet)]
             {:id id
              :title (get snippet :title)
              :channel (get snippet :channelTitle)}))
         items)))

;; (search-videos "90s hits" 5)
