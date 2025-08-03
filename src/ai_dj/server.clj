(ns ai-dj.server
  (:require [reitit.ring :as ring]
            [ai-dj.ws :as ws]
            [org.httpkit.server :as http]
            [cheshire.core :as json])
  (:gen-class))

(defn routes []
  [["/ws" {:get ws/handle-ws}]
   ;; ["/prompt" {:post (fn [req]
   ;;                     (let [{:keys [text]} (-> req :body slurp (json/parse-string true))]
   ;;                       (ws/serve-queue! text)
   ;;                       {:status 200 :body "OK"}))}]
   ])

(def app
  (ring/ring-handler
   (ring/router (routes))
   (constantly {:status 404 :body "Not found"})))

(defonce server (atom nil))

(defn start []
  (reset! server (http/run-server #'app {:port 3000}))
  (println "AI DJ backend running on http://localhost:3000"))

(defn stop []
  (when @server
    (@server :timeout 100)
    (reset! server nil)))

(defn -main [& args]
  (println "starting backend..." args)
  (start))

;; (-main)
