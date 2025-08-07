(ns ai-dj.server
  (:gen-class)
  (:require
   [ai-dj.ws :as ws]
   [clojure.tools.logging :as log]
   [org.httpkit.server :as http]
   [reitit.ring :as ring]))

(defn wrap-logger [handler]
  (fn [request]
    (let [resp (handler request)]
      (log/info (merge (select-keys request [:request-method :uri])
                       (select-keys resp [:status])))
      resp)))

(defn routes []
  [["/ws" {:get ws/handle-ws}]
   ["/*" (ring/create-file-handler)]])

(def app
  (wrap-logger (ring/ring-handler
                (ring/router (routes) {:conflicts (constantly nil)})
                (ring/create-default-handler))))

(defonce server (atom nil))

(def port (Integer/parseInt (or (not-empty (System/getenv "PORT")) "3000")))

(defn start []
  (reset! server (http/run-server #'app {:port port}))
  (log/infof "AI DJ backend running on http://localhost:%d. Work dir: %s" port (System/getProperty "user.dir")))

(defn stop []
  (when @server
    (@server :timeout 100)
    (reset! server nil)))

(defn -main [& args]
  (start))

;; (-main)
