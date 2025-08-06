
(ns build
  (:require [clojure.tools.build.api :as b]))

(def class-dir "target/classes")

(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn uberjar [_]
  (b/compile-clj {:basis @basis
                  :ns-compile '[ai-dj.server]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file "target/ai-dj-backend.jar"
           :basis @basis
           :main 'ai-dj.server}))
