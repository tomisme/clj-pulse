(ns app.views
  (:require
   [re-frame.core :as rf]))


(defn navigate! [employee-id]
  (rf/dispatch [:dcw.event/navigate employee-id]))


(defn navigated []
  @(rf/subscribe [:dcw.sub/navigated]))


(defn save! [value]
  (rf/dispatch [:dcw.event/save value]))
