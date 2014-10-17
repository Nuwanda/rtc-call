(ns rtc-call.core
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [chan]]
            [rtc-call.video-display :as video]
            [rtc-call.ws-client :as ws]))

(def test-channel (chan))

(defonce app-state (atom {:call-in-msgs test-channel
                          :call-out-msgs test-channel}))

(defn main []
  (om/root ws/ws-client
           app-state
           {:target (. js/document (getElementById "app"))}))