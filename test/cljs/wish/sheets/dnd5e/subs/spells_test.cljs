(ns wish.sheets.dnd5e.subs.spells-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [wish.sheets.dnd5e.subs.spells :as subs
             :refer [knowable-spell-counts-for
                     available-slots
                     spell-slots
                     usable-slots-for]]))

(def ^:private warlock
  {:attrs
   {:5e/spellcaster
    {:warlock
     {:cantrips [1 3,
                 4 1,
                 10 1]
      :ability :cha
      :spells :warlock/spells-list
      :extra-spells :warlock/extra-spells
      :prepares? false

      :slots-type :pact-magic
      :slots-label "Pact Magic"
      :slots {1 {1 1}, 2 {1 2}
              3 {2 2}, 4 {2 2}
              5 {3 2}, 6 {3 2}
              7 {4 2}, 8 {4 2}
              9 {5 2}, 10 {5 2}
              11 {5 3}, 12 {5 3}
              13 {5 3}, 14 {5 3}
              15 {5 3}, 16 {5 3}
              17 {5 4}, 18 {5 4}
              19 {5 4}, 20 {5 4}}
      :known [2 3 4 5 6 7 8 9 10 11 12 12 13 13 14 14 15 15 15 15]
      :multiclass-levels-mod 0
      :restore-trigger :short-rest
      }}
    }})

(def warlock-caster (-> warlock :attrs :5e/spellcaster :warlock))

(defn usable-slot-for
  ([slots s]
   (usable-slot-for slots warlock-caster s))
  ([slots spellcaster s]
   (first (usable-slots-for slots spellcaster s))))

(defn- ->standard
  [slots]
  {:standard {:label "Spell Slots"
              :slots slots}})

(deftest knowable-spell-counts-for-test
  (testing "Table"
    (is (= {:spells 4
            :cantrips 3}
           (knowable-spell-counts-for
             (assoc warlock-caster :level 3)
             {})))
    (is (= {:spells 11
            :cantrips 5}
           (knowable-spell-counts-for
             (assoc warlock-caster :level 10)
             {})))
    (is (= 0
           (:spells
             (knowable-spell-counts-for
               {:level 2
                :known [0 0 3]}
               {})))))

  (testing "Standard (eg: cleric)"
    (is (= {:spells 10
            :cantrips 4}
           (knowable-spell-counts-for
             {:level 7
              :id :cleric
              :slots :standard
              :cantrips [1 3,
                         4 1,
                         10 1]}
             {:cleric 3})))

    ; NOTE: :slots is omitted here; :standard is the default!
    (is (= {:spells 10
            :cantrips 4}
           (knowable-spell-counts-for
             {:level 7
              :id :cleric
              :cantrips [1 3,
                         4 1,
                         10 1]}
             {:cleric 3})))))

(deftest spell-slots-test
  (testing "No slots"
    (is (nil? (:slots (spell-slots [{:slots :none}])))))
  (testing "Single class, standard"
    (is (= (->standard {1 4, 2 3, 3 3, 4 1})
           (spell-slots [{:level 7}]))))
  (testing "Single class, half"
    (is (= (->standard {1 4, 2 3})
           (spell-slots [{:level 7
                          :slots :standard/half}]))))
  (testing "Single class, Warlock-like"
    (is (= {:pact-magic
            {:label "Pact Magic"
             :slots {4 2}}}
           (spell-slots [(merge
                           warlock-caster
                           {:level 7})]))))

  (testing "Multiclass, both standard"
    (is (= (->standard {1 4, 2 3, 3 3, 4 3, 5 2, 6 1, 7 1})
           (spell-slots [{:level 7}
                         {:level 7}]))))
  (testing "Multiclass, one half"
    (is (= (->standard {1 4, 2 3, 3 3, 4 3, 5 2})
           (spell-slots [{:level 7}
                         {:level 7
                          :slots :standard/half
                          :multiclass-levels-mod 2}]))))
  (testing "Multiclass, one warlock-like"
    (is (= (assoc (->standard {1 4, 2 3, 3 3, 4 1})
                  :pact-magic
                  {:label "Pact Magic"
                   :slots {4 2}})
           (spell-slots [{:level 7}
                         (merge
                           warlock-caster
                           {:level 7})])))))

(deftest available-slots-test
  (testing "Default slots"
    (is (= [{:kind :default
             :level 2
             :total 4
             :unused 2}]
           (available-slots
             {:default {:slots {1 4, 2 4}}}
             {:default {1 4, 2 2}}))))

  (testing "Mixed slots"
    (is (= [{:kind :default
             :level 1
             :total 4
             :unused 1}
            {:kind :pact-magic
             :level 1
             :total 2
             :unused 1}]
           (available-slots
             {:default {:slots {1 4}}
              :pact-magic {:slots {1 2}}}
             {:default {1 3}
              :pact-magic {1 1}})))))

(deftest usable-slot-for-test
  (testing "No downcasting"
    (is (nil? (usable-slot-for
                [{:kind :default
                  :level 1
                  :total 4
                  :unused 1}]
                {:spell-level 2}))))

  (testing "Same level"
    (is (= {:kind :default
            :level 1
            :total 4
            :unused 1}

           (usable-slot-for
             [{:kind :default
               :level 1
               :total 4
               :unused 1}]
             {:spell-level 1}))))

  (testing "Upcast"
    (is (= {:kind :default
            :level 2
            :total 4
            :unused 1}

           (usable-slot-for
             [{:kind :default
               :level 2
               :total 4
               :unused 1}]
             {:spell-level 1}))))

  (testing ":*spell-slot-consuming feature"
    (is (= {:kind :default
            :level 2
            :total 4
            :unused 1}

           (usable-slot-for
             [{:kind :default
               :level 2
               :total 4
               :unused 1}]
             {:spell-level 1
              :consumes :*spell-slot}))))

  (testing "No-slot caster"
    (is (nil? (usable-slot-for
                [{:kind :default
                  :level 2
                  :total 4
                  :unused 1}]
                {:slots :none}
                {:spell-level 1}))))

  (testing ":*spell-slot-consuming feature from a no-slot caster"
    (is (= {:kind :default
            :level 2
            :total 4
            :unused 1}

           (usable-slot-for
             [{:kind :default
               :level 2
               :total 4
               :unused 1}]
             {:slots :none}
             {:spell-level 1
              :consumes :*spell-slot})))))

(defn- slots-at-level [schedule level]
  {:slots (get-in subs/spell-slot-schedules [schedule level])})

(deftest highest-spell-level-for-slots-test
  (testing "Standard spell slots"
    (is (= 1 (subs/highest-spell-level-for-slots
               (slots-at-level :standard 1))))
    (is (= 2 (subs/highest-spell-level-for-slots
               (slots-at-level :standard 3))))
    (is (= 5 (subs/highest-spell-level-for-slots
               (slots-at-level :standard 10)))))

  (testing "Cantrip-only slots"
    (is (= 0 (subs/highest-spell-level-for-slots
               {:cantrips [0 1]
                :slots :none})))))
