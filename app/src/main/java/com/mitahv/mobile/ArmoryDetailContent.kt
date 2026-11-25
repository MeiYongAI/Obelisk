package com.mitahv.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.i18n.messageText
import com.mitahv.mobile.model.*

@Composable
internal fun ArmoryDetailContent(
    page: BazaarSectionsPage,
    access: PageInteraction,
    responseRevision: Int,
    onAction: (NativeAction) -> Unit,
) {
    val busy = !access.canSubmit
    val equipment = page.sections.firstOrNull() ?: return
    val equipmentKey = (page.forms.flatMap { it.actions } + page.actions)
        .firstNotNullOfOrNull { it.url.queryParameter("eqids[]") } ?: equipment.title
    val charmForm = page.forms.firstOrNull { it.armoryCharm != null }
    val repairForm = page.forms.firstOrNull { it.armoryRepair != null }
    val remainingForms = page.forms.filterNot { it == charmForm || it == repairForm }
    val upgradeGroups = page.sections.drop(1).filter { it.title == "Upgrade" }
    val charmGroups = page.sections.drop(1).filter { it.title == "Charms" }
    val rollGroups = page.sections.drop(1).filter { it.title in setOf("Base Stat Rolls", "Stat Fusion") }
    val otherGroups = page.sections.drop(1).filterNot { it in upgradeGroups || it in charmGroups || it in rollGroups }
    val itemWorldActions = page.actions.filter { it.url.queryParameter("s") == "Battle" && it.url.queryParameter("ss") == "iw" }
    val fusionActions = page.actions.filter { it.url.queryParameter("screen") == "statfuse" }
    val menuActions = page.actions.filterNot { it in itemWorldActions || it in fusionActions }
    val tabs = buildList {
        if (upgradeGroups.isNotEmpty() || itemWorldActions.isNotEmpty()) add(ArmoryDetailTab.Upgrade)
        if (charmGroups.isNotEmpty() || charmForm != null) add(ArmoryDetailTab.Charms)
        if (rollGroups.isNotEmpty() || fusionActions.isNotEmpty()) add(ArmoryDetailTab.Rolls)
    }
    var selectedTab by rememberSaveable(equipmentKey) {
        mutableStateOf(if (charmForm != null) ArmoryDetailTab.Charms else tabs.firstOrNull())
    }
    val activeTab = selectedTab.takeIf { it in tabs } ?: tabs.firstOrNull()
    var operationKey by rememberSaveable(equipmentKey, charmForm?.armoryCharm?.slot) {
        mutableStateOf(charmForm?.key)
    }
    var menuOpen by remember { mutableStateOf(false) }
    var statsOpen by rememberSaveable(equipmentKey) { mutableStateOf(false) }
    val scrollStates = rememberSaveableStateHolder()
    LaunchedEffect(charmForm?.armoryCharm?.slot) {
        if (charmForm != null) selectedTab = ArmoryDetailTab.Charms
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EquipmentNameText(equipment.title, Modifier.weight(1f).semantics { heading() },
                originalName = equipment.originalEquipmentName, style = MaterialTheme.typography.titleLarge)
            if (equipment.facts.isNotEmpty()) IconButton(onClick = { statsOpen = true }) {
                Icon(Icons.Default.Info, stringResource(R.string.game_ui_equipment_stats))
            }
            if (remainingForms.isNotEmpty() || menuActions.isNotEmpty()) Box {
                IconButton(onClick = { menuOpen = true }, enabled = !busy || menuActions.any(access::allows)) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.game_ui_more_actions))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    remainingForms.forEach { form ->
                        DropdownMenuItem(text = { Text(messageText(form.title)) }, enabled = !busy,
                            onClick = { menuOpen = false; operationKey = form.key })
                    }
                    menuActions.forEach { action ->
                        DropdownMenuItem(text = { Text(messageText(action.label)) }, enabled = access.allows(action),
                            onClick = { menuOpen = false; onAction(action) })
                    }
                }
            }
        }
        repairForm?.let { form ->
            TextButton(onClick = { operationKey = form.key }, enabled = !busy,
                modifier = Modifier.padding(start = 12.dp).heightIn(min = 48.dp)) {
                Icon(Icons.Default.Build, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(messageText(form.title))
            }
        }
        if (tabs.size > 1) PrimaryTabRow(selectedTabIndex = tabs.indexOfFirst { it == activeTab }.coerceAtLeast(0)) {
            tabs.forEach { tab ->
                Tab(selected = activeTab == tab, onClick = { selectedTab = tab },
                    text = { Text(stringResource(tab.label)) })
            }
        }
        scrollStates.SaveableStateProvider("$equipmentKey:${activeTab?.name}") {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(page.messages) { message ->
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                        Text(messageText(message), Modifier.fillMaxWidth().padding(12.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                otherGroups.forEach { group ->
                    if (group.facts.isNotEmpty() || group.emptyText.isNotBlank()) item {
                        BazaarFacts(group.facts)
                        if (group.emptyText.isNotBlank()) Text(messageText(group.emptyText),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    items(group.entries, key = { "other:${group.title}:${it.key}" }) { entry ->
                        ArmoryDetailEntry(entry, !access.allows(entry.action), onAction)
                    }
                }
                when (activeTab) {
                    ArmoryDetailTab.Upgrade -> {
                        upgradeGroups.forEach { group ->
                            if (group.facts.isNotEmpty()) item { BazaarFacts(group.facts) }
                            if (group.emptyText.isNotBlank()) item { Text(messageText(group.emptyText)) }
                            items(group.entries, key = { "upgrade:${it.key}" }) { entry -> ArmoryDetailEntry(entry, !access.allows(entry.action), onAction) }
                        }
                        items(itemWorldActions) { action ->
                            OutlinedButton(onClick = { onAction(action) }, enabled = access.allows(action),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(messageText(action.label)) }
                        }
                    }
                    ArmoryDetailTab.Charms -> {
                        charmGroups.forEach { group ->
                            if (group.facts.isNotEmpty()) item { BazaarFacts(group.facts) }
                            if (group.emptyText.isNotBlank()) item { Text(messageText(group.emptyText)) }
                            items(group.entries, key = { "charm:${it.key}" }) { entry ->
                                ArmoryCharmSlot(entry, !access.allows(entry.action)) { action ->
                                    if (charmForm != null && action.url.queryParameter("charmslot") == charmForm.armoryCharm?.slot.toString())
                                        operationKey = charmForm.key
                                    else onAction(action)
                                }
                            }
                        }
                        if (charmForm != null && charmGroups.all { it.entries.isEmpty() }) item {
                            FilledTonalButton(onClick = { operationKey = charmForm.key }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(messageText(charmForm.title)) }
                        }
                    }
                    ArmoryDetailTab.Rolls -> {
                        rollGroups.forEach { group ->
                            if (group.facts.isNotEmpty()) item { BazaarFacts(group.facts) }
                            if (group.emptyText.isNotBlank()) item { Text(messageText(group.emptyText)) }
                            if (group.title == "Base Stat Rolls" && group.entries.isNotEmpty()) item {
                                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        group.entries.forEach { entry ->
                                            BazaarFacts(listOf(entry.title.removeSuffix(":") to entry.details.joinToString(" · ") {
                                                if (it.first.isBlank()) it.second else "${it.first}: ${it.second}"
                                            }))
                                        }
                                    }
                                }
                            } else items(group.entries, key = { "roll:${it.key}" }) { entry -> ArmoryDetailEntry(entry, !access.allows(entry.action), onAction) }
                        }
                        items(fusionActions) { action ->
                            FilledTonalButton(onClick = { onAction(action) }, enabled = access.allows(action),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(messageText(action.label)) }
                        }
                    }
                    null -> Unit
                }
            }
        }
    }
    page.forms.firstOrNull { it.key == operationKey }?.let { form ->
        BazaarOperationDialog(form, busy, responseRevision, onDismiss = { operationKey = null },
            onSubmit = { action -> operationKey = null; onAction(action) })
    }
    if (statsOpen) BazaarDetailsDialog(equipment.title, equipment.facts, equipmentName = true,
        originalEquipmentName = equipment.originalEquipmentName, onDismiss = { statsOpen = false })
}

private enum class ArmoryDetailTab(val label: Int) {
    Upgrade(R.string.armory_detail_upgrade),
    Charms(R.string.armory_detail_charms),
    Rolls(R.string.armory_detail_rolls),
}

@Composable
private fun ArmoryCharmSlot(entry: BazaarSectionsEntry, busy: Boolean, onAction: (NativeAction) -> Unit) {
    Surface(onClick = { entry.action?.let(onAction) }, enabled = !busy && entry.enabled && entry.action != null,
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(messageText(entry.title), style = MaterialTheme.typography.titleMedium)
                BazaarFacts(entry.details)
            }
            if (entry.action != null && entry.enabled) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ArmoryDetailEntry(entry: BazaarSectionsEntry, busy: Boolean, onAction: (NativeAction) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (entry.action != null || !entry.enabled) {
            Button(onClick = { entry.action?.let(onAction) }, enabled = !busy && entry.enabled && entry.action != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(messageText(entry.title)) }
        } else Text(messageText(entry.title), style = MaterialTheme.typography.titleSmall)
        if (entry.details.isNotEmpty()) BazaarFacts(entry.details)
    }
}
