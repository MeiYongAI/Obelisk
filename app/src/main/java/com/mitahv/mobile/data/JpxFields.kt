package com.mitahv.mobile.data

// JPX action/condition vocabulary, not preset configurations.
internal object JpxFields {
    val types = "target spellSupport item toggle stop smartDebuff spellDebuff spellDamage skill normalAttack kamisamaniNattaHi".split(' ')
    fun typesForGroup(group: String): List<String> = when (group) {
        "supports" -> listOf("spellSupport", "item", "toggle", "stop")
        "attacks" -> listOf("target", "toggle", "smartDebuff", "spellDebuff", "spellDamage", "skill", "normalAttack", "kamisamaniNattaHi")
        else -> types
    }
    val conditions = "world pLevel pMaxSpellType battleTypes difficulty roundCurrent roundLeft roundTotal floor pActionCooldown pActionHistory pActionCounts pTotalActionCounts pHP pMP pSP pOC pSpiritStatus pEffects pIgnoredEffects pEffectStacks monsters activeMonsters defeatedMonsters bosses activeBosses defeatedBosses mLevel mWithoutEffects tName tTypes tClasses tPowerLevel tIndex tHP tMP tSP tEffects tIgnoredEffects tEffectStacks tDaysSinceUpdate".split(' ')
    val withNames = setOf("spellSupport", "item", "toggle", "smartDebuff", "spellDebuff", "spellDamage", "skill")
    val ranges = "pLevel roundCurrent roundLeft roundTotal floor pHP pMP pSP pOC monsters activeMonsters defeatedMonsters bosses activeBosses defeatedBosses mLevel tPowerLevel tIndex tHP tMP tSP tDaysSinceUpdate".split(' ').toSet()
    val rangeLists = "pActionCooldown pActionHistory pActionCounts pTotalActionCounts pEffects pIgnoredEffects pEffectStacks mWithoutEffects tEffects tIgnoredEffects tEffectStacks".split(' ').toSet()
    fun extras(type: String) = when (type) {
        "target" -> listOf("priorityRule")
        "toggle" -> listOf("toggled")
        "stop" -> listOf("customMessage")
        "kamisamaniNattaHi" -> listOf("code")
        "smartDebuff" -> listOf("targetCount", "bottomUp", "tailSkip", "maxAtFirst", "minMonstersLeft")
        else -> emptyList()
    }
    private val PRIORITY_RULES = listOf("Top Down", "Bottom Up", "Current HP Low to High", "Current HP High to Low", "Current HP Percent Low to High", "Current HP Percent High to Low")
    private val TOGGLE = listOf("Spirit", "Defend", "Focus")
    private val ITEMS = listOf("Mystic Gem", "Health Gem", "Health Draught", "Health Potion", "Health Elixir", "Mana Gem", "Mana Draught", "Mana Potion", "Mana Elixir", "Spirit Gem", "Spirit Draught", "Spirit Potion", "Spirit Elixir", "Last Elixir", "Infusion of Flames", "Infusion of Frost", "Infusion of Storms", "Infusion of Lightning", "Infusion of Divinity", "Infusion of Darkness", "Scroll of Swiftness", "Scroll of Protection", "Scroll of the Avatar", "Scroll of Absorption", "Scroll of Shadows", "Scroll of Life", "Scroll of the Gods", "Caffeinated Candy", "Energy Drink", "Flower Vase", "Bubble-Gum")
    private val SKILLS = listOf("Shield Bash", "Vital Strike", "Merciful Blow", "Iris Strike", "Backstab", "Frenzied Blows", "Great Cleave", "Rending Blow", "Shatter Strike", "Skyward Sword", "Concussive Strike", "Flee", "Scan", "FUS RO DAH", "Orbital Friendship Cannon")
    private val SPELLS_SUPPORT = listOf("Cure", "Full-Cure", "Regen", "Haste", "Protection", "Absorb", "Shadow Veil", "Spark of Life", "Spirit Shield", "Heartseeker", "Arcane Focus")
    private val SPELLS_DAMAGE = listOf("T1", "T2", "T3", "Fiery Blast", "Inferno", "Flames of Loki", "Freeze", "Blizzard", "Fimbulvetr", "Gale", "Downburst", "Storms of Njord", "Shockblast", "Chained Lightning", "Wrath of Thor", "Smite", "Banishment", "Paradise Lost", "Corruption", "Disintegrate", "Ragnarok")
    private val SPELLS_DEBUFF = listOf("Drain", "Weaken", "Imperil", "Slow", "Sleep", "Confuse", "Blind", "Silence", "Immobilize")
    private val PLAYER_EFFECTS = listOf("Channeling", "Regeneration", "Replenishment", "Refreshment", "Regen", "Heartseeker", "Arcane Focus", "Hastened", "Protection", "Absorbing Ward", "Shadow Veil", "Spark of Life", "Spirit Shield", "Infused Flames", "Infused Frost", "Infused Storm", "Infused Lightning", "Infused Divinity", "Infused Darkness", "Energized", "Sleeper Imprint", "Kicking Ass", "Overwhelming Strikes", "Ether Tap", "Cloak of the Fallen", "Blessing of the RiddleMaster", "Defending", "Focusing")
    private val MONSTER_TYPES = listOf("Normal", "Rare", "Legendary", "Ultimate", "Arena300", "Arena400", "Arena500")
    private val MONSTER_CLASSES = listOf("Arthropod", "Avion", "Beast", "Celestial", "Daimon", "Dragonkin", "Elemental", "Giant", "Humanoid", "Mechanoid", "Reptilian", "Sprite", "Undead")
    private val BATTLE_TYPES = listOf("Arena", "Encounter", "Colosseum", "Battle1000", "Item", "Tower")
    private val MONSTER_EFFECTS = listOf("Vital Theft", "Ether Theft", "Spirit Theft", "Weakened", "Imperiled", "Slowed", "Asleep", "Confused", "Blinded", "Silenced", "Immobilized", "Stunned", "Penetrated Armor", "Bleeding Wound", "Spreading Poison", "Coalesced Mana", "Searing Skin", "Freezing Limbs", "Turbulent Air", "Deep Burns", "Breached Defense", "Blunted Attack", "Burning Soul", "Ripened Soul", "Fury of the Sisters", "Lamentations of the Future", "Screams of the Past", "Wails of the Present", "Absorbing Ward")
    fun conditionOptions(key: String): List<String> = when (key) {
        "world" -> listOf("Persistent", "Isekai")
        "pMaxSpellType" -> listOf("fire", "cold", "elec", "wind", "holy", "dark")
        "difficulty" -> listOf("Normal", "Hard", "Nightmare", "Hell", "Nintendo", "IWBTH", "PFUDOR")
        "battleTypes" -> BATTLE_TYPES
        "tTypes" -> MONSTER_TYPES
        "tClasses" -> MONSTER_CLASSES
        "pActionHistory", "pActionCounts", "pTotalActionCounts", "pActionCooldown" ->
            (if (key == "pActionCooldown") emptyList() else listOf("Attack") + TOGGLE) + SKILLS + SPELLS_SUPPORT + ITEMS + SPELLS_DAMAGE + SPELLS_DEBUFF
        "pEffects", "pIgnoredEffects", "pEffectStacks" -> PLAYER_EFFECTS
        "tEffects", "tIgnoredEffects", "tEffectStacks", "mWithoutEffects" -> MONSTER_EFFECTS
        else -> emptyList()
    }
    fun choices(key: String) = when (key) {
        "priorityRule" -> PRIORITY_RULES
        "item", "itemDuration", "itemInstant" -> ITEMS
        "spellSupport", "spellDuration", "spellInstant" -> SPELLS_SUPPORT
        "toggle" -> TOGGLE
        "spellDamage" -> SPELLS_DAMAGE
        "spellDebuff", "smartDebuff" -> SPELLS_DEBUFF
        "skill" -> SKILLS
        else -> emptyList()
    }
}
