package com.mitahv.mobile

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mitahv.mobile.i18n.messageText
import com.mitahv.mobile.model.CharacterSectionRow
import com.mitahv.mobile.model.CharacterSectionsPage
import com.mitahv.mobile.model.CharacterAbilityTier
import com.mitahv.mobile.model.CharacterAbilityUpgradeRequirements
import com.mitahv.mobile.model.NativeAction

private val slotFamilies = listOf("r", "g", "b", "p")
private val slotFamilyLabels = listOf(R.string.ability_ui_major, R.string.ability_ui_supportive,
    R.string.ability_ui_protection, R.string.ability_ui_drain)

private fun CharacterSectionRow.actionFor(field: String) = actions.firstOrNull { action ->
    action.fields.any { it.first == field }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AbilitiesScreen(page: CharacterSectionsPage, busy: Boolean, onAction: (NativeAction) -> Unit,
    loading: Boolean = false, treeEnabled: Boolean = !busy, pendingTree: String? = null,
    onTree: (String) -> Unit = { id -> page.navigation.find { it.abilityTreeId == id }?.takeUnless { it.selected }?.action?.let(onAction) },
    loadTree: suspend (okhttp3.HttpUrl) -> CharacterSectionsPage, loadError: String? = null, onRetry: () -> Unit = {}) {
    val rows = page.groups.flatMap { it.rows }
    val abilities = rows.filter { it.ability != null }
    val slots = rows.filter { it.abilitySlot != null }
    val tree = page.navigation.firstOrNull { it.selected }
    var showingSlots by rememberSaveable { mutableStateOf(false) }
    var familyIndex by rememberSaveable { mutableIntStateOf(0) }
    var treePicker by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var detailId by rememberSaveable(tree?.abilityTreeId) { mutableStateOf<String?>(null) }
    var slotId by rememberSaveable { mutableStateOf<String?>(null) }
    var assignAbilityId by rememberSaveable(tree?.abilityTreeId) { mutableStateOf<String?>(null) }
    val selectedTree = pendingTree ?: tree?.abilityTreeId
    var directorySelection by remember { mutableIntStateOf(0) }
    var upgradesOpen by rememberSaveable { mutableStateOf(false) }
    val listStates = rememberSaveableStateHolder()
    val resetAll = rows.firstNotNullOfOrNull { it.actionFor("reset_all") }
    val selectedSlot = slots.firstOrNull { it.abilitySlot?.id == slotId }
    val slotTrees = selectedSlot?.abilitySlot?.let(page::abilityTreesFor).orEmpty()

    fun openSlot(row: CharacterSectionRow) {
        val slot = requireNotNull(row.abilitySlot)
        slotId = slot.id
        if (slot.unlocked) {
            page.preferredAbilityTreeFor(slot)?.takeUnless { it.selected }?.action?.let(onAction)
        }
    }

    BackHandler(enabled = showingSlots && selectedSlot == null && !treePicker) { showingSlots = false }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showingSlots) {
                IconButton(onClick = { showingSlots = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.app_back)) }
                Text(stringResource(R.string.ability_ui_slots), Modifier.padding(end = 10.dp), style = MaterialTheme.typography.titleMedium)
            }
            AbilityPoints(page, Modifier.weight(1f))
            if (!showingSlots) TextButton(onClick = { showingSlots = true }) {
                Icon(Icons.Default.DashboardCustomize, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ability_ui_slots))
            }
            if (resetAll != null) Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.game_ui_more_actions)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(messageText(resetAll.label)) }, enabled = !busy,
                        onClick = { menuOpen = false; onAction(resetAll) })
                }
            }
        }
        if (showingSlots) {
            PrimaryTabRow(selectedTabIndex = familyIndex) {
                slotFamilyLabels.forEachIndexed { index, label ->
                    Tab(selected = familyIndex == index, onClick = { familyIndex = index },
                        text = { Text(stringResource(label), maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center) })
                }
            }
        } else {
            key(directorySelection) { AbilityTreeBar(page.navigation, selectedTree, treeEnabled, { treePicker = true }, onTree) }
        }
        listStates.SaveableStateProvider(if (showingSlots) "slots:$familyIndex" else "tree:${tree?.abilityTreeId}") {
            if (showingSlots) {
                val familySlots = slots.filter { it.abilitySlot?.family == slotFamilies[familyIndex] }
                LazyVerticalGrid(columns = GridCells.Adaptive(104.dp), modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (page.notices.isNotEmpty()) item(key = "notices", span = { GridItemSpan(maxLineSpan) }) { AbilityNotices(page) }
                    items(familySlots, key = { it.abilitySlot!!.id }) { slot ->
                        AbilitySlotTile(slot, enabled = treeEnabled, onClick = { openSlot(slot) })
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (page.notices.isNotEmpty()) item(key = "notices") { AbilityNotices(page) }
                    item(key = "equipped-upgrades") {
                        OutlinedButton(onClick = { upgradesOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.ability_ui_equipped_upgrades))
                        }
                    }
                    if (abilities.isEmpty()) item(key = "empty") {
                        Text(stringResource(R.string.ability_ui_empty), Modifier.padding(vertical = 24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(abilities, key = { it.ability!!.id }) { row ->
                        AbilityRow(row, busy, onAction, onDetail = { detailId = row.ability!!.id })
                    }
                }
            }
        }
    }

    if (treePicker) AbilityTreeSheet(if (selectedSlot == null) page.navigation else slotTrees, selectedTree,
        treeEnabled, onDismiss = { treePicker = false }, onSelect = {
            directorySelection++; onTree(it)
        })
    if (upgradesOpen) EquippedUpgradeDialog(page, busy, loadTree, loadError, onRetry, onAction) { upgradesOpen = false }

    abilities.firstOrNull { it.ability?.id == detailId }?.let { row ->
        AbilityDetailDialog(row, busy, onDismiss = { detailId = null }, onAction = onAction,
            onChooseSlot = { detailId = null; assignAbilityId = row.ability!!.id })
    }
    abilities.firstOrNull { it.ability?.id == assignAbilityId }?.let { ability ->
        val id = ability.ability!!.id
        val compatible = slots.filter { slot -> slot.form?.canSubmit(mapOf("equip_ability" to id)) == true }
        if (compatible.isEmpty()) AlertDialog(onDismissRequest = { assignAbilityId = null },
            title = { Text(messageText(ability.title)) }, text = { Text(stringResource(R.string.ability_ui_no_slots)) },
            confirmButton = { TextButton(onClick = {
                assignAbilityId = null
                showingSlots = true
                familyIndex = slotFamilies.indexOf(ability.ability.family).coerceAtLeast(0)
            }) { Text(stringResource(R.string.game_ui_ability_slots)) } },
            dismissButton = { TextButton(onClick = { assignAbilityId = null }) { Text(stringResource(R.string.game_ui_close)) } })
        else AbilityPickerDialog(messageText(ability.title), onDismiss = { assignAbilityId = null }) {
            LazyVerticalGrid(columns = GridCells.Adaptive(96.dp), modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(compatible, key = { it.abilitySlot!!.id }) { slot ->
                    val current = slot.abilitySlot!!.id in ability.ability.equippedSlotIds
                    AbilitySlotTile(slot, enabled = !busy && !current, current = current, onClick = {
                        assignAbilityId = null
                        slot.form?.let { onAction(it.actionWith(mapOf("equip_ability" to id), emptyMap())) }
                    })
                }
            }
        }
    }
    if (!treePicker && selectedSlot != null) AbilitySlotDialog(selectedSlot,
        tree = selectedSlot.abilitySlot?.let(page::preferredAbilityTreeFor)?.label.orEmpty(),
        canChooseTree = slotTrees.size > 1, treeEnabled = treeEnabled, busy = busy, loading = loading,
        onDismiss = { slotId = null }, onChooseTree = { treePicker = true },
        onAction = { slotId = null; onAction(it) })
}

@Composable
private fun AbilityNotices(page: CharacterSectionsPage) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        page.notices.forEach { message ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Text(messageText(message), Modifier.fillMaxWidth().padding(12.dp))
            }
        }
    }
}

@Composable
private fun AbilityPoints(page: CharacterSectionsPage, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        page.summary.forEach { entry ->
            val value = entry.substringAfter(':').trim()
            val label = when (entry.substringBefore(':').trim().lowercase()) {
                "ability points" -> stringResource(R.string.ability_ui_balance_ap, value)
                "mastery points" -> stringResource(R.string.ability_ui_balance_mp, value)
                else -> messageText(entry)
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AbilityRow(row: CharacterSectionRow, busy: Boolean, onAction: (NativeAction) -> Unit, onDetail: () -> Unit) {
    val ability = requireNotNull(row.ability)
    val advance = row.actionFor("unlock_ability")
    val state = when {
        advance != null -> null
        (ability.maxTier > 0 && ability.currentTier == ability.maxTier) || row.value == "At Maximum" -> R.string.ability_ui_maxed
        !ability.acquired -> R.string.ability_ui_locked
        else -> R.string.ability_ui_upgrade_unavailable
    }
    val rank = if (ability.maxTier > 0) stringResource(R.string.ability_ui_rank, ability.currentTier, ability.maxTier) else null
    val summary = listOfNotNull(rank, state?.let { stringResource(it) },
        if (ability.equippedSlotIds.isNotEmpty()) stringResource(R.string.inventory_ui_equipped) else null).joinToString(" · ")
    val requirements = ability.upgradeRequirements
    val blockers = if (advance == null && requirements != null) listOfNotNull(
        if (requirements.levelMet == false) stringResource(R.string.ability_ui_need_level, requirements.requiredLevel) else null,
        requirements.missingAbilityPoints?.takeIf { it > 0 }?.let { stringResource(R.string.ability_ui_need_points, it) }
    ).joinToString(" · ") else ""
    val detailLabel = stringResource(R.string.game_ui_details)
    val stacked = LocalDensity.current.fontScale >= 1.3f
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable(onClickLabel = detailLabel, onClick = onDetail)
                    .heightIn(min = 68.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AbilityArtwork(row.title, ability.family, size = 32.dp, acquired = ability.acquired)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(messageText(row.title), style = MaterialTheme.typography.titleSmall)
                        if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (blockers.isNotEmpty()) Text(blockers, style = MaterialTheme.typography.labelMedium)
                    }
                    if (advance == null || stacked) Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!stacked && advance != null) AbilityAdvanceButton(row, advance, busy, onAction, Modifier.padding(end = 8.dp))
            }
            if (stacked && advance != null) AbilityAdvanceButton(row, advance, busy, onAction,
                Modifier.align(Alignment.End).padding(end = 8.dp, bottom = 4.dp))
        }
    }
}

@Composable
internal fun AbilityAdvanceButton(row: CharacterSectionRow, action: NativeAction, busy: Boolean,
    onAction: (NativeAction) -> Unit, modifier: Modifier = Modifier) {
    val ability = requireNotNull(row.ability)
    val cost = ability.upgradeCost.takeIf { it.isNotBlank() }?.let { abilityCost(it) }
        ?: ability.upgradeRequirements?.let { stringResource(R.string.ability_ui_point_cost, it.requiredAbilityPoints.toString()) }
    val label = listOfNotNull(messageText(action.label), cost).joinToString(" · ")
    val description = "${messageText(row.title)}, $label"
    FilledTonalButton(onClick = { onAction(action) }, enabled = !busy, modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, Modifier.clearAndSetSemantics { contentDescription = description }, textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelLarge)
    }
}

private val slotCost = Regex("Unlock Cost: ([0-9,]+) Mastery Points?", RegexOption.IGNORE_CASE)

@Composable
private fun slotUnlockLabel(row: CharacterSectionRow): String {
    val cost = slotCost.matchEntire(row.detail)?.groupValues?.get(1)
    val state = if (row.actionFor("unlock_slot") != null) R.string.ability_ui_unlockable else R.string.ability_ui_locked
    return listOfNotNull(stringResource(state), cost?.let { stringResource(R.string.ability_ui_mastery_cost, it) },
        row.detail.takeIf { cost == null && it.isNotBlank() }?.let { messageText(it) }).joinToString(" · ")
}

@Composable
private fun AbilitySlotTile(row: CharacterSectionRow, enabled: Boolean = true, current: Boolean = false, onClick: () -> Unit) {
    val slot = requireNotNull(row.abilitySlot)
    val occupied = slot.equippedName.isNotEmpty()
    Card(onClick = onClick, enabled = enabled, border = if (current) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = if (occupied)
        MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 100.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(row.title.substringAfterLast(' '), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                if (!slot.unlocked) Icon(Icons.Default.Lock, null, Modifier.size(16.dp))
                else if (occupied) AbilityArtwork(slot.equippedName, slot.family, size = 24.dp)
            }
            Text(if (!slot.unlocked) slotUnlockLabel(row)
                else if (occupied) messageText(slot.equippedName) else stringResource(R.string.character_ui_empty),
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun AbilityPickerDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.heightIn(max = 640.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.game_ui_close)) }
                }
                content()
            }
        }
    }
}

@Composable
private fun AbilityDetailDialog(row: CharacterSectionRow, busy: Boolean, onDismiss: () -> Unit,
    onAction: (NativeAction) -> Unit, onChooseSlot: () -> Unit) {
    val ability = requireNotNull(row.ability)
    val upgrade = row.actionFor("unlock_ability")
    val reset = row.actionFor("reset_ability")
    AlertDialog(onDismissRequest = onDismiss, title = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AbilityArtwork(row.title, ability.family, size = 36.dp, acquired = ability.acquired)
            Text(messageText(row.title))
        }
    },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (ability.tiers.isEmpty()) {
                    if (row.value.isNotBlank()) Text(messageText(row.value), color = MaterialTheme.colorScheme.primary)
                    Text(messageText(row.expandedDetail))
                } else {
                    Text(stringResource(R.string.ability_ui_rank, ability.currentTier, ability.maxTier), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary)
                    if (ability.description.isNotBlank()) Text(messageText(ability.description))
                    ability.tiers.firstOrNull { it.number == ability.currentTier }?.let { tier ->
                        AbilityTierDetails(stringResource(R.string.ability_ui_current_rank, tier.number), tier, showRequirements = false)
                    }
                    ability.tiers.firstOrNull { it.number == ability.currentTier + 1 }?.let { tier ->
                        AbilityTierDetails(stringResource(R.string.ability_ui_next_rank, tier.number), tier,
                            requirements = ability.upgradeRequirements,
                            showRequirementStatus = upgrade != null || ability.upgradeRequirements?.hasUnmetOrUnknownCondition == true)
                        if (upgrade == null && ability.upgradeRequirements?.hasUnmetOrUnknownCondition == false) {
                            Text(stringResource(R.string.ability_ui_upgrade_unavailable), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (ability.acquired) OutlinedButton(onClick = onChooseSlot, enabled = !busy) {
                    Text(stringResource(if (ability.equippedSlotIds.isEmpty()) R.string.ability_ui_choose_slot else R.string.ability_ui_equipped_change))
                }
                if (reset != null) TextButton(onClick = { onDismiss(); onAction(reset) }, enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(messageText(reset.label))
                }
            }
        },
        confirmButton = {
            if (upgrade != null) AbilityAdvanceButton(row, upgrade, busy, onAction) else TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_ui_close)) }
        },
        dismissButton = { if (upgrade != null) TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_ui_close)) } })
}

private val CharacterAbilityUpgradeRequirements.hasUnmetOrUnknownCondition: Boolean
    get() = levelMet != true || missingAbilityPoints != 0

@Composable
private fun AbilityRequirements(requirements: CharacterAbilityUpgradeRequirements,
    showStatus: Boolean = true) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AbilityRequirement(stringResource(R.string.ability_ui_player_level, requirements.requiredLevel),
            requirements.levelMet.takeIf { showStatus })
        AbilityRequirement(stringResource(R.string.ability_ui_point_cost, requirements.requiredAbilityPoints.toString()),
            requirements.missingAbilityPoints?.let { it == 0 }.takeIf { showStatus })
    }
}

@Composable
private fun AbilityRequirement(label: String, met: Boolean?) {
    val description = when (met) {
        true -> "$label, ${stringResource(R.string.ability_ui_condition_met)}"
        false -> "$label, ${stringResource(R.string.ability_ui_condition_unmet)}"
        null -> label
    }
    val color = if (met == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (met != null) Icon(if (met) Icons.Default.Check else Icons.Default.Close, null, Modifier.size(14.dp), tint = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun AbilityTierDetails(title: String, tier: CharacterAbilityTier, showRequirements: Boolean = true,
    requirements: CharacterAbilityUpgradeRequirements? = null, showRequirementStatus: Boolean = true) {
    if ((!showRequirements || tier.requirements.isBlank()) && tier.effects.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (showRequirements) {
            if (requirements != null) AbilityRequirements(requirements, showStatus = showRequirementStatus)
            else if (tier.requirements.isNotBlank()) Text(messageText(tier.requirements), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (tier.effects.isNotBlank()) Text(messageText(tier.effects))
    }
}

@Composable
private fun AbilitySlotDialog(row: CharacterSectionRow, tree: String, canChooseTree: Boolean, treeEnabled: Boolean, busy: Boolean, loading: Boolean,
    onDismiss: () -> Unit, onChooseTree: () -> Unit, onAction: (NativeAction) -> Unit) {
    val slot = requireNotNull(row.abilitySlot)
    val form = row.form
    val field = form?.fields?.firstOrNull { it.name == "equip_ability" }
    val options = field?.options.orEmpty().filter { it.enabled }
    var details by rememberSaveable(slot.id) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(characterLabel(row.title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!slot.unlocked) Text(slotUnlockLabel(row))
                else {
                    if (slot.equippedName.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AbilityArtwork(slot.equippedName, slot.family, size = 28.dp)
                            Text(messageText(slot.equippedName), style = MaterialTheme.typography.titleSmall)
                        }
                        if (row.expandedDetail.isNotBlank()) TextButton(onClick = { details = !details }) {
                            Text(stringResource(if (details) R.string.ability_ui_hide_details else R.string.game_ui_details))
                        }
                    }
                    if (details) Text(messageText(row.expandedDetail), Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()))
                    if (canChooseTree) OutlinedButton(onClick = onChooseTree, enabled = treeEnabled, modifier = Modifier.fillMaxWidth()) {
                        Text(messageText(tree), Modifier.weight(1f))
                        Icon(Icons.Default.ExpandMore, null)
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else if (options.isEmpty()) Text(stringResource(R.string.ability_ui_no_tree_choices),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!loading) LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(options, key = { it.value }) { option ->
                            val selected = option.value == field?.value
                            Surface(selected = selected, onClick = {
                                onAction(requireNotNull(form).actionWith(mapOf("equip_ability" to option.value), emptyMap()))
                            }, enabled = !busy && !selected, shape = MaterialTheme.shapes.medium,
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AbilityArtwork(option.label, slot.family, size = 24.dp)
                                    Text(messageText(option.label), Modifier.weight(1f))
                                    if (selected) Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_ui_close)) } },
        dismissButton = {
            row.actions.forEach { action -> TextButton(onClick = { onAction(action) }, enabled = !busy) {
                Text(messageText(action.label))
            } }
        })
}

@Composable
private fun abilityCost(raw: String): String {
    val points = Regex("([0-9,]+) AP", RegexOption.IGNORE_CASE).matchEntire(raw)
    return if (points != null) stringResource(R.string.ability_ui_point_cost, points.groupValues[1]) else messageText(raw)
}
