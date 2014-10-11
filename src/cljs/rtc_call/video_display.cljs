(ns rtc-call.video-display
  (:require-macros [cljs.core.async.macros :refer [go-loop go]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [<! put!]]))

(defn- create-video [owner media]
  (let [node (js/document.createElement "video" )
        stream (.-stream media)]
    (.setAttribute node "autoplay" "autoplay")
    (.appendChild (om/get-node owner "wrapper") node)
    (if stream
      (. js/window (attachMediaStream node stream))
      (. js/window (attachMediaStream node media)))))

(defn- create-offer [data pc stream]
  (doto pc
    (.onaddstream stream)
    (.addStream stream)
    (.createOffer (fn [offer] (.setLocalDescription pc
                                                    (js/RTCSessionDescription. offer)
                                                    #(om/update! data :offer offer)
                                                    #(print "error setting local description")))
                  #(print "error creating offer"))))

(defn- create-answer [data pc stream]
  (doto pc
    (.onaddstream stream)
    (.addStream stream)
    (.setRemoteDescription (js/RTCSessionDescription. (:offer @data))
                           (fn []
                             (.createAnswer pc
                                            (fn [answer]
                                              (.setLocalDescription pc
                                                                    (js/RTCSessionDescription. answer)
                                                                    #(put! (:events @data) answer)
                                                                    #(print "error setting remote local description")))
                                            #(print "error creating answer")))
                           #(print "error setting remote description"))))

(defn- initiate-call [data pc]
  (let [constraints #js {:video true :audio true}
        on-success  #(create-offer data pc %)
        on-error    #(js/alert "Error getting media stream")]
    (js/getUserMedia constraints on-success on-error)))

(defn- receive-call [data pc]
  (let [constraints #js {:video true :audio true}
        on-success  #(create-answer data pc %)
        on-error    #(js/alert "Error getting media stream")]
    (js/getUserMedia constraints on-success on-error)))

(defcomponent local-view [data owner]
              (display-name [_]
                            "video-view")
              (init-state [_]
                          {:pc nil})
              (will-mount [_]
                          (let [pc (js/RTCPeerConnection. nil)]
                            (-> pc (.-onaddstream) (set! #(create-video owner %)))
                            (om/set-state! owner :pc pc)))
              (did-mount [_]
                         (go
                           (let [answer (<! (:events @data))]
                             (.setRemoteDescription (om/get-state owner :pc)
                                                    (js/RTCSessionDescription. answer)
                                                    #(print "added answer to local rtcpeer")
                                                    #(print "Error setting remote description")))))
              (render-state [_ {:keys [pc]}]
                      (dom/div
                        (dom/div {:class "row" :style {:margin-top "5px"}}
                                 (dom/div {:class "col-md-8 col-md-offset-2" :style {:text-align "center"} :ref "wrapper"}
                                          (dom/button {:class "btn btn-primary"
                                                       :on-click #(initiate-call data pc)}
                                                      "Start Call")
                                          (dom/br nil)
                                          (dom/br nil))))))

(defcomponent remote-view [data owner]
              (display-name [_]
                            "video-view")
              (init-state [_]
                          {:pc nil})
              (will-mount [_]
                          (let [pc (js/RTCPeerConnection. nil)]
                            (-> pc (.-onaddstream) (set! #(create-video owner %)))
                            (om/set-state! owner :pc pc)))
              (render-state [_ {:keys [pc]}]
                            (dom/div
                              (dom/div {:class "row" :style {:margin-top "5px"}}
                                       (dom/div {:class "col-md-8 col-md-offset-2" :style {:text-align "center"} :ref "wrapper"}
                                                (dom/button {:class "btn btn-primary"
                                                             :on-click #(receive-call data pc)}
                                                            "Pick-up Call")
                                                (dom/br nil)
                                                (dom/br nil))))))

(defcomponent call-view [data owner]
              (render [_]
                      (dom/div
                        (om/build local-view data)
                        (om/build remote-view data))))