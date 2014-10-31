(ns rtc-call.video-display
  (:require-macros [cljs.core.async.macros :refer [go-loop go]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [<! put! chan]]
            [rtc-call.servers :refer [SERVERS]]
            #_[rtc.adapter]
            [rtc-call.util :as util]))

(defn- create-video [owner media]
  (let [node (js/document.createElement "video" )
        stream (.-stream media)]
    (.setAttribute node "autoplay" "autoplay")
    (.setAttribute node "style" "margin-top:10px")
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
  (let [in-call (om/get-state owner :in-call)
        pc      (om/get-state owner :pc)
        cand    (js/RTCIceCandidate. #js {:sdpMLineIndex (.-label msg)
                                          :candidate (.-cand msg)})]
    (if in-call
      (.addIceCandidate pc cand)
      (om/update-state! owner :candidates #(conj % cand)))))

(defn- unqueue-candidates [owner]
  (let [cands (om/get-state owner :candidates)
        pc    (om/get-state owner :pc)]
    (doall (map #(.addIceCandidate pc %) cands))))

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
                             (fn []
                               (unqueue-candidates owner)
                               (.createAnswer pc
                                              #(got-local-description owner data :answer %)
                                              #(js/alert "Error creating answer")
                                              constraints))
                             #(js/alert "Error setting remote description")))))

(defn- initiate-call [owner data]
  (let [pc         (om/get-state owner :pc)
        on-success #(got-local-description owner data :offer %)
        on-error   #(js/alert "Error creating offer")]
    (om/set-state! owner :caller true)
    (om/set-state! owner :in-call true)
    (.createOffer pc on-success on-error)))

(defn- create-rtc-peer [data owner]
  (let [pc (js/RTCPeerConnection. SERVERS)]
    (-> pc (.-onaddstream) (set! #(create-video owner %)))
    (-> pc (.-onicecandidate) (set! #(got-ice-candidate data owner %)))
    (om/set-state! owner :pc pc)))

(defn- handle-call [data owner src-id offer]
  (om/set-state! owner :request true)
  (om/set-state! owner :remote-id src-id)
  (go
    (let [accepted (<! (om/get-state owner :req-ch))]
      (if accepted
        (do
          (om/set-state! owner :in-call true)
          (got-remote-description owner data offer))
        (om/set-state! owner :remote-id nil)))
    (om/set-state! owner :request false)))

(defn- stop-call [owner]
  (js/alert "not yet implemented"))

(defn- handle-input [owner e]
  (om/set-state! owner :remote-id (.. e -target -value)))

(defcomponent call-view [data owner]
              (init-state [_]
                          {:pc nil
                           :caller false
                           :remote-id nil
                           :request false
                           :req-ch (chan)
                           :in-call false
                           :candidates []})
              (will-mount [_]
                          (create-rtc-peer data owner))
              (did-mount [_]
                         (go-loop []
                                  (let [{:keys [type desc src] :as msg} (<! (:call-in-msgs @data))
                                        parsed-desc (js/JSON.parse desc)]
                                    (cond
                                      (= type :offer) (do
                                                        (.log js/console (str "Got offer: " parsed-desc))
                                                        (handle-call data owner src parsed-desc))
                                      (= type :answer) (do
                                                         (.log js/console (str "Got answer: " parsed-desc))
                                                         (om/set-state! owner :remote-id src)
                                                         (got-remote-description owner data parsed-desc))
                                      (= type :candidate) (do
                                                            (.log js/console (str "Got candidate: " parsed-desc))
                                                            (got-remote-candidate owner parsed-desc))
                                      :else (.log js/console (str "Unexpected message: " msg))))
                                  (recur)))
              (render-state [_ {:keys [remote-id request req-ch]}]
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
                                                (dom/div {:class "col-md-4 col-md-offset-4"
                                                          :style {:text-align "center"
                                                                  :display (util/display request)}}
                                                         (dom/h3 (str "Incoming call from id: " remote-id))
                                                         (dom/div {:class "btn-group"}
                                                                  (dom/button {:class    "btn btn-primary"
                                                                               :on-click #(put! req-ch true)}
                                                                              "Accept")
                                                                  (dom/button {:class    "btn btn-primary"
                                                                               :on-click #(put! req-ch false)}
                                                                              "Reject"))))))))