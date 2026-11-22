package com.mitahv.mobile.model

import kotlin.math.ceil

data class CharacterSectionsPage(
    val route: HvRoute,
    val summary: List<String> = emptyList(),
    val notices: List<String> = emptyList(),
    val navigation: List<CharacterSectionLink> = emptyList(),
    val groups: List<CharacterSectionGroup> = emptyList(),
    val forms: List<CharacterSectionForm> = emptyList(),
    val trainingProgress: CharacterTrainingProgress? = null,
    val equipmentSelection: Boolean = false,
) {
    /** Settings groups share one HTML form, so edits across groups must be submitted together. */
    fun combinedSettingsForm(): CharacterSectionForm? {
        if (route != HvRoute.Settings) return null
        return forms.firstOrNull()?.copy(fields = forms.flatMap { it.fields }, description = "")
    }

    fun abilityTreesFor(slot: CharacterAbilitySlotInfo): List<CharacterSectionLink> =
        navigation.filter { slot.family in it.abilityFamilies && (it.selected || it.action != null) }

    fun preferredAbilityTreeFor(slot: CharacterAbilitySlotInfo): CharacterSectionLink? {
        val trees = abilityTreesFor(slot)
        val defaultTree = when (slot.family) {
            "r" -> "general"
            "g" -> "supportive1"
            "b" -> "supportive2"
            "p" -> "deprecating2"
            else -> null
        }
        return trees.firstOrNull { it.selected }
            ?: trees.firstOrNull { it.abilityTreeId == defaultTree }
            ?: trees.firstOrNull()
    }
}

data class CharacterTrainingProgress(
    val name: String,
    val initialPercent: Double,
    val totalSeconds: Long?,
    val endsAtServer: Long?,
    val refreshAction: NativeAction?,
    val cancelAction: NativeAction?,
) {
    fun percentAfter(elapsedMillis: Long): Double =
        (initialPercent + if (totalSeconds != null && totalSeconds > 0)
            elapsedMillis.coerceAtLeast(0).toDouble() / (totalSeconds * 10.0) else 0.0).coerceIn(0.0, 100.0)

    fun remainingSecondsAfter(elapsedMillis: Long): Long? {
        val percent = percentAfter(elapsedMillis)
        if (percent >= 100.0) return 0
        val total = totalSeconds?.takeIf { it > 0 } ?: return null
        return ceil(total * (100.0 - percent) / 100.0).toLong()
    }
}

data class CharacterSectionLink(
    val label: String,
    val action: NativeAction?,
    val selected: Boolean = false,
    val abilityTreeId: String? = null,
    val abilityFamilies: Set<String> = emptySet(),
)
data class CharacterSectionGroup(val title: String, val rows: List<CharacterSectionRow>)
data class CharacterSectionRow(
    val title: String,
    val detail: String = "",
    val value: String = "",
    val actions: List<NativeAction> = emptyList(),
    val form: CharacterSectionForm? = null,
    val expandedDetail: String = "",
    val ability: CharacterAbilityInfo? = null,
    val abilitySlot: CharacterAbilitySlotInfo? = null,
    val originalEquipmentName: String? = null,
    val training: CharacterTrainingInfo? = null,
    val inventory: CharacterInventoryItemInfo? = null,
)

enum class CharacterInventoryKind { General, Scroll, Infusion }

data class CharacterInventoryItemInfo(
    val id: String,
    val category: String,
    val kind: CharacterInventoryKind,
)

data class CharacterInventorySlotInfo(
    val id: String,
    val label: String,
    val kind: CharacterInventoryKind,
)

data class CharacterTrainingInfo(
    val effect: String,
    val cost: String?,
    val duration: String?,
    val currentLevel: Int?,
    val levelCap: Int?,
)

data class CharacterAbilityInfo(
    val id: String,
    val family: String,
    val acquired: Boolean,
    val equippedSlotIds: List<String> = emptyList(),
    val upgradeCost: String = "",
    val description: String = "",
    val tiers: List<CharacterAbilityTier> = emptyList(),
    val upgradeRequirements: CharacterAbilityUpgradeRequirements? = null,
) {
    val currentTier: Int get() = tiers.count { it.state == CharacterAbilityTierState.Acquired }
    val maxTier: Int get() = tiers.size
}

data class CharacterAbilityUpgradeRequirements(
    val requiredLevel: Int,
    val requiredAbilityPoints: Int,
    val currentLevel: Int?,
    val availableAbilityPoints: Int?,
) {
    val levelMet: Boolean? get() = currentLevel?.let { it >= requiredLevel }
    val missingAbilityPoints: Int? get() = availableAbilityPoints?.let { (requiredAbilityPoints - it).coerceAtLeast(0) }
}

enum class CharacterAbilityTierState { Acquired, Available, Locked }

data class CharacterAbilityTier(
    val number: Int,
    val state: CharacterAbilityTierState,
    val requirements: String = "",
    val effects: String = "",
)

data class CharacterAbilitySlotInfo(
    val id: String,
    val family: String,
    val unlocked: Boolean,
    val equippedName: String = "",
)

enum class CharacterFieldKind { Text, Choice, Toggle }
data class CharacterFieldOption(
    val value: String,
    val label: String,
    val enabled: Boolean = true,
    val detail: String = "",
    val group: String = "",
    val originalEquipmentName: String? = null,
)
data class CharacterSectionField(
    val name: String,
    val label: String,
    val value: String,
    val kind: CharacterFieldKind,
    val options: List<CharacterFieldOption> = emptyList(),
    val enabled: Boolean = true,
    val checked: Boolean = false,
    val maxLength: Int? = null,
    val hint: String = "",
)

data class CharacterSectionForm(
    val title: String,
    val action: NativeAction,
    val fields: List<CharacterSectionField>,
    val inventorySlot: CharacterInventorySlotInfo? = null,
    val description: String = "",
) {
    fun canSubmit(values: Map<String, String>): Boolean = fields.filter { it.enabled }.all { field ->
        val value = values[field.name] ?: field.value
        (field.maxLength == null || value.length <= field.maxLength) &&
            (field.kind != CharacterFieldKind.Choice || field.options.any { it.value == value && it.enabled })
    }

    /** Preserve every other successful control in the original form. Unchecked checkboxes are omitted. */
    fun actionWith(values: Map<String, String>, toggles: Map<String, Boolean>): NativeAction {
        val editable = fields.filter { it.enabled }
        val names = editable.map { it.name }.toSet()
        val replacements = editable.mapNotNull { field ->
            if (field.kind == CharacterFieldKind.Toggle) {
                if (toggles[field.name] ?: field.checked) field.name to field.value else null
            } else {
                val value = values[field.name] ?: field.value
                require(field.maxLength == null || value.length <= field.maxLength)
                if (field.kind == CharacterFieldKind.Choice) {
                    require(field.options.any { it.value == value && it.enabled })
                }
                field.name to value
            }
        }
        return action.copy(fields = action.fields.filterNot { it.first in names } + replacements)
    }
}
