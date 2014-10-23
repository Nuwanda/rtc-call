(ns rtc-call.core
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [chan]]
            [rtc-call.video-display :as video]
            [rtc-call.ws-client :as ws]))

(def app-state (atom {:call-in-msgs (chan)
                      :call-out-msgs (chan)}))

(defcomponent main-view [data owner]
              (render [_]
                      (dom/div
                        (om/build ws/ws-client data)
                        (om/build video/call-view data))))

(defn main []
  (om/root main-view
           app-state
           {:target (. js/document (getElementById "app"))}))