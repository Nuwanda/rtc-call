(ns rtc-call.core
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [chan]]
            [rtc-call.video-display :as video]))

(defonce app-state (atom {}))

(defn main []
  (om/root video/call-view
           app-state
           {:target (. js/document (getElementById "app"))}))