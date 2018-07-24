(ns ^{:author "Daniel Leong"
      :doc "DND 5e sheet"}
  wish.sheets.dnd5e
  (:require-macros [wish.util :refer [fn-click]]
                   [wish.util.log :as log])
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [reagent-forms.core :refer [bind-fields]]
            [wish.util :refer [>evt <sub click>evt invoke-callable]]
            [wish.util.nav :refer [sheet-url]]
            [wish.inventory :as inv]
            [wish.sheets.dnd5e.overlays :as overlays]
            [wish.sheets.dnd5e.style :as styles :refer [styles]]
            [wish.sheets.dnd5e.subs :as subs]
            [wish.sheets.dnd5e.events :as events]
            [wish.sheets.dnd5e.util :refer [ability->mod equippable? mod->str]]
            [wish.sheets.dnd5e.widgets :refer [currency-preview spell-card]]
            [wish.views.widgets :as widgets
             :refer-macros [icon]
             :refer [expandable formatted-text link]]
            [wish.views.widgets.virtual-list :refer [virtual-list]]))

; ======= Top bar ==========================================

(defn- hp-normal [hp max-hp]
  [:<>
   [:div.label [:span.content "Hit Points"]]
   [:div.value
    [:div.now hp]
    [:div.divider " / "]
    [:div.max max-hp]]])

(defn- save-indicators
  [prefix icon-class used]
  [:div.indicators
   prefix

   (for [i (range 3)]
     (with-meta
       (if (< i used)
         (icon :radio-button-checked.icon {:class icon-class})
         (icon :radio-button-unchecked.icon {:class icon-class}))
       {:key i}))])

(defn- hp-death-saving-throws []
  (let [{:keys [saves fails]} (<sub [::subs/death-saving-throws])]
    [:<>
     [save-indicators "😇" :save saves]
     [save-indicators "☠️" :fail fails]]))

(defn hp []
  (let [[hp max-hp] (<sub [::subs/hp]) ]
    [:div.clickable.hp.col
     {:on-click (click>evt [:toggle-overlay [#'overlays/hp-overlay]])}

     (if (> hp 0)
       [hp-normal hp max-hp]
       [hp-death-saving-throws])]))

(defn header []
  (let [common (<sub [:sheet-meta])
        classes (<sub [:classes])]
    [:div {:class (:header styles/header)}
     [:div.left.side
      [:div.col
       [widgets/save-state]]

      [:div.col.left
       [:div.name [link {:href "/sheets"}
                   (:name common)]]
       [:div.meta
        [:div.race (:name (<sub [:race]))]
        [:div.classes (->> classes
                           (map (fn [c]
                                  (str (-> c :name) " " (:level c))))
                           (str/join " / "))]]]

      [:div.col
       [link {:href "#"
              :on-click (click>evt [:toggle-overlay [#'overlays/notes-overlay]])}
        (icon :description)]]]

     [:div.space]

     [:div.settings.side
      [:div.col
       (let [sheet-id (<sub [:active-sheet-id])]
         [link {:href (sheet-url sheet-id :builder :class)}
          (icon :settings)])]]

     [:div.right.side
      [:div.col
       [:div.stat (mod->str
                    (<sub [::subs/proficiency-bonus]))]
       [:div.label "Proficiency"]]

      [:div.col
       [:div.stat (<sub [::subs/ac])]
       [:div.label "AC"]]

      [:div.col
       [:div.stat (<sub [::subs/speed]) [:span.unit " ft"]]
       [:div.label "Speed"]]

      [:div.col
       [:div.stat (<sub [::subs/passive-perception])]
       [:div.label "Pass. Perc."]]

      [:div.col
       [:div.stat (mod->str
                    (<sub [::subs/initiative]))]
       [:div.label "Initiative"]]

      [:div.col
       [hp]] ] ]))


; ======= abilities ========================================

(def labeled-abilities
  [[:str "Strength"]
   [:dex "Dexterity"]
   [:con "Constitution"]
   [:int "Intelligence"]
   [:wis "Wisdom"]
   [:cha "Charisma"]])

(defn abilities-section []
  (let [abilities (<sub [::subs/abilities])
        prof-bonus (<sub [::subs/proficiency-bonus])
        save-proficiencies (<sub [::subs/save-proficiencies])
        save-buffs (<sub [::subs/save-buffs])
        save-extras (<sub [::subs/save-extras])]
    [:<>
     (for [[id label] labeled-abilities]
       ; TODO this should probably be a single subscription...
       (let [score (get abilities id)
             modifier (ability->mod score)
             modifier-str (mod->str modifier)
             proficient? (get save-proficiencies id)
             save-str (if proficient?
                        (mod->str
                          (+ modifier save-buffs prof-bonus))

                        (mod->str
                          (+ modifier save-buffs)))]
         ^{:key id}
         [:div.ability
          [:div.score score]
          [:div.label label]
          [:div.info "mod"]
          [:div.mod modifier-str]
          [:div.info "save"]
          [:div.mod save-str]
          [:div.proficiency
           {:class (when proficient?
                     "proficient")}]]))

     ; This is a good place for things like Elven advantage
     ; on saving throws against being charmed
     (when save-extras
       [:ul
        (for [item save-extras]
          ^{:key (:id item)}
          [:li (:desc item)])])]))


; ======= Skills ===========================================

(def ^:private skills-table
  [[[:dex :acrobatics "Acrobatics"]
    [:wis :animal-handling "Animal Handling"]
    [:int :arcana "Arcana"]
    [:str :athletics "Athletics"]
    [:cha :deception "Deception"]
    [:int :history "History"]
    [:wis :insight "Insight"]
    [:cha :intimidation "Intimidation"]
    [:int :investigation "Investigation"]]
   [[:wis :medicine "Medicine"]
    [:int :nature "Nature"]
    [:wis :perception "Perception"]
    [:cha :performance "Performance"]
    [:cha :persuasion "Persuasion"]
    [:int :religion "Religion"]
    [:dex :sleight-of-hand "Sleight of Hand"]
    [:dex :stealth "Stealth"]
    [:wis :survival "Survival"]]])

(defn skill-box
  [ability label total-modifier expert? proficient?]
  [:div.skill
   [:div.base-ability
    (str "(" (name ability) ")")]
   [:div.label label]
   [:div.score
    (mod->str
      total-modifier)]
   [:div.proficiency
    {:class (str (when (or expert? proficient?)
                   "proficient ")
                 (when expert?
                   "expert"))}]])

(defn skills-section []
  (let [abilities (<sub [::subs/abilities])
        expertise (<sub [::subs/skill-expertise])
        proficiencies (<sub [::subs/skill-proficiencies])
        prof-bonus (<sub [::subs/proficiency-bonus])]
    (vec (cons
           :div.sections
           (map
             (fn [col]
               [:div.skill-col
                (for [[ability skill-id label] col]
                  (let [expert? (contains? expertise skill-id)
                        proficient? (contains? proficiencies skill-id)
                        total-modifier (+ (ability->mod (get abilities ability))
                                          (cond
                                            expert? (* 2 prof-bonus)
                                            proficient?  prof-bonus))]
                    ^{:key skill-id}
                    [skill-box ability label total-modifier expert? proficient?]))])
             skills-table)))))


; ======= Combat ===========================================

(defn- attack-block [s & [extra-info]]
  [:div.attack (or extra-info {})
   [:div.name (:name s)]

   [:div.info-group
    [:div.label
     "TO HIT"]
    [:div.to-hit
     (mod->str (:to-hit s))]]

   [:div.info-group
    [:div.label
     "DMG"]
    [:div.dmg
     (or (:base-dice s)
         (:dmg s))]
    (when-let [alt-dice (:alt-dice s)]
      [:div.dmg.alt
       "(" alt-dice ")"])]])

(defn- combat-home []
  [:<>

   (when-let [s (<sub [::subs/unarmed-strike])]
     [:div.unarmed-strike
      [attack-block (assoc s :name "Unarmed Strike")]])

   (when-let [weapons (seq (<sub [::subs/equipped-weapons]))]
     [:div.weapons
      [:h4 "Weapons"]
      (for [w weapons]
        ^{:key (:id w)}
        [attack-block w])])

   (when-let [spell-attacks (seq (<sub [::subs/spell-attacks]))]
     [:div.spell-attacks
      [:h4 "Spell Attacks"]
      (for [s spell-attacks]
        ^{:key (:id s)}
        [attack-block s {:class :spell-attack}])])])

(defn- action-block
  [a]
  [:div.action
   [:div.name (:name a)]
   (when-let [use-id (:consumes a)]
     (when-let [{:keys [name uses-left]} (<sub [::subs/limited-use use-id])]
       [:div.consumable
        [:div.name name]
        [:div.uses uses-left " left"]
        (when (> uses-left 0)
          [:div.button
           {:on-click (click>evt [:+use use-id 1])}
           "Use 1"])]))
   [formatted-text :div.desc (:desc a)]])

(defn- combat-actions [filter-type]
  (let [spells (seq (<sub [::subs/prepared-spells-filtered filter-type]))
        actions (seq (<sub [::subs/combat-actions filter-type]))]
    (if (or spells actions)
      [:<>
       (when spells
         [:div.spells
          [:div.section-label "Spells"]

          (for [s spells]
            ^{:key (:id s)}
            [:div.spell-name.clickable
             {:on-click (click>evt [:toggle-overlay [#'overlays/spell-info s]])}
             (:name s)])])

       (when actions
         [:div.actions
          (for [a actions]
            ^{:key (:id a)}
            [action-block a])])]

      [:<> "Nothing available."])))

(defn- combat-page-link
  [page id label]
  (let [selected? (= id @page)]
    [:div.filter {:class (when selected?
                           "selected")}
     (if selected?
       [:span.unselectable label]

       [:a {:href "#"
            :on-click (fn-click
                        (reset! page id))}
        label])]))

(defn combat-section []
  (let [page (r/atom :home)]
    (fn []
      [:<>
       [:div.filters
        [combat-page-link page :home "Home"]
        [combat-page-link page :bonuses "Bonuses"]
        [combat-page-link page :reactions "Reactions"]]

       (case @page
         :home [combat-home]
         :bonuses [combat-actions :bonus]
         :reactions [combat-actions :reaction])])))


; ======= Features =========================================

(defn feature
  [f]
  [expandable
   [:div.feature
    [:div.name (:name f)]]
   [formatted-text :div.desc (:desc f)]])

(defn features-section []
  [:<>
   (when-let [fs (<sub [::subs/features-for [:classes]])]
      [:div.features-category
       [:h3 "Class features"]
       (for [f fs]
         ^{:key (:id f)}
         [feature f])])

    (when-let [fs (<sub [::subs/features-for [:races]])]
      [:div.features-category
       [:h3 "Racial Traits"]
       (for [f fs]
         ^{:key (:id f)}
         [feature f])])

    ; TODO proficiencies?
    ; TODO feats?
    ])


; ======= Limited-use ======================================

(defn usage-box-single
  "Render a toggle button for whether a single-use item has been used"
  [item used?]
  [:div.button
   {:class (when used?
             "selected")
    :on-click (click>evt [:toggle-used (:id item)])}
   (if used?
     "Used"
     "Use")])

(defn usage-box-many
  "Render a box for specifying how many uses remain"
  [item uses-left]
  [:div.many
   [:a.modify {:href "#"
               :on-click (click>evt [:+use (:id item) 1])}
    (icon :remove-circle)]

   ; TODO should this be a text box?
   uses-left

   [:a.modify {:href "#"
               :on-click (click>evt [:+use (:id item) -1])}
    (icon :add-circle)]])

(defn usage-box
  "Render some sort of box for 'using' a limited-use item,
   appropriate to the number of total possible `uses` and
   indicating the current `used-count`."
  [item uses used-count]
  [:div.usage
   (cond
     (= 1 uses) [usage-box-single item (> used-count 0)]
     :else (let [uses-left (- uses used-count)]
             [usage-box-many item uses-left]))])


(def trigger-labels
  {:short-rest "Short Rest"
   :long-rest "Long Rest"})

(defn describe-uses
  [uses trigger]
  (if (= 1 uses)
    (str "Once per " (trigger-labels trigger))
    (str uses " uses / " (trigger-labels trigger))))

(defn limited-use-section []
  (let [items (<sub [::subs/limited-uses])
        used (<sub [:limited-used])]
    [:<>
     [:div.rests
      [:div.button.short
       {:on-click (click>evt [:toggle-overlay [#'overlays/short-rest-overlay]])}
       "Short Rest"]
      [:div.button.long
       {:on-click (click>evt [:trigger-limited-use-restore
                              [:short-rest :long-rest]])}
       "Long Rest"]]

     (if-not (empty? items)
       (for [item items]
         (let [uses (:uses item)
               used-count (get used (:id item))]
           ^{:key (:id item)}
           [:div.limited-use
            [:div.info
             [:div.name (:name item)]
             [:div.recovery
              (describe-uses uses (:restore-trigger item))]]
            [usage-box item uses used-count]]))

       [:div.explanation
        [:p "Features, traits, and abilities that can only be used a limited number of times before requiring a rest will be listed here."]
        [:p "You will gain limited-use actions as you adventure and level up."]])
     ]))


; ======= Spells ===========================================

(defn spell-block
  [s]
  (let [level (:spell-level s)]
    [expandable
     [:div.spell
      [:div.spell-info
       [:div.name (:name s)]

       ; TODO concentration? ritual?
       [:div.meta (if (= 0 level)
                    "Cantrip"
                    (str "Level " level))]]

      (cond
        (:dice s)
        [:div.dice
         (invoke-callable s :dice)]

        (:save s)
        [:div.dice
         [:div.meta (:save-label s)]
         (:save-dc s)]
        )]

     ; collapsed:
     [spell-card s]]))

(defn spell-slot-use-block
  [kind level total used]
  [widgets/slot-use-block
   {:total total
    :used used
    :consume-evt [::events/use-spell-slot kind level total]
    :restore-evt [::events/restore-spell-slot kind level total]}])

(defn spells-list [spells]
  [:<>
   (for [s spells]
     ^{:key (:id s)}
     [spell-block s])])

(defn spells-section [spell-classes]
  (let [slots-sets (<sub [::subs/spell-slots])
        slots-used (<sub [::subs/spell-slots-used])
        prepared-spells-by-class (<sub [::subs/prepared-spells-by-class])]
    [:<>
     (for [[id {:keys [label slots]}] slots-sets]
       ^{:key id}
       [:div.spell-slots
        [:h4 label]
        (for [[level total] slots]
          ^{:key (str "slots/" level)}
          [:div.spell-slot-level
           [:div.label
            (str "Level " level)]
           [spell-slot-use-block
            id level total (get-in slots-used [id level])]])])

     (for [c spell-classes]
       (let [prepared-spells (get prepared-spells-by-class (:id c))
             attrs (-> c :attrs :5e/spellcaster)
             prepares? (:prepares? attrs)
             acquires? (:acquires? attrs)
             fixed-list? (not (:spells attrs))
             any-prepared? (> (count prepared-spells) 0)
             prepared-label (if prepares?
                              "prepared"
                              "known")]
         ^{:key (:id c)}
         [:div.spells
          [:h4 (:name c)
           (when-not fixed-list?
             [:div.manage-link
              [:a {:href "#"
                   :on-click (click>evt [:toggle-overlay
                                         [#'overlays/spell-management c]
                                         :scrollable? true])}
               (str "Manage " prepared-label " spells")]])
           (when acquires?
             [:div.manage-link
              [:a {:href "#"
                   :on-click (click>evt [:toggle-overlay
                                         [#'overlays/spell-management
                                          c
                                          :mode :acquisition]
                                         :scrollable? true])}
               (str "Manage " (:acquired-label attrs))]])]

          (if fixed-list?
            [spells-list prepared-spells]

            [expandable
             [:b (str (str/capitalize prepared-label) " Spells")
              [:span.count "(" (count prepared-spells) ")"]]

             (if any-prepared?
               [spells-list prepared-spells]
               [:div (str "You don't have any " prepared-label " spells")])

             ; auto-expand to show the "nothing prepared" explanation
             {:start-expanded? (not any-prepared?)}])]))]))


; ======= inventory ========================================

(defn- item-quantity-manager [item]
  [:div.quantity
   [:a.modify {:href "#"
               :on-click (click>evt [:inventory-subtract item 1])}
    (icon :remove-circle)]

   [bind-fields

    [:input.quantity {:field :numeric
                      :id :quantity
                      :min 0}]

    {:get #(<sub [::subs/item-quantity (:id item)])
     :save! (fn [path v]
              (>evt [:inventory-set-amount item v]))}]

   [:a.modify {:href "#"
               :on-click (click>evt [:inventory-add item 1])}
    (icon :add-circle)] ])

(defn- inventory-entry
  [item can-attune?]
  (let [{:keys [attrs type]
         quantity :wish/amount} item
        stacks? (inv/stacks? item)]
    [expandable
     [:div.item
      [:div.info
       [:div.name (:name item)]
       (when-let [n (:notes item)]
         [:div.notes-preview n])]

      (when (inv/instanced? item)
        [:div.notes
         [link {:href "#"
                :on-click (click>evt [:toggle-overlay
                                      [#'overlays/notes-overlay :item item]]
                                     :propagate? false)}
          (icon :description)]])

      (when stacks?
        [:div.quantity quantity])

      (when (= :ammunition type)
        [:div.consume.button
         {:on-click (click>evt [:inventory-subtract item]
                               :propagate? false)}
         "Consume 1"])

      (when (equippable? item)
        [:div.equip.button
         {:on-click (click>evt [:toggle-equipped item]
                               :propagate? false)}
         (if (:wish/equipped? item)
           "Unequip"
           "Equip")])

      (when (:attunes? item)
        [:div.attune.button
         {:class (when-not (or (:attuned? item)
                               can-attune?)
                   ; "limit" 3 attuned
                   "disabled")

          :on-click (click>evt [::events/toggle-attuned item]
                               :propagate? false)}

         (if (:attuned? item)
           "Unattune"
           "Attune")])]

     [:div.item-info
      [formatted-text :div.desc (:desc item)]

      (when stacks?
        [item-quantity-manager item])

      [:a.delete {:href "#"
           :on-click (click>evt [:inventory-delete item])}
       (icon :delete-forever)
       " Delete" (when stacks? " all") " from inventory"]]]) )

(defn- item-browser-item [item]
  [:<>
   [:div.name (:name item)]
   [:div.add.button
    {:on-click (click>evt [:inventory-add item])}
    "Add"]])

(defn- item-browser []
  [:<>
   ; TODO search bar with filtering
   [:div.item-browser.scrollable
    [virtual-list
     :items (<sub [:all-items])
     :render-item (fn [props item]
                    [:div.item props
                     [item-browser-item item]])]]])

(defn inventory-section []
  [:<>
   (when-let [inventory (seq (<sub [::subs/inventory-sorted]))]
     (let [can-attune? (< (count (<sub [::subs/attuned-ids]))
                          3)]
       (for [item inventory]
         ^{:key (:id item)}
         [inventory-entry item can-attune?])))

   [:h4 "Add Items"]

   [:a {:href "#"
        :on-click (click>evt [:toggle-overlay
                              [#'overlays/custom-item-creator]])}
    "Create a custom item"]

   [item-browser]])


; ======= Main interface ===================================

(defn- section
  ([title content]
   (section title nil content))
  ([title section-style content]
   (let [opts (if section-style
                {:class (section-style styles)}
                {})]
     [:div.section opts
      [:h1 title]
      content])))

(defn sheet []
  [:<>
   [header]
   [:div.sections
    [section "Abilities"
     :abilities
     [abilities-section]]
    [section "Skills"
     :skills
     [skills-section]]
    [section "Combat"
     :combat
     [combat-section]]

    [section "Features"
     [features-section]]

    [section "Limited-use"
     :limited-use-section
     [limited-use-section]]

    (when-let [spell-classes (seq (<sub [::subs/spellcaster-classes]))]
      [section "Spells"
       :spells-section
       [spells-section spell-classes]])

    [section [:<>
              "Inventory"
              [:span.clickable
               {:class "clickable"
                :on-click (click>evt [:toggle-overlay [#'overlays/currency-manager]])}
               [currency-preview]]]
     :inventory-section
     [inventory-section]]]])
