package com.mitahv.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.i18n.gameText
import com.mitahv.mobile.i18n.messageText
import com.mitahv.mobile.model.*

private data class EquipmentDetail(val name: String, val text: String, val originalName: String? = null)

@Composable
internal fun EquipmentScreen(page: CharacterSectionsPage, busy: Boolean, onAction: (NativeAction) -> Unit,
    onDraftChanged: (Boolean) -> Unit, responseRevision: Int) {
    val slots = page.groups.firstOrNull { it.title == "Equipment Slots" }?.rows.orEmpty()
    if (page.equipmentSelection) {
        EquipmentSelection(page, slots.firstOrNull(), busy, onAction, onDraftChanged, responseRevision)
    } else {
        EquipmentOverview(page, slots, busy, onAction)
    }
}

@Composable
private fun EquipmentOverview(page: CharacterSectionsPage, slots: List<CharacterSectionRow>, busy: Boolean,
    onAction: (NativeAction) -> Unit) {
    var stats by rememberSaveable { mutableStateOf(false) }
    var setsOpen by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<EquipmentDetail?>(null) }
    val listStates = rememberSaveableStateHolder()
    val statGroups = page.groups.filter { it.title != "Equipment Slots" && it.rows.isNotEmpty() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (page.navigation.isNotEmpty()) TextButton(onClick = { setsOpen = true }, enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text(characterLabel(page.navigation.firstOrNull { it.selected }?.label ?: page.navigation.first().label))
                Icon(Icons.Default.ExpandMore, null, Modifier.padding(start = 4.dp).size(20.dp))
            }
            if (statGroups.isNotEmpty()) SingleChoiceSegmentedButtonRow(Modifier.weight(1f).heightIn(min = 48.dp)) {
                listOf(R.string.equipment_ui_loadout, R.string.equipment_ui_stats).forEachIndexed { index, label ->
                    SegmentedButton(selected = stats == (index == 1), onClick = { stats = index == 1 },
                        shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(stringResource(label)) }
                }
            }
        }
        listStates.SaveableStateProvider(if (stats) "stats" else "slots") {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(page.notices) { EquipmentNotice(it) }
                if (stats) items(statGroups, key = { it.title }) { EquipmentStats(it) }
                else items(slots, key = { it.title }) { slot ->
                    EquipmentSlot(slot, busy, onClick = slot.actions.firstOrNull()?.let { action -> { onAction(action) } },
                        onDetail = { detail = EquipmentDetail(slot.detail, slot.expandedDetail, slot.originalEquipmentName) })
                }
            }
        }
    }
    if (setsOpen) MobileChoiceSheet(title = stringResource(R.string.equipment_ui_set),
        choices = page.navigation.mapIndexed { index, link ->
            MobileChoice(index.toString(), characterLabel(link.label), !busy && (link.selected || link.action != null))
        }, selectedValue = page.navigation.indexOfFirst { it.selected }.toString(), searchable = false,
        onSelect = { index -> setsOpen = false; page.navigation[index.toInt()].action?.let(onAction) },
        onDismiss = { setsOpen = false })
    detail?.let { EquipmentDetails(it) { detail = null } }
}

@Composable
private fun EquipmentSlot(slot: CharacterSectionRow, busy: Boolean, onClick: (() -> Unit)?, onDetail: () -> Unit,
    current: Boolean = false) {
    val empty = slot.emptyEquipmentSlot
    Surface(onClick = { onClick?.invoke() }, enabled = !busy && onClick != null,
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = MaterialTheme.shapes.medium,
                color = if (empty) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.secondaryContainer) {
                Icon(EquipmentIcons.forSlot(slot.title), null, Modifier.padding(10.dp).size(24.dp),
                    tint = if (empty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (current) stringResource(R.string.equipment_ui_current) else messageText(slot.title),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (empty) {
                    val status = if (slot.detail.isBlank() || slot.detail.equals("(empty)", ignoreCase = true))
                        stringResource(R.string.character_ui_empty) else gameText(slot.detail)
                    Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else EquipmentNameText(slot.detail, originalName = slot.originalEquipmentName,
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (slot.expandedDetail.isNotBlank()) IconButton(onClick = onDetail) {
                Icon(Icons.Default.Info, stringResource(R.string.game_ui_details), Modifier.size(20.dp))
            } else if (onClick != null) Icon(Icons.Default.ChevronRight, null,
                Modifier.padding(horizontal = 12.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun EquipmentStats(group: CharacterSectionGroup) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(messageText(group.title), Modifier.padding(bottom = 4.dp).semantics { heading() },
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            group.rows.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(messageText(row.title), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(messageText(row.value), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun EquipmentSelection(page: CharacterSectionsPage, slot: CharacterSectionRow?, busy: Boolean,
    onAction: (NativeAction) -> Unit, onDraftChanged: (Boolean) -> Unit, responseRevision: Int) {
    val form = page.forms.firstOrNull { it.fields.any { field -> field.name == "eqids[]" } }
    val field = form?.fields?.firstOrNull { it.name == "eqids[]" }
    val initial = field?.value.orEmpty()
    var selected by rememberSaveable(slot?.title, initial, responseRevision) { mutableStateOf(initial) }
    var detail by remember { mutableStateOf<EquipmentDetail?>(null) }
    val currentOnDraftChanged by rememberUpdatedState(onDraftChanged)
    val dirty = selected != initial
    LaunchedEffect(dirty, responseRevision) { currentOnDraftChanged(dirty) }
    DisposableEffect(Unit) { onDispose { currentOnDraftChanged(false) } }
    val unequip = page.groups.flatMap { it.rows }.flatMap { it.actions }
        .firstOrNull { action -> action.fields.any { it == "action" to "unequip" } }
    val options = field?.options.orEmpty().filter { it.value.isNotBlank() }
    val back = slot?.actions?.firstOrNull()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (back != null) IconButton(onClick = { onAction(back) }, enabled = !busy) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.equipment_ui_back))
            }
            Text(slot?.title?.let { messageText(it) }.orEmpty(), style = MaterialTheme.typography.titleMedium)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().selectableGroup(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (slot != null) item(key = "current") {
                EquipmentSlot(slot, busy, onClick = if (slot.expandedDetail.isNotBlank()) ({ detail = EquipmentDetail(slot.detail, slot.expandedDetail, slot.originalEquipmentName) }) else null,
                    onDetail = { detail = EquipmentDetail(slot.detail, slot.expandedDetail, slot.originalEquipmentName) }, current = true)
            }
            items(page.notices) { EquipmentNotice(it) }
            options.groupBy { it.group }.forEach { (group, candidates) ->
                if (group.isNotBlank()) item(key = "group:$group") {
                    Text(messageText(group), Modifier.padding(top = 8.dp, bottom = 2.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                items(candidates, key = { it.value }) { option ->
                    val enabled = !busy && field?.enabled == true && option.enabled
                    Surface(selected = selected == option.value, onClick = { selected = option.value }, enabled = enabled,
                        shape = MaterialTheme.shapes.large, color = if (selected == option.value)
                            MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RadioButton(selected = selected == option.value, onClick = null, enabled = enabled)
                            EquipmentCandidateName(option.label, Modifier.weight(1f), originalName = option.originalEquipmentName)
                            if (option.detail.isNotBlank()) IconButton(onClick = { detail = EquipmentDetail(option.label, option.detail, option.originalEquipmentName) }) {
                                Icon(Icons.Default.Info, stringResource(R.string.game_ui_details), Modifier.size(20.dp))
                            } else Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            }
        }
        if (form != null || unequip != null) Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (unequip != null) OutlinedButton(onClick = { onAction(unequip) }, enabled = !busy,
                    modifier = Modifier.heightIn(min = 48.dp).then(if (form == null) Modifier.weight(1f) else Modifier)) {
                    Text(stringResource(R.string.equipment_ui_unequip))
                }
                if (form != null) Button(onClick = { onAction(form.actionWith(mapOf("eqids[]" to selected), emptyMap())) },
                    enabled = !busy && form.canSubmit(mapOf("eqids[]" to selected)),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.equipment_ui_equip))
                }
            }
        }
    }
    detail?.let { EquipmentDetails(it) { detail = null } }
}

// These leading symbols are status markers in the source equipment list, separate from translation decoration.
// Only their presentation changes; the original name and the submitted equipment ID stay untouched.
private data class EquipmentMarker(val symbol: String, val icon: ImageVector, val label: Int)
private val equipmentMarkers = listOf(
    EquipmentMarker("🗡", Icons.Default.CheckCircle, R.string.equipment_ui_equipped),
    EquipmentMarker("🛡", Icons.Default.Shield, R.string.equipment_ui_protected),
    EquipmentMarker("⚠", Icons.Default.Warning, R.string.equipment_ui_damaged),
    EquipmentMarker("❌", Icons.Default.Block, R.string.equipment_ui_unusable),
    EquipmentMarker("📦", Icons.Default.Inventory2, R.string.equipment_ui_stored),
    EquipmentMarker("📌", Icons.Default.PushPin, R.string.equipment_ui_pinned),
    EquipmentMarker("🔒", Icons.Default.Lock, R.string.equipment_ui_locked),
    EquipmentMarker("🔺", Icons.Default.KeyboardArrowUp, R.string.equipment_ui_high_level),
)

@Composable
internal fun EquipmentCandidateName(raw: String, modifier: Modifier = Modifier, originalName: String? = null,
    showOriginal: Boolean = false) {
    var name = raw.trimStart()
    val markers = buildList {
        while (true) {
            val marker = equipmentMarkers.firstOrNull { name.startsWith(it.symbol) } ?: break
            add(marker)
            name = name.removePrefix(marker.symbol).trimStart('\uFE0F', '\uFE0E', ' ')
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EquipmentNameText(name, originalName = originalName, showOriginal = showOriginal, style = MaterialTheme.typography.bodyMedium)
        if (markers.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            markers.forEach { Icon(it.icon, stringResource(it.label), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun EquipmentNotice(message: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
        Text(messageText(message), Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun EquipmentDetails(detail: EquipmentDetail, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { EquipmentCandidateName(detail.name,
        originalName = detail.originalName, showOriginal = true) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.text.lineSequence().filter { it.isNotBlank() }.forEach { Text(messageText(it), style = MaterialTheme.typography.bodyMedium) }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_ui_close)) } })
}
