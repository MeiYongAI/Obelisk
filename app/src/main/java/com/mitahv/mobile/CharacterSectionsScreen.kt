package com.mitahv.mobile

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mitahv.mobile.i18n.messageText
import com.mitahv.mobile.model.*

private val itemSlotLabel = Regex("Slot ([SN]?[0-9]+)")
private val equipmentSetLabel = Regex("Set ([0-9]+)")
private val abilitySlotLabel = Regex("(Major|Supportive|Protection Augment|Drain Augment) ([0-9]+)")

@Composable
internal fun characterLabel(text: String): String {
    itemSlotLabel.matchEntire(text)?.let { return stringResource(R.string.character_ui_slot, it.groupValues[1]) }
    equipmentSetLabel.matchEntire(text)?.let { return stringResource(R.string.character_ui_set, it.groupValues[1]) }
    abilitySlotLabel.matchEntire(text)?.let {
        val label = when (it.groupValues[1]) {
            "Major" -> R.string.character_ui_major_slot
            "Supportive" -> R.string.character_ui_supportive_slot
            "Protection Augment" -> R.string.character_ui_protection_slot
            else -> R.string.character_ui_drain_slot
        }
        return stringResource(label, it.groupValues[2])
    }
    return when (text) {
        "(Empty)" -> stringResource(R.string.character_ui_empty)
        "Battle Item" -> stringResource(R.string.character_ui_battle_item)
        "Ability" -> stringResource(R.string.game_ui_abilities)
        "Ability Slots" -> stringResource(R.string.game_ui_ability_slots)
        "Item Inventory" -> stringResource(R.string.game_ui_item_inventory)
        else -> messageText(text)
    }
}

@Composable
internal fun CharacterSectionsScreen(
    page: CharacterSectionsPage,
    access: PageInteraction,
    onAction: (NativeAction) -> Unit,
    onDraftChanged: (Boolean) -> Unit = {},
    responseRevision: Int = 0,
) {
    when (page.route) {
        HvRoute.Training -> TrainingScreen(page, !access.canSubmit, responseRevision, onAction)
        HvRoute.ItemInventory -> InventoryScreen(page, access, onAction)
        HvRoute.Settings -> GameSettingsScreen(page, access, onAction, onDraftChanged, responseRevision)
        else -> Unit
    }
}
