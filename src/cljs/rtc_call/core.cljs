(ns rtc-call.core
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [rtc-call.video-display :as video]))

(enable-console-print!)

(defonce app-state (atom {:offer nil
                      :answer nil}))

(defn main []
  (om/root video/call-view
           app-state
           {:target (. js/document (getElementById "app"))}))