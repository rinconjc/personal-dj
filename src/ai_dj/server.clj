(ns ai-dj.server
  (:require
   [ai-dj.ws :as ws]
   [clojure.tools.logging :as log]
   [org.httpkit.server :as http]
   [reitit.ring :as ring])
  (:gen-class))

(defn routes []
  [["/ws" {:get ws/handle-ws}]
   ["/*" (ring/create-resource-handler)]])

(def app
  (ring/ring-handler
   (ring/router (routes) {:conflicts (constantly nil)})
   (ring/create-default-handler)))

(defonce server (atom nil))

(def port (Integer/parseInt (or (not-empty (System/getenv "PORT")) "3000")))

(defn start []
  (reset! server (http/run-server #'app {:port port}))
  (log/info "AI DJ backend running on http://localhost:" port))

(defn stop []
  (when @server
    (@server :timeout 100)
    (reset! server nil)))

(defn -main [& args]
  (start))

;; (-main)
