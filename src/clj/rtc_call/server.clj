(ns rtc-call.server
  (:import (java.util Date))
  (:require [clojure.java.io :as io]
            [rtc-call.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel]]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [resources]]
            [compojure.handler :refer [site]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [ring.middleware.reload :as reload]
            [environ.core :refer [env]]
            [org.httpkit.server :refer [run-server]]
            [chord.http-kit :refer [wrap-websocket-handler]]
            [clojure.core.async :refer [<! >! chan go-loop put!]]))

(def db (atom {}))

(deftemplate page
  (io/resource "index.html") [] [:body] (if is-dev? inject-devmode-html identity))

(defn- handle-message [ws-ch {:keys [id] :as msg}]
  (if (nil? id)
    (prn (str "Got poorly formatted message: " msg))
    (let [{:keys [get set]} msg]
      (cond
        get (put! ws-ch (clojure.core/get @db id))
        set (swap! db assoc id (:data set))
        :else (prn (str "Got poorly formatted message: " msg))))))

(defn ws-handler [{:keys [ws-channel]}]
  (go-loop []
           (when-let [{:keys [message error]} (<! ws-channel)]
             (if error
               (prn (format "Error: '%s'." (pr-str error)))
               (do
                 (prn (format "Received: '%s' at %s." (pr-str message) (Date.)))
                 (handle-message ws-channel message)))
             (recur))))

(defroutes routes
  (resources "/")
  (resources "/react" {:root "react"})
  (GET "/ws" [] (-> ws-handler
                    (wrap-websocket-handler)))
  (GET "/*" req (page)))

(def http-handler
  (if is-dev?
    (reload/wrap-reload (site #'routes))
    (site routes)))

(defn run [& [port]]
  (defonce ^:private server
    (do
      (if is-dev? (start-figwheel))
      (let [port (Integer. (or port (env :port) 8080))]
        (print "Starting web server on port" port ".\n")
        (run-server http-handler {:port port
                          :join? false}))))
  server)

(defn -main [& [port]]
  (run port))
