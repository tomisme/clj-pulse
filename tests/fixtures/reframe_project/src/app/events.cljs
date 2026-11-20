(ns app.events
  (:require
   [re-frame.core :as rf :refer [reg-event-db]]))


(rf/reg-event-fx
 :dcw.event/navigate
 (fn [_ [_ employee-id]]
   {:fx [[:dispatch [:router/navigate employee-id]]]}))


(rf/reg-sub
 :dcw.sub/navigated
 (fn [db _]
   (:route db)))


(reg-event-db
 :dcw.event/save
 (fn [db [_ value]]
   (assoc db :saved value)))
