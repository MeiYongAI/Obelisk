package com.mitahv.mobile.model

data class ArenaPage(
    val postToken: String,
    val challenges: List<ArenaChallenge>,
    val emptyMessage: String = "",
)

data class ArenaChallenge(
    val id: Int?,
    val name: String,
    val highestClear: String,
    val minimumLevel: String,
    val rounds: String,
    val experienceMultiplier: String,
    val entryCost: String,
    val clearBonus: String,
    val availabilityInfo: String,
    val available: Boolean,
)

data class BattlePage(
    val token: String,
    val playerVitals: List<BattleVital>,
    val enemies: List<BattleEnemy>,
    val quickActions: List<BattleActionOption>,
    val skills: List<BattleActionOption>,
    val spells: List<BattleActionOption>,
    val items: List<BattleActionOption>,
    val log: List<String>,
    val completion: String,
    val experienceBarWidth: Float,
    val scene: BattleScene? = null,
    val turnKey: Any = Any(),
    val playerEffects: List<BattleEffect> = emptyList(),
    val controls: List<BattleControl> = emptyList(),
    val isComplete: Boolean = false,
    val continuation: BattleContinuation? = null,
    val completionLines: List<String> = emptyList(),
    val itemText: Map<String, BattleItemText> = emptyMap(),
) {
    val canContinue: Boolean get() = continuation != null
}

/** A missing GET action means the official continue control reloads the current battle URL. */
data class BattleContinuation(
    val label: String,
    val action: NativeAction? = null,
    val destination: HvRoute? = null,
)

data class BattleItemText(val name: String, val descriptionHtml: String)

data class BattleVital(val name: String, val value: String, val fraction: Float)

data class BattleEnemy(
    val target: Int,
    val name: String,
    val level: String,
    val healthFraction: Float,
    val magicFraction: Float? = null,
    val spiritFraction: Float? = null,
    val effects: List<BattleEffect> = emptyList(),
    val targetable: Boolean = false,
)

data class BattleEffect(val name: String, val description: String, val duration: String)

data class BattleControl(val mode: String, val enabled: Boolean, val selected: Boolean = false)

data class BattleActionOption(
    val id: String,
    val name: String,
    val mode: String,
    val skill: Int,
    val targetsEnemy: Boolean,
    val enabled: Boolean = true,
    val description: String = "",
    val manaCost: Int? = null,
    val chargeCost: Int? = null,
    val cooldown: Int? = null,
    val group: String = "",
)

data class BattleAction(
    val mode: String,
    val target: Int = 0,
    val skill: Int = 0,
)
