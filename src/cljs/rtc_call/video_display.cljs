(ns rtc-call.video-display
  (:require-macros [cljs.core.async.macros :refer [go-loop go]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [<! put!]]
            [rtc-call.servers :refer [server-config]]
            [rtc.adapter]))

(defn- create-video [owner media]
  (let [node (js/document.createElement "video" )
        stream (.-stream media)]
    (.setAttribute node "autoplay" "autoplay")
    (.appendChild (om/get-node owner "wrapper") node)
    (if stream
      (. js/window (attachMediaStream node stream))
      (. js/window (attachMediaStream node media)))))

(defn- got-media [owner stream]
  (let [pc (om/get-state owner :pc)]
    (.addStream pc stream)
    (create-video owner stream)))

(defn- get-user-media [owner]
  (let [constraints #js {:video true :audio true}
        on-success  #(got-media owner %)
        on-error    #(js/alert "Error getting media stream")]
    (js/getUserMedia constraints on-success on-error)))

(defn- initialize [data owner]
  (let [o-ch (:call-out-msgs @data)]
    (put! o-ch {:type :reg})
    (get-user-media owner)))

(defn- got-ice-candidate [data owner event]
  (let [o-ch    (:call-out-msgs @data)
        dest    (om/get-state owner :remote-id)
        msg     {:type :candidate :dest dest}
        cand    (.-candidate event)
        to-json #(js/JSON.stringify % nil 2)]
    (if cand
      (put! o-ch (assoc msg :desc (to-json #js {:cand (.-candidate cand) :label (.-sdpMLineIndex cand)}))))))

(defn- got-local-description [owner data type desc]
  (let [pc      (om/get-state owner :pc)
        dest    (om/get-state owner :remote-id)
        js-desc (js/JSON.stringify desc nil 2)]
    (.setLocalDescription pc desc)
    (put! (:call-out-msgs @data) {:type type :desc js-desc :dest dest})))

(defn- got-remote-candidate [owner msg]
  (let [pc   (om/get-state owner :pc)
        cand (js/RTCIceCandidate. #js {:sdpMLineIndex (.-label msg)
                                       :candidate (.-cand msg)})]
    (.addIceCandidate pc cand)))

(defn- got-remote-description [owner data desc]
  (let [pc          (om/get-state owner :pc)
        caller?     (om/get-state owner :caller)
        rtc-desc    (js/RTCSessionDescription. desc)
        constraints  #js {:mandatory #js {:OfferToReceiveAudio true
                                          :OfferToReceiveVideo true}}]
    (if caller?
      (.setRemoteDescription pc
                             rtc-desc
                             #(.log js/console "Added remote description")
                             #(js/alert "Error adding remote description"))
      (.setRemoteDescription pc
                             rtc-desc
                             (fn [] (.createAnswer pc
                                                   #(got-local-description owner data :answer %)
                                                   #(js/alert "Error creating answer")
                                                   constraints))
                             #(js/alert "Error setting remote description")))))

(defn- initiate-call [owner data]
  (let [pc (om/get-state owner :pc)]
    (om/set-state! owner :caller true)
    (.createOffer pc #(got-local-description owner data :offer %) #(js/alert "Error creating offer"))))

(defn- create-rtc-peer [data owner]
  (let [pc (js/RTCPeerConnection. server-config)]
    (-> pc (.-onaddstream) (set! #(create-video owner %)))
    (-> pc (.-onicecandidate) (set! #(got-ice-candidate data owner %)))
    (om/set-state! owner :pc pc)))

(defn- handle-input [owner e]
  (om/set-state! owner :remote-id (.. e -target -value)))

(defcomponent call-view [data owner]
              (init-state [_]
                          {:pc nil
                           :caller false
                           :remote-id nil})
              (will-mount [_]
                          (create-rtc-peer data owner))
              (did-mount [_]
                         (go-loop []
                                  (let [{:keys [type desc src] :as msg} (<! (:call-in-msgs @data))
                                        parsed-desc (js/JSON.parse desc)]
                                    (cond
                                      (= type :offer) (do
                                                        (.log js/console (str "Got offer: " parsed-desc))
                                                        (om/set-state! owner :remote-id src)
                                                        (got-remote-description owner data parsed-desc))
                                      (= type :answer) (do
                                                         (.log js/console (str "Got answer: " parsed-desc))
                                                         (om/set-state! owner :remote-id src)
                                                         (got-remote-description owner data parsed-desc))
                                      (= type :candidate) (do
                                                            (.log js/console (str "Got candidate: " parsed-desc))
                                                            (got-remote-candidate owner parsed-desc))
                                      :else (.log js/console (str "Unexpected message: " msg))))
                                  (recur)))
              (render [_]
                      (dom/div
                        (dom/div {:class "row" :style {:margin-top "5px"}}
                                 (dom/div {:ref "wrapper" :style {:text-align "center"}}
                                          (dom/div {:class "col-md-2 col-md-offset-4" :style {:text-align "center"}}
                                                   (dom/button {:class    "btn btn-primary"
                                                                :on-click #(initialize data owner)}
                                                               "Initialize"))
                                          (dom/div {:class "col-md-2" :style {:text-align "center"}}
                                                   (dom/div {:class "input-group"}
                                                            (dom/input {:type "text"
                                                                        :class "form-control"
                                                                        :placeholder "ID to Call"
                                                                        :on-change #(handle-input owner %)}
                                                                       (dom/span {:class "input-group-btn"}
                                                                                 (dom/button {:class    "btn btn-default"
                                                                                              :on-click #(initiate-call owner data)}
                                                                                             "Call")))))
                                          #_(dom/button {:class "btn btn-primary"
                                                       :on-click #(got-remote-description owner data nil)
                                                       :disabled false
                                                       :ref "pick-up"}
                                                      "Pick-up Call")
                                          #_(dom/button {:class "btn btn-primary"
                                                       :on-click #(stop-call owner)
                                                       :disabled false
                                                       :ref "end"}
                                                      "End Call")
                                          (dom/br)
                                          (dom/br))))))