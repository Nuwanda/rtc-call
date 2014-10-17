(ns rtc-call.ws-client
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [<! put!]]
            [chord.client :refer [ws-ch]]))

(defn- receive-message [owner]
  (let [ws-ch (om/get-state owner :ws-ch)
        id    (om/get-state owner :id)
        msg   {:id id :get true}]
    (go
      (>! ws-ch msg)
      (let [msg (<! ws-ch)]
        (.log js/console (str "Got message: " (:message msg)))
        (om/set-state! owner :text (:message msg))))))

(defn- send-message [owner]
  (let [ws-ch (om/get-state owner :ws-ch)
        id    (om/get-state owner :id)
        data  (om/get-state owner :text)
        msg   {:id id :set {:data data}}]
    (go
      (.log js/console (str "Sent: " msg))
      (>! ws-ch msg))))

(defn handle-change [owner ref e]
  (om/set-state! owner ref (.. e -target -value)))

(defcomponent ws-client [data owner]
              (init-state [_]
                          {:ws-ch nil
                           :text "something here"
                           :id "0"})
              (did-mount [_]
                         (go
                           (let [{:keys [ws-channel error]}
                                 (<! (ws-ch "ws://localhost:8080/ws"))]
                             (if error
                               (.log js/console (str "error opening ws-channel: " error))
                               (om/set-state! owner :ws-ch ws-channel)))))
              (render-state [_ {:keys [id text]}]
                      (dom/div {:class "row"}
                               (dom/div {:style {:text-align "center" :margin-top "5px"}
                                         :class "col-md-4 col-md-offset-4"}
                                        (dom/input {:class "form-control"
                                                    :type "text"
                                                    :style {:text-align "center"}
                                                    :value id
                                                    :on-change #(handle-change owner :id %)}
                                                   nil)
                                        (dom/input {:class "form-control"
                                                    :type "text"
                                                    :style {:text-align "center"}
                                                    :value text
                                                    :on-change #(handle-change owner :text %)}
                                                   nil)
                                        (dom/br)
                                        (dom/div {:class "btn-group"}
                                                 (dom/button {:class "btn btn-primary"
                                                              :on-click #(send-message owner)}
                                                             "Send")
                                                 (dom/button {:class "btn btn-primary"
                                                              :on-click #(receive-message owner)}
                                                             "Receive"))))))