(ns ^{:author "Daniel Leong"
      :doc "dnd5e.subs"}
  wish.sheets.dnd5e.subs
  (:require [re-frame.core :refer [reg-sub subscribe]]
            [wish.sources.core :refer [expand-list find-class find-race]]
            [wish.sheets.dnd5e.util :refer [ability->mod]]))

; ability scores are a function of the raw, rolled stats
; in the sheet, racial modififiers, and any ability score improvements
; from the class.
; TODO There are also equippable items, but we don't yet support that.
(reg-sub
  ::abilities
  :<- [:sheet]
  :<- [:race]
  :<- [:classes]
  (fn [[sheet race classes]]
    (apply merge-with +
           (:abilities sheet)
           (-> race :attrs :5e/ability-score-increase)
           ; TODO how should classes do this?
           [])))

(reg-sub
  ::limited-uses
  :<- [:limited-uses]
  (fn [items]
    (remove
      :implicit?
      items)))

(reg-sub
  ::hp
  :<- [:sheet]
  :<- [::abilities]
  :<- [::total-level]
  :<- [:limited-used]
  (fn [[sheet abilities total-level limited-used-map]]
    (let [max-hp (apply +
                        (* total-level
                           (->> abilities
                                :con
                                ability->mod))
                        (->> sheet
                             :hp-rolled))
          used-hp (or (:hp#uses limited-used-map)
                      0)]
      [(- max-hp used-hp) max-hp])))

; returns a set of skill ids
(reg-sub
  ::skill-proficiencies
  :<- [:classes]
  (fn [classes _]
    ; TODO do any races provide skill proficiency?
    (->> classes
         (mapcat :attrs)
         (filter (fn [[k v]]
                   (when (= v true)
                     (= "proficiency" (namespace k)))))
         (map (comp keyword name first))
         (into #{}))))

; returns a set of skill ids
(reg-sub
  ::skill-expertise
  :<- [:classes]
  (fn [classes _]
    ; TODO expertise support
    #{}))


(reg-sub
  ::total-level
  :<- [:classes]
  (fn [classes _]
    (apply + (map :level classes))))

(defn level->proficiency-bonus
  [level]
  (condp <= level
    17 6
    13 5
    9 4
    5 3
    ; else
    2))

(reg-sub
  ::proficiency-bonus
  :<- [::total-level]
  (fn [total-level _]
    (level->proficiency-bonus total-level)))

; TODO selected class spells?
(reg-sub
  ::class-spells
  :<- [:classes]
  :<- [:sheet-source]
  (fn [[classes source]]
    (->> classes
         (map (comp :5e/spellcaster :attrs))
         (filter identity)
         (mapcat #(list (:spells %)
                        (:extra-spells %)))

         ; TODO provide options in case the list is
         ; a feature with options
         (mapcat #(expand-list source % nil)))))
