package com.mitahv.mobile.model

sealed interface BattleModesPage {
    val messages: List<String>

    data class RingOfBlood(
        val tokenBalance: String,
        val challenges: List<BattleModesChallenge>,
        override val messages: List<String>,
        val emptyMessage: String = "",
    ) : BattleModesPage

    data class GrindFest(
        val rounds: String,
        val staminaCost: String,
        val rules: List<String>,
        val start: NativeAction?,
        override val messages: List<String>,
    ) : BattleModesPage

    data class Tower(
        val ranking: String,
        val floor: String,
        val difficulty: String,
        val attempts: String,
        val clears: String,
        val rules: List<String>,
        val start: NativeAction?,
        override val messages: List<String>,
    ) : BattleModesPage

    data class ItemWorld(
        val filters: List<BattleModesFilter>,
        val equipment: List<BattleModesEquipment>,
        override val messages: List<String>,
    ) : BattleModesPage
}

data class BattleModesChallenge(
    val name: String,
    val highestClear: String,
    val minimumLevel: String,
    val rounds: String,
    val experienceMultiplier: String,
    val entryCost: String,
    val clearBonus: String,
    val status: String,
    val start: NativeAction?,
)

data class BattleModesFilter(val name: String, val selected: Boolean, val action: NativeAction)

data class BattleModesMaterial(val name: String, val required: Long, val owned: Long)

data class BattleModesEquipment(
    val id: String,
    val name: String,
    val group: String,
    val tier: String,
    val worldLevel: String,
    val rounds: String,
    val monsterLevel: String,
    val difficulty: String,
    val materials: List<BattleModesMaterial>,
    val selectable: Boolean,
    val unavailableReason: String?,
    val start: NativeAction?,
    val equipmentDetails: String = "",
    val originalEquipmentName: String? = null,
)
