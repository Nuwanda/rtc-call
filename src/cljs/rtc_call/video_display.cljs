(ns rtc-call.video-display
  (:require-macros [cljs.core.async.macros :refer [go-loop go]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [<! put!]]
            [rtc-call.servers :refer [server-config]]))

(defn- create-video [owner media]
  (let [node (js/document.createElement "video" )
        stream (.-stream media)]
    (.setAttribute node "autoplay" "autoplay")
    (.appendChild (om/get-node owner "wrapper") node)
    (if stream
      (. js/window (attachMediaStream node stream))
      (. js/window (attachMediaStream node media)))))

(defn- send-answer [data desc]
  (put! (:call-out-msgs @data) [:answer desc]))

(defn- got-media [owner stream]
  (let [pc (om/get-state owner :pc)]
    (.addStream pc stream)
    (create-video owner stream)))

(defn- get-user-media [owner]
  (let [constraints #js {:video true :audio true}
        on-success  #(got-media owner %)
        on-error    #(js/alert "Error getting media stream")]
    (js/getUserMedia constraints on-success on-error)))

(defn- got-ice-candidate [owner event]
  (let [pc (om/get-state owner :pc)]
    (if (.-candidate event)
      (.addIceCandidate pc (js/RTCIceCandidate. (.-candidate event))))))

(defn- got-remote-description [owner desc]
  (let [pc (om/get-state owner :pc)]
    (.setRemoteDescription pc desc)))

(defn- got-local-description [owner data type desc]
  (let [pc (om/get-state owner :pc)]
    (.setLocalDescription pc desc)
    (put! (:call-out-msgs @data) [type desc])))

(defn- initiate-call [owner data]
  (let [pc (om/get-state owner :pc)]
    (.createOffer pc #(got-local-description owner data :offer %) #(js/alert "Error creating offer"))))


;;TODO: this isn't working, gotta send createAnswer as a callback to setRemoteDesc
(defn- receive-call [owner data offer]
  (got-remote-description owner offer)
  (let [pc (om/get-state owner :pc)]
    (.createAnswer pc #(send-answer data %) #(js/alert "Error creating answer"))))

(defn- stop-call [_]
  (js/alert "NOT IMPLEMENTED"))

(defn- create-rtc-peer [owner]
  (let [pc (js/RTCPeerConnection. server-config)]
    (-> pc (.-onaddstream) (set! #(create-video owner %)))
    (-> pc (.-onicecandidate) (set! #(got-ice-candidate owner %)))
    (om/set-state! owner :pc pc)))

(defcomponent call-view [data owner]
              (init-state [_]
                          {:pc nil})
              (will-mount [_]
                          (create-rtc-peer owner))
              (did-mount [_]
                         (go-loop []
                                  (let [[type msg] (<! (:call-in-msgs @data))]
                                    (cond
                                      (= type :offer) (do
                                                        (.log js/console (str "Got offer: " msg))
                                                        (receive-call owner data msg))
                                      (= type :answer) (do
                                                         (.log js/console (str "Got answer: " msg))
                                                         (got-remote-description owner msg))
                                      :else (.log js/console (str "Unexpected message: " msg))))
                                  (recur)))
              (render [_]
                      (dom/div
                        (dom/div {:class "row" :style {:margin-top "5px"}}
                                 (dom/div {:class "col-md-8 col-md-offset-2" :style {:text-align "center"} :ref "wrapper"}
                                          (dom/div {:class "btn-group"}
                                                   (dom/button {:class "btn btn-primary"
                                                                :on-click #(get-user-media owner)
                                                                :disabled false
                                                                :ref "init"}
                                                               "Initialize")
                                                   (dom/button {:class    "btn btn-primary"
                                                                :on-click #(initiate-call owner data)
                                                                :disabled false
                                                                :ref "start"}
                                                               "Start Call")
                                                   (dom/button {:class "btn btn-primary"
                                                                :on-click #(receive-call owner data nil)
                                                                :disabled false
                                                                :ref "pick-up"}
                                                               "Pick-up Call")
                                                   (dom/button {:class "btn btn-primary"
                                                                :on-click #(stop-call owner)
                                                                :disabled false
                                                                :ref "end"}
                                                               "End Call"))
                                          (dom/br nil)
                                          (dom/br nil))))))