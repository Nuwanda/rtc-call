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
            [clojure.core.async :refer [<! >! chan go-loop put!]]
            [clojure.set :refer [map-invert]])
  (:gen-class :main true))

(def clients (atom {}))

(deftemplate page
  (io/resource "index.html") [] [:body] (if is-dev? inject-devmode-html identity))

(defn- store-channel [id ch]
  (when-not (contains? @clients id)
    (swap! clients assoc id ch)))

(defn- send-message [msg to from-ch]
  (if-let [ch (get @clients to)]
    (put! ch msg)
    (put! from-ch {:error "Destination id not found on server"})))

(defn- handle-message [ws-ch {:keys [src dest desc type] :as msg}]
  (prn (str "Connected clients: " @clients))
  (if (or (nil? src) (nil? type))
    (prn (str "Got poorly formatted message: " msg))
    (if (= type :reg)
      (store-channel src ws-ch)
      (if (or (nil? desc) (nil? dest))
        (prn (str "Got poorly formatted message: " msg))
        (do
          (store-channel src ws-ch)
          (send-message {:desc desc :type type :src src} dest ws-ch))))))

(defn ws-handler [{:keys [ws-channel]}]
  (go-loop []
           (if-let [{:keys [message error]} (<! ws-channel)]
             (do
               (if error
                 (prn (format "Error: '%s'." (pr-str error)))
                 (do
                   (prn (format "Received: '%s' at %s" (pr-str message) (Date.)))
                   (handle-message ws-channel message)))
               (recur))
             (let [inv-clients (map-invert @clients)
                   removed     (dissoc inv-clients ws-channel)
                   new-clients (map-invert removed)]
               (reset! clients new-clients)
               (prn "Client left.")
               (prn (str "Connected clients: " @clients))))))

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
      (let [port (Integer. (or port (env :port) 5000))]
        (print "Starting web server on port" port ".\n")
        (run-server http-handler {:port port
                          :join? false}))))
  server)

(defn -main [& [port]]
  (run port))
