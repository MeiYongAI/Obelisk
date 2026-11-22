package com.mitahv.mobile

import androidx.compose.ui.res.stringResource
import com.mitahv.mobile.i18n.gameText
import com.mitahv.mobile.i18n.messageText

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.model.BattleModesChallenge
import com.mitahv.mobile.model.BattleModesEquipment
import com.mitahv.mobile.model.BattleModesPage
import com.mitahv.mobile.model.NativeAction

@Composable
internal fun BattleModesScreen(
    page: BattleModesPage,
    busy: Boolean,
    onAction: (NativeAction) -> Unit,
    onStartBattle: (NativeAction) -> Unit,
    responseRevision: Int = 0,
) {
    val equipmentList = (page as? BattleModesPage.ItemWorld)?.let { it.filters to it.equipment.map { item -> item.id } }
    var selectedEquipmentId by remember(equipmentList, responseRevision) { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(page.messages) { message -> Text(messageText(message), color = MaterialTheme.colorScheme.primary) }
        when (page) {
            is BattleModesPage.RingOfBlood -> {
                if (page.tokenBalance.isNotBlank()) item {
                    Text(messageText(page.tokenBalance), style = MaterialTheme.typography.titleMedium)
                }
                if (page.challenges.isEmpty()) item {
                    Text(if (page.emptyMessage.isBlank()) stringResource(R.string.game_ui_no_challenges) else messageText(page.emptyMessage),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(page.challenges) { challenge -> ChallengeCard(challenge, busy, onStartBattle) }
            }
            is BattleModesPage.GrindFest -> {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.game_ui_grindfest), style = MaterialTheme.typography.headlineSmall)
                            BattleModeStat(stringResource(R.string.game_ui_rounds_label), page.rounds)
                            BattleModeStat(stringResource(R.string.game_ui_stamina), page.staminaCost)
                            StartBattleButton(page.start, busy, stringResource(R.string.game_ui_start_grindfest), onStartBattle)
                        }
                    }
                }
                item { BattleModeRules(page.rules, responseRevision) }
            }
            is BattleModesPage.Tower -> {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.game_ui_tower), style = MaterialTheme.typography.headlineSmall)
                            BattleModeStat(stringResource(R.string.game_ui_ranking), page.ranking)
                            BattleModeStat(stringResource(R.string.game_ui_floor), page.floor)
                            if (page.difficulty.isNotBlank()) Text(messageText(page.difficulty), style = MaterialTheme.typography.titleMedium)
                            HorizontalDivider()
                            BattleModeStat(stringResource(R.string.game_ui_daily_attempts), page.attempts)
                            BattleModeStat(stringResource(R.string.game_ui_daily_clears), page.clears)
                            StartBattleButton(page.start, busy, stringResource(R.string.game_ui_unavailable), onStartBattle)
                        }
                    }
                }
                item { BattleModeRules(page.rules, responseRevision) }
            }
            is BattleModesPage.ItemWorld -> {
                if (page.filters.isNotEmpty()) item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        page.filters.forEach { filter ->
                            FilterChip(selected = filter.selected, enabled = !busy,
                                onClick = { if (!filter.selected) onAction(filter.action) }, label = { Text(messageText(filter.name)) })
                        }
                    }
                }
                if (page.equipment.isEmpty()) item { Text(stringResource(R.string.game_ui_no_equipment), style = MaterialTheme.typography.bodyLarge) }
                items(page.equipment, key = { it.id }) { equipment ->
                    ItemWorldEquipmentCard(equipment, equipment.id == selectedEquipmentId, busy, responseRevision,
                        onSelect = { selectedEquipmentId = if (selectedEquipmentId == equipment.id) null else equipment.id },
                        onStartBattle = onStartBattle)
                }
            }
        }
    }
}

@Composable
private fun ChallengeCard(challenge: BattleModesChallenge, busy: Boolean, onStartBattle: (NativeAction) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(gameText(challenge.name), style = MaterialTheme.typography.titleLarge)
            Text(messageText(listOf(challenge.minimumLevel, challenge.rounds.takeIf(String::isNotBlank)?.let { stringResource(R.string.game_ui_round_count, it) },
                challenge.experienceMultiplier).filterNotNull().filter(String::isNotBlank).joinToString(" · ")),
                style = MaterialTheme.typography.bodyMedium)
            BattleModeStat(stringResource(R.string.game_ui_highest_clear), challenge.highestClear)
            BattleModeStat(stringResource(R.string.game_ui_entry_cost_label), challenge.entryCost)
            BattleModeStat(stringResource(R.string.game_ui_clear_bonus), challenge.clearBonus)
            if (challenge.status.isNotBlank()) Text(messageText(challenge.status), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            StartBattleButton(challenge.start, busy, stringResource(R.string.game_ui_unavailable), onStartBattle)
        }
    }
}

@Composable
private fun ItemWorldEquipmentCard(
    equipment: BattleModesEquipment, selected: Boolean, busy: Boolean, responseRevision: Int,
    onSelect: () -> Unit, onStartBattle: (NativeAction) -> Unit,
) {
    var showEquipmentDetails by remember(equipment.id, responseRevision) { mutableStateOf(false) }
    OutlinedCard(onClick = onSelect, enabled = !busy && equipment.selectable, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = null, enabled = !busy && equipment.selectable)
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EquipmentNameText(equipment.name, originalName = equipment.originalEquipmentName,
                        style = MaterialTheme.typography.titleMedium)
                    Text(messageText(listOf(equipment.group, equipment.tier).filter(String::isNotBlank).joinToString(" · ")),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (selected) {
                HorizontalDivider()
                BattleModeStat(stringResource(R.string.game_ui_world_level), equipment.worldLevel)
                BattleModeStat(stringResource(R.string.game_ui_rounds_label), equipment.rounds)
                BattleModeStat(stringResource(R.string.game_ui_monster_level), equipment.monsterLevel)
                BattleModeStat(stringResource(R.string.game_ui_difficulty), equipment.difficulty)
                if (equipment.equipmentDetails.isNotBlank()) {
                    TextButton(onClick = { showEquipmentDetails = true }) {
                        Text(stringResource(R.string.game_ui_equipment_stats))
                    }
                }
                equipment.materials.forEach { material ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(gameText(material.name), Modifier.weight(1f))
                        Text(stringResource(R.string.game_ui_material_count, material.required, material.owned),
                            color = if (material.required > material.owned) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    }
                }
                equipment.unavailableReason?.let { Text(messageText(it), color = MaterialTheme.colorScheme.error) }
                StartBattleButton(equipment.start, busy, stringResource(R.string.game_ui_enter_itemworld), onStartBattle)
            }
        }
    }
    if (showEquipmentDetails) BazaarDetailsDialog(equipment.name, listOf("" to equipment.equipmentDetails),
        equipmentName = true, originalEquipmentName = equipment.originalEquipmentName) { showEquipmentDetails = false }
}

@Composable
private fun BattleModeStat(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(messageText(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(messageText(value), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StartBattleButton(action: NativeAction?, busy: Boolean, fallback: String, onStartBattle: (NativeAction) -> Unit) {
    var confirmStart by remember(action) { mutableStateOf(false) }
    Button(onClick = {
        action?.let {
            if (it.confirmation != null) confirmStart = true else onStartBattle(it)
        }
    }, enabled = action != null && !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(action?.label?.let { messageText(it) } ?: fallback)
    }
    if (confirmStart && action != null) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text(messageText(action.label)) },
            text = { Text(messageText(action.confirmation.orEmpty()), Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    confirmStart = false
                    onStartBattle(action)
                }) { Text(stringResource(R.string.app_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStart = false }) { Text(stringResource(R.string.app_cancel)) }
            },
        )
    }
}

@Composable
private fun BattleModeRules(rules: List<String>, responseRevision: Int) {
    if (rules.isEmpty()) return
    var expanded by remember(rules, responseRevision) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(R.string.game_ui_rules))
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        if (expanded) rules.forEach { Text(messageText(it), style = MaterialTheme.typography.bodyMedium) }
    }
}
