(ns rtc-call.ws-client
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [<! put!]]
            [chord.client :refer [ws-ch]]
            [rtc-call.util :as util]))

(defn- receive-from-server [data owner]
  (let [ws-ch (om/get-state owner :ws-ch)
        in-ch (:call-in-msgs @data)]
    (go-loop []
      (let [msg (<! ws-ch)]
        (.log js/console (str "Got from server - message: " (:message msg)))
        (put! in-ch (:message msg)))
      (recur))))

(defn- send-to-server [owner dest desc type]
  (let [ws-ch     (om/get-state owner :ws-ch)
        src       (om/get-state owner :src-id)
        msg       {:src src :desc desc :type type :dest dest}]
    (.log js/console (str "Sent: " msg))
    (put! ws-ch msg)))

(defn- send-registration [owner]
  (let [ws-ch (om/get-state owner :ws-ch)
        src   (om/get-state owner :src-id)
        msg   {:type :reg :src src}]
    (.log js/console (str "Sent: " msg))
    (om/set-state! owner :logged true)
    (put! ws-ch msg)))

(defn- receive-from-client [data owner]
  (let [ch (:call-out-msgs @data)]
    (go-loop []
             (let [{:keys [type desc dest] :as msg} (<! ch)]
               (.log js/console (str "Got from client : " msg))
               (if (nil? type)
                 (.log js/console (str "Client sending poorly formatted message: " msg))
                 (if (= type :reg)
                   (send-registration owner)
                   (if (nil? desc)
                     (.log js/console (str "Client sending poorly formatted message: " msg))
                     (send-to-server owner dest desc type)))))
             (recur))))

(defn- handle-change [owner ref e]
  (om/set-state! owner ref (.. e -target -value)))

(defcomponent ws-client [data owner]
              (init-state [_]
                          {:ws-ch nil
                           :src-id (str (rand-int 100))
                           :logged false})
              (did-mount [_]
                         (go
                           (let [{:keys [ws-channel error]}
                                 (<! (ws-ch "ws://rtc-call.herokuapp.com/ws"))]
                             (if error
                               (.log js/console (str "error opening ws-channel: " error))
                               (do
                                 (om/set-state! owner :ws-ch ws-channel)
                                 (receive-from-server data owner)
                                 (receive-from-client data owner))))))
              (render-state [_ {:keys [src-id logged]}]
                      (dom/div {:class "page-header" :style {:display (util/display logged)}}
                        (dom/h1 {:style {:text-align "center"}} "Your id: " (dom/small (str src-id))))))
