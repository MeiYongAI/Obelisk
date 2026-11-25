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
    val round: BattleRoundFacts = BattleRoundFacts(),
    val encounterKey: Any = Any(), // Retained across pane updates and a confirmed next-round request.
    val turnKey: Any = Any(),
    val playerEffects: List<BattleEffect> = emptyList(),
    val controls: List<BattleControl> = emptyList(),
    val isComplete: Boolean = false,
    val continuation: BattleContinuation? = null,
    val completionLines: List<String> = emptyList(),
    val itemText: Map<String, BattleItemText> = emptyMap(),
    val remainingCooldowns: Map<String, Int> = emptyMap(),
    val difficulty: String? = null,
    val monsterEffects: Map<String, List<BattleEffect>> = emptyMap(),
) {
    // These exact fragments describe this instance's parsed lists. A copy can replace a list
    // or enrich the item dictionary, so copy() intentionally starts with no recorded fragments.
    internal var parsedPaneHtml: Map<String, String> = emptyMap()

    internal fun afterRoundContinuation(previous: BattlePage): BattlePage {
        if (isComplete || !previous.isComplete || previous.continuation?.isNextRound != true) return this
        if (scene != null && previous.scene != null && scene != previous.scene) return this
        if (round.total != null && previous.round.total != null && round.total != previous.round.total) return this
        val lastRound = previous.round.current
        val expectedRound = lastRound?.plus(1)
        val total = round.total ?: previous.round.total
        if (expectedRound != null && total != null && expectedRound > total) return this
        if (round.current != null && expectedRound != null && round.current != expectedRound) return this
        // The server's next-round control still identifies the encounter if its log was truncated.
        return copy(encounterKey = previous.encounterKey, scene = scene ?: previous.scene,
            difficulty = difficulty ?: previous.difficulty,
            round = round.copy(current = round.current ?: expectedRound, total = total).after(previous.round))
    }

    val canContinue: Boolean get() = continuation != null
    internal fun effectsOf(enemy: BattleEnemy): List<BattleEffect> =
        if (enemy.targetable && enemy.effects.size == 6) monsterEffects[enemy.name] ?: enemy.effects else enemy.effects
}

/** A missing GET action means the official continue control reloads the current battle URL. */
data class BattleContinuation(
    val label: String,
    val action: NativeAction? = null,
    val destination: HvRoute? = null,
) {
    val isNextRound: Boolean get() = action == null && destination == null
}

data class BattleItemText(val name: String, val descriptionHtml: String)

data class BattleVital(val name: String, val value: String, val fraction: Float, val measured: Boolean = true)

data class BattleEnemy(
    val target: Int,
    val name: String,
    val level: String,
    val healthFraction: Float,
    val magicFraction: Float? = null,
    val spiritFraction: Float? = null,
    val effects: List<BattleEffect> = emptyList(),
    val targetable: Boolean = false,
    val healthMeasured: Boolean = true,
)

data class BattleEffect(val name: String, val description: String, val duration: String,
    val iconKey: String? = null, val unknownStack: Boolean = false)

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
    val iconKey: String? = null,
    val itemId: Int? = null,
)

data class BattleAction(
    val mode: String,
    val target: Int = 0,
    val skill: Int = 0,
)
