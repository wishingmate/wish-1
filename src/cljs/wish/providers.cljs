(ns ^{:author "Daniel Leong"
      :doc "Data source providers"}
  wish.providers
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [wish.util.log :as log])
  (:require [clojure.core.async :refer [<!]]
            [clojure.string :as str]
            [cljs.reader :as edn]
            [wish.providers.gdrive :as gdrive]
            [wish.providers.gdrive.config :as gdrive-config]
            [wish.providers.wish :as wish]
            [wish.providers.core :as provider]
            [wish.sheets :refer [stub-sheet]]
            [wish.sheets.util :refer [unpack-id]]
            [wish.util :refer [>evt]]))

(def ^:private providers
  {:gdrive
   {:id :gdrive
    :name "Google Drive"
    :config #'gdrive-config/view
    :inst (gdrive/create-provider)}

   :wish
   {:id :wish
    :name "Wish Built-ins"
    :inst (wish/create-provider)}})

(defn config-view
  [provider-id]
  (if-let [{:keys [config]} (get providers provider-id)]
    [config]

    [:div.error "No config for this provider"]))

(defn get-info
  [provider-id]
  (get providers provider-id))

(defn init! []
  (doseq [provider (vals providers)]
    (when-let [inst (:inst provider)]
      (provider/init! inst))))

(defn create-sheet!
  "Returns a channel that emits [err sheet-id] on success"
  [sheet-name provider-id sheet-kind]
  {:pre [(not (nil? provider-id))
         (not (nil? sheet-kind))]}
  (if-let [{:keys [inst]} (get providers provider-id)]
    (provider/create-sheet inst
                           sheet-name
                           (stub-sheet sheet-kind sheet-name))

    (throw (js/Error. (str "No provider instance for " provider-id)))))

(defn load-raw
  "Load raw data for the given ID, formatted the same as a sheet-id
   (IE: the provider id is the namespace, and the data id is the name.)"
  [raw-id]
  (let [[provider-id pro-raw-id] (unpack-id raw-id)]
    (if-let [{:keys [inst]} (get providers provider-id)]
      (provider/load-raw inst pro-raw-id)

      (throw (js/Error. (str "No provider instance for " raw-id
                             "(" provider-id " / " pro-raw-id ")"))))))

(defn load-sheet!
  [sheet-id]
  (go (let [[err data] (<! (load-raw sheet-id))
            [sheet-err sheet] (when data
                                (try
                                  [nil (edn/read-string data)]
                                  (catch :default e
                                    [e nil])))]
        (if-let [e (or err sheet-err)]
          (do (log/err "Failed to load sheet: " e)
              (>evt [:put-sheet-error! sheet-id
                     {:err e
                      :retry-evt [:load-sheet! sheet-id]}]))

          (>evt [:put-sheet! sheet-id sheet])))))

(defn save-sheet!
  [sheet-id data on-done]
  (let [[provider-id pro-sheet-id] (unpack-id sheet-id)]
    (if-let [{:keys [inst]} (get providers provider-id)]
      (go (let [[err] (<! (provider/save-sheet
                            inst pro-sheet-id data))]
            (on-done err)))

      (on-done (js/Error. (str "No provider instance for " sheet-id
                               "(" provider-id " / " pro-sheet-id ")"))))))
