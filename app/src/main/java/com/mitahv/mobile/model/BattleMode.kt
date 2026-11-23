package com.mitahv.mobile.model

enum class BattleScene {
    General, Arena, Encounter, Colosseum, Battle1000, Item, Tower;

    companion object {
        private val oneRound = Regex("""\(Round\s+1\s*/\s*1\)""", RegexOption.IGNORE_CASE)
        fun fromLog(lines: List<String>): BattleScene? = lines.firstNotNullOfOrNull { line ->
            if (!line.startsWith("Initializing ")) return@firstNotNullOfOrNull null
            val start = line.removePrefix("Initializing ")
            when {
                start.startsWith("arena challenge", true) -> if (oneRound.containsMatchIn(start)) Colosseum else Arena
                start.startsWith("random encounter", true) -> Encounter
                start.startsWith("Grindfest", true) -> Battle1000
                start.startsWith("Item World", true) -> Item
                start.startsWith("The Tower", true) -> Tower
                else -> null
            }
        }
        fun fromLobby(route: HvRoute?) = when (route) {
            HvRoute.Battle -> Arena
            HvRoute.RingOfBlood -> Colosseum
            HvRoute.GrindFest -> Battle1000
            HvRoute.ItemWorld -> Item
            HvRoute.Tower -> Tower
            else -> null
        }
    }
}

enum class BattleStyle {
    OneHanded, TwoHanded, DualWielding, NitenIchiryu, Staff, Unarmed, OneMage, TwoMage, DualMage, NitenMage;

    val key: String get() = when (this) {
        OneMage -> "1H_Mage"
        TwoMage -> "2H_Mage"
        DualMage -> "DW_Mage"
        NitenMage -> "NI_Mage"
        else -> name
    }
    fun slot(scene: BattleScene) = "${key}_${scene.name}"
}

val BattleLoadout.style: BattleStyle? get() {
    fun armed(physical: BattleStyle, mage: BattleStyle) = when (isBattlemage) {
        true -> mage
        false -> physical
        null -> null
    }
    return when (family) {
        WeaponFamily.One -> armed(BattleStyle.OneHanded, BattleStyle.OneMage)
        WeaponFamily.Two -> armed(BattleStyle.TwoHanded, BattleStyle.TwoMage)
        WeaponFamily.Dual -> armed(BattleStyle.DualWielding, BattleStyle.DualMage)
        WeaponFamily.Niten -> armed(BattleStyle.NitenIchiryu, BattleStyle.NitenMage)
        WeaponFamily.Staff -> BattleStyle.Staff
        WeaponFamily.Unarmed -> BattleStyle.Unarmed
        null -> null
    }
}
