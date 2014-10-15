(ns rtc-call.video-display
  (:require-macros [cljs.core.async.macros :refer [go-loop go]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [<! put!]]))

(defn- got-local-ice [owner event]
  (let [rp (om/get-state owner :remote-pc)]
    (if (.-candidate event)
      (.addIceCandidate rp (js/RTCIceCandidate. (.-candidate event))))))

(defn- got-remote-ice [owner event]
  (let [lp (om/get-state owner :local-pc)]
    (if (.-candidate event)
      (.addIceCandidate lp (js/RTCIceCandidate. (.-candidate event))))))

(defn- create-video [owner media]
  (let [node (js/document.createElement "video" )
        stream (.-stream media)]
    (.setAttribute node "autoplay" "autoplay")
    (.appendChild (om/get-node owner "wrapper") node)
    (if stream
      (. js/window (attachMediaStream node stream))
      (. js/window (attachMediaStream node media)))))

(defn- create-rtc-peers [owner]
  (let [remote-pc (js/RTCPeerConnection. nil)
        local-pc (js/RTCPeerConnection. nil)]
    (-> local-pc (.-onicecandidate) (set! #(got-local-ice owner %)))
    (-> remote-pc (.-onaddstream) (set! #(create-video owner %)))
    (-> remote-pc (.-onicecandidate) (set! #(got-remote-ice owner %)))
    (om/set-state! owner :local-pc local-pc)
    (om/set-state! owner :remote-pc remote-pc)))

(defn- get-user-media [owner]
  (let [constraints #js {:video true :audio true}
        on-success  (fn [stream]
                      (do
                        (om/set-state! owner :local-stream stream)
                        (create-video owner stream)))
        on-error    #(js/alert "Error getting media stream")]
    (js/getUserMedia constraints on-success on-error)))

(defn- got-remote-description [owner desc]
  (let [lp (om/get-state owner :local-pc)
        rp (om/get-state owner :remote-pc)]
    (.setLocalDescription rp desc)
    (.setRemoteDescription lp desc)))

(defn- got-local-description [owner desc]
  (let [lp (om/get-state owner :local-pc)
        rp (om/get-state owner :remote-pc)]
    (.setLocalDescription lp desc)
    (.setRemoteDescription rp desc)
    (.createAnswer rp #(got-remote-description owner %) #(js/alert "Error creating offer"))))

(defn- initiate-call [owner]
  (let [pc (om/get-state owner :local-pc)
        stream (om/get-state owner :local-stream)]
    (doto pc
      (.addStream stream)
      (.createOffer #(got-local-description owner %) #(js/alert "Error creating offer")))))

(defn- stop-call [owner]
  (js/alert "NOT IMPLEMENTED"))

(defcomponent call-view [data owner]
              (display-name [_]
                            "video-view")
              (init-state [_]
                          {:local-pc nil
                           :remote-pc nil
                           :local-stream nil})
              (will-mount [_]
                          (create-rtc-peers owner))
              (render [_]
                      (dom/div
                        (dom/div {:class "row" :style {:margin-top "5px"}}
                                 (dom/div {:class "col-md-8 col-md-offset-2" :style {:text-align "center"} :ref "wrapper"}
                                          (dom/button {:class "btn btn-primary"
                                                       :on-click #(get-user-media owner)}
                                                      "Initialize")
                                          (dom/button {:class "btn btn-primary"
                                                       :on-click #(initiate-call owner)}
                                                      "Start Call")
                                          (dom/button {:class "btn btn-primary"
                                                       :on-click #(stop-call owner)}
                                                      "End Call")
                                          (dom/br nil)
                                          (dom/br nil))))))