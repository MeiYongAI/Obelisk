package com.mitahv.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mitahv.mobile.i18n.messageText
import com.mitahv.mobile.model.CharacterSectionRow
import com.mitahv.mobile.model.CharacterSectionsPage
import com.mitahv.mobile.model.CharacterAbilityInfo
import com.mitahv.mobile.model.CharacterAbilityTier
import com.mitahv.mobile.model.CharacterAbilityTierState
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
    loading: Boolean = false) {
    val rows = page.groups.flatMap { it.rows }
    val abilities = rows.filter { it.ability != null }
    val slots = rows.filter { it.abilitySlot != null }
    val tree = page.navigation.firstOrNull { it.selected }?.label.orEmpty()
    var showingSlots by rememberSaveable { mutableStateOf(false) }
    var familyIndex by rememberSaveable { mutableIntStateOf(0) }
    var treePicker by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var slotId by rememberSaveable { mutableStateOf<String?>(null) }
    var assignAbilityId by rememberSaveable { mutableStateOf<String?>(null) }
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

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f).heightIn(min = 48.dp)) {
                listOf(R.string.game_ui_abilities, R.string.ability_ui_slots).forEachIndexed { index, label ->
                    SegmentedButton(selected = showingSlots == (index == 1), onClick = { showingSlots = index == 1 },
                        shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(stringResource(label)) }
                }
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
                        text = { Text(stringResource(label), maxLines = 2) })
                }
            }
        } else {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { treePicker = true }, enabled = !busy,
                    modifier = Modifier.align(Alignment.CenterVertically).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(messageText(tree), Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ExpandMore, null)
                }
                AbilityPoints(page, Modifier.align(Alignment.CenterVertically))
            }
        }
        listStates.SaveableStateProvider(if (showingSlots) "slots:$familyIndex" else "tree:$tree") {
            if (showingSlots) {
                val familySlots = slots.filter { it.abilitySlot?.family == slotFamilies[familyIndex] }
                LazyVerticalGrid(columns = GridCells.Adaptive(104.dp), modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "summary", span = { GridItemSpan(maxLineSpan) }) { AbilitySummary(page) }
                    items(familySlots, key = { it.abilitySlot!!.id }) { slot ->
                        AbilitySlotTile(slot, enabled = !busy, onClick = { openSlot(slot) })
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (page.notices.isNotEmpty()) item(key = "notices") { AbilitySummary(page, showPoints = false) }
                    if (abilities.isEmpty()) item(key = "empty") {
                        Text(stringResource(R.string.ability_ui_empty), Modifier.padding(vertical = 24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(abilities, key = { it.ability!!.id }) { row ->
                        AbilityRow(row, onDetail = { detailId = row.ability!!.id })
                    }
                }
            }
        }
    }

    if (treePicker) AbilityPickerDialog(stringResource(R.string.ability_ui_tree), onDismiss = { treePicker = false }) {
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(if (selectedSlot != null) slotTrees else page.navigation, key = { it.label }) { link ->
                Surface(selected = link.selected, onClick = { treePicker = false; link.action?.let(onAction) },
                    enabled = !busy, shape = MaterialTheme.shapes.medium,
                    color = if (link.selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.heightIn(min = 56.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(messageText(link.label), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        if (link.selected) Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp))
                    }
                }
            }
        }
    }

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
        canChooseTree = slotTrees.size > 1, busy = busy, loading = loading,
        onDismiss = { slotId = null }, onChooseTree = { treePicker = true },
        onAction = { slotId = null; onAction(it) })
}

@Composable
private fun AbilitySummary(page: CharacterSectionsPage, showPoints: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        page.notices.forEach { message ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Text(messageText(message), Modifier.fillMaxWidth().padding(12.dp))
            }
        }
        if (showPoints) AbilityPoints(page)
    }
}

@Composable
private fun AbilityPoints(page: CharacterSectionsPage, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        page.summary.forEach { Text(messageText(it), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun AbilityRow(row: CharacterSectionRow, onDetail: () -> Unit) {
    val ability = requireNotNull(row.ability)
    val canUpgrade = row.actionFor("unlock_ability") != null
    Card(onClick = onDetail, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(messageText(row.title), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    if (ability.tiers.isNotEmpty()) {
                        val rankDescription = stringResource(R.string.ability_ui_rank, ability.currentTier, ability.maxTier)
                        Text(stringResource(R.string.ability_ui_rank_compact, ability.currentTier, ability.maxTier),
                            Modifier.clearAndSetSemantics { contentDescription = rankDescription },
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (ability.tiers.isNotEmpty()) {
                    AbilityTierProgress(ability, canUpgrade)
                } else if (row.value.isNotBlank()) Text(messageText(row.value), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
                else if (occupied) Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp))
            }
            Text(if (!slot.unlocked) stringResource(R.string.ability_ui_locked)
                else if (occupied) messageText(slot.equippedName) else stringResource(R.string.character_ui_empty),
                maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AbilityPickerDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text(messageText(row.title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (ability.tiers.isEmpty()) {
                    if (row.value.isNotBlank()) Text(messageText(row.value), color = MaterialTheme.colorScheme.primary)
                    Text(messageText(row.expandedDetail))
                } else {
                    Text(stringResource(R.string.ability_ui_rank, ability.currentTier, ability.maxTier), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary)
                    AbilityTierStates(ability, upgrade != null)
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
                    Text(stringResource(R.string.ability_ui_choose_slot))
                }
                if (reset != null) TextButton(onClick = { onDismiss(); onAction(reset) }, enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(messageText(reset.label))
                }
            }
        },
        confirmButton = {
            if (upgrade != null) Button(onClick = { onAction(upgrade) }, enabled = !busy) {
                Text(messageText(upgrade.label) + row.ability!!.upgradeCost.takeIf { it.isNotBlank() }
                    ?.let { " · " + abilityCost(it) }.orEmpty())
            } else TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_ui_close)) }
        },
        dismissButton = { if (upgrade != null) TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_ui_close)) } })
}

private val CharacterAbilityUpgradeRequirements.hasUnmetOrUnknownCondition: Boolean
    get() = levelMet != true || missingAbilityPoints != 0

@Composable
private fun AbilityRequirements(requirements: CharacterAbilityUpgradeRequirements, prefix: String? = null,
    showStatus: Boolean = true) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (prefix != null) Text(prefix, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun abilityTierStatus(tier: CharacterAbilityTier, currentTier: Int, canUpgrade: Boolean): String = stringResource(when {
    tier.state == CharacterAbilityTierState.Acquired -> R.string.ability_ui_learned
    tier.number == currentTier + 1 && canUpgrade -> R.string.ability_ui_upgradable
    tier.state == CharacterAbilityTierState.Available -> R.string.ability_ui_pending
    else -> R.string.ability_ui_locked
})

@Composable
private fun AbilityTierProgress(ability: CharacterAbilityInfo, canUpgrade: Boolean) {
    val states = ability.tiers.map { tier ->
        stringResource(R.string.ability_ui_tier_state, tier.number, abilityTierStatus(tier, ability.currentTier, canUpgrade))
    }.joinToString(", ")
    Row(Modifier.fillMaxWidth().padding(top = 2.dp).clearAndSetSemantics { contentDescription = states },
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        ability.tiers.forEach { tier ->
            val color = when (tier.state) {
                CharacterAbilityTierState.Acquired -> MaterialTheme.colorScheme.primary
                CharacterAbilityTierState.Available -> MaterialTheme.colorScheme.secondaryContainer
                CharacterAbilityTierState.Locked -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(Modifier.weight(1f).height(4.dp).background(color, MaterialTheme.shapes.extraSmall))
        }
    }
}

@Composable
private fun AbilityTierStates(ability: CharacterAbilityInfo, canUpgrade: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ability.tiers.chunked(5).forEach { tiers ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tiers.forEach { tier ->
                    val acquired = tier.state == CharacterAbilityTierState.Acquired
                    Surface(Modifier.weight(1f), shape = MaterialTheme.shapes.small,
                        color = if (acquired) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = if (tier.number == ability.currentTier + 1) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
                        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(tier.number.toString(), style = MaterialTheme.typography.titleSmall)
                            Text(abilityTierStatus(tier, ability.currentTier, canUpgrade), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (ability.tiers.size > 5) repeat(5 - tiers.size) { Spacer(Modifier.weight(1f)) }
            }
        }
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
private fun AbilitySlotDialog(row: CharacterSectionRow, tree: String, canChooseTree: Boolean, busy: Boolean, loading: Boolean,
    onDismiss: () -> Unit, onChooseTree: () -> Unit, onAction: (NativeAction) -> Unit) {
    val slot = requireNotNull(row.abilitySlot)
    val form = row.form
    val field = form?.fields?.firstOrNull { it.name == "equip_ability" }
    val options = field?.options.orEmpty().filter { it.enabled }
    var details by rememberSaveable(slot.id) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(characterLabel(row.title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!slot.unlocked) Text(messageText(row.detail))
                else {
                    if (slot.equippedName.isNotBlank()) {
                        Text(messageText(slot.equippedName), style = MaterialTheme.typography.titleSmall)
                        if (row.expandedDetail.isNotBlank()) TextButton(onClick = { details = !details }) {
                            Text(stringResource(if (details) R.string.ability_ui_hide_details else R.string.game_ui_details))
                        }
                    }
                    if (details) Text(messageText(row.expandedDetail), Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()))
                    if (canChooseTree) OutlinedButton(onClick = onChooseTree, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
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
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
