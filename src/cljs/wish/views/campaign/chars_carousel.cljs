(ns ^{:author "Daniel Leong"
      :doc "campaign.chars-carousel"}
  wish.views.campaign.chars-carousel
  (:require [spade.core :refer [defattrs]]
            [wish.style.flex :as flex]
            [wish.style.shared :refer [metadata]]
            [wish.util :refer [<sub >evt]]
            [wish.util.nav :as nav :refer [sheet-url]]
            [wish.views.error-boundary :refer [error-boundary]]
            [wish.views.widgets :refer [icon link link>evt]]
            [wish.views.campaign.events :as events]
            [wish.subs.campaign.members :as members]))

(defn- sheet-loader [sheet]
  [:div "Loading " (:name sheet) "..."])

(defn- sources-loader [sheet]
  [:div "Loading " (:name sheet) "..."])

(defn- char-sheet-loader
  [sheet-id content-fn]
  (let [sheet (<sub [:provided-sheet sheet-id])]
    (if (:sources sheet)
      (if (<sub [:sheet-source sheet-id])
        ; sheet is ready; render!
        [error-boundary
         [content-fn sheet]]

        (do
          (>evt [:load-sheet-source! sheet (:sources sheet)])
          [sources-loader sheet]))

      ; either we don't have the sheet at all, or it's just
      ; a stub with no actual data; either way, load it!
      (do
        (>evt [:load-sheet! sheet-id])
        [sheet-loader sheet]))))


; ======= overlay =========================================

(defattrs add-chars-overlay-style []
  {:padding "32px"}

  [:.desc (merge metadata
                 {:padding "8px"})]

  [:.character (flex/create
                 :center :vertical
                 {:width "100%"
                  :margin "8px 0"})
   [:.name {:width "45%"}]])

(defn add-chars-overlay []
  (let [campaign-id (<sub [:active-sheet-id])
        campaign-name (<sub [:meta/name])]
    [:div (add-chars-overlay-style)
     [:div.title
      "Add characters to " campaign-name]

     [:div.desc
      [:ol
       [:li "Request players share their sheet with you."]
       [:li "When they do, add them to the campaign, below. (You may need to refresh this page.)"]
       [:li "Send the invite link that appears to the character's owner."]]]

     [:div.candidates
      (for [c (<sub [::members/all])]
        ^{:key (:id c)}
        [:div.character
         [:div.name (:name c)]

         [:input.invite-url
          {:type :text
           :read-only true
           :on-click (fn [e]
                       (.select (.-target e))
                       (try
                         (js/document.execCommand "copy")
                         (>evt [:notify! {:duration :short
                                          :content "Copied to clipboard!"}])
                         (catch :default _
                           (println "Unable to copy to clipboard"))))
           :value (nav/campaign-invite-url
                    campaign-id
                    (:id c)
                    campaign-name)}]

         [:div.remove
          [link>evt [::events/remove-player (:id c)]
           (icon :close)]]
         ])

      (for [c (<sub [::members/candidates])]
        ^{:key (:id c)}
        [:div.character
         [:div.name (:name c)]

         [:div.add
          [link>evt [::events/add-player (:id c)]
           "Add to campaign"]]
         ])]
     ]))


; ======= the carousel ====================================

(defattrs carousel-style []
  (flex/create
    {:width "100%"
     :overflow-x 'auto})

  [:.carousel (flex/create
                {:margin 'auto})
   [:a.add-button (flex/create
                    :flow :vertical
                    :center :vertical
                    {:padding "8px"})]
   [:a.card {:color "inherit"
             :margin "8px 4px"
             :padding 0}]
   [:div.card {:background-color "#eee"
               :border-radius "4px"
               :padding "8px"
               :text-align 'center
               :width "220px"}
    [:&:hover {:background-color "#ddd"}]
    [:&:active {:background-color "#ccc"}]]])

(defn chars-carousel [chars-card-view]
  (if-let [members (seq (<sub [::members/all]))]
    [:div (carousel-style)
     [:div.carousel

      (for [c members]
        ^{:key (:id c)}
        [link {:href (sheet-url (:id c))
               :class "card"}
         [:div.card
          [char-sheet-loader
           (:id c)
           chars-card-view]]])

      [link>evt {:> [:toggle-overlay [#'add-chars-overlay]]
                 :class "add-button"}
       (icon :add)]

      ]]

    [:div.empty-carousel
     "No characters in this campaign... yet!"]))
