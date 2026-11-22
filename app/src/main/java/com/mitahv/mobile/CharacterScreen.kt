package com.mitahv.mobile

import androidx.compose.ui.res.stringResource
import com.mitahv.mobile.i18n.gameText
import com.mitahv.mobile.i18n.messageText

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.mitahv.mobile.data.AttributeAllocation
import com.mitahv.mobile.model.CharacterAttribute
import com.mitahv.mobile.model.CharacterPage
import com.mitahv.mobile.model.CharacterStatGroup
import java.text.NumberFormat
import java.util.Locale

private data class AllocationDraft(val snapshot: String, val deltas: Map<String, Int>)

private val AllocationSaver = mapSaver<AllocationDraft>(
    save = { mapOf("snapshot" to it.snapshot) + it.deltas },
    restore = { saved -> AllocationDraft(saved.getValue("snapshot") as String,
        saved.filterKeys { it != "snapshot" }.mapValues { it.value as Int }) },
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CharacterScreen(
    page: CharacterPage,
    worldKey: String,
    busy: Boolean,
    actionError: String?,
    revision: Int,
    onRefresh: () -> Unit,
    onDraftChanged: (Boolean) -> Unit,
    onPersonaSelected: (String) -> Unit,
    onRenamePersona: (String) -> Unit,
    onCreatePersona: (String) -> Unit,
    onApplyAttributes: (Map<String, Int>) -> Unit,
) {
    val snapshotKey = "$worldKey:${page.personaId}:${page.attributes}:${page.availableExperience}"
    val initialDeltas = page.attributes.associate { it.key to it.pendingDelta }
    var draft by rememberSaveable(snapshotKey, revision, stateSaver = AllocationSaver) {
        mutableStateOf(AllocationDraft(snapshotKey, initialDeltas))
    }
    val deltas = if (draft.snapshot == snapshotKey) draft.deltas else initialDeltas
    var step by rememberSaveable { mutableIntStateOf(1) }
    var personaMenu by remember { mutableStateOf(false) }
    var createMenu by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var renameValue by rememberSaveable { mutableStateOf("") }
    var createKind by remember { mutableStateOf<String?>(null) }
    var discardAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val dirty = page.attributes.any { (deltas[it.key] ?: it.pendingDelta) != it.pendingDelta }
    val remaining = AttributeAllocation.remainingExperience(page, deltas)
    val enabled = !busy && actionError == null
    val switchPersonaDescription = stringResource(R.string.game_ui_switch_persona)
    LaunchedEffect(dirty, revision) { onDraftChanged(dirty) }
    fun resetDraft() { draft = AllocationDraft(snapshotKey, initialDeltas) }
    fun afterDiscard(action: () -> Unit) {
        if (dirty) discardAction = action else action()
    }
    BackHandler(enabled = dirty && !busy) { discardAction = { resetDraft() } }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            Surface(
                                onClick = { personaMenu = true },
                                enabled = enabled && page.canSwitchPersona,
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.semantics { contentDescription = switchPersonaDescription },
                            ) {
                                Row(Modifier.heightIn(min = 48.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(page.personaName, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Icon(Icons.Filled.ExpandMore, null)
                                }
                            }
                            DropdownMenu(expanded = personaMenu, onDismissRequest = { personaMenu = false }) {
                                page.personaOptions.forEach { persona ->
                                    DropdownMenuItem(
                                        text = { Text(persona.name) },
                                        trailingIcon = { if (persona.id == page.personaId) Icon(Icons.Filled.Check, stringResource(R.string.game_ui_current_persona)) },
                                        onClick = {
                                            personaMenu = false
                                            if (persona.id != page.personaId) afterDiscard { onPersonaSelected(persona.id) }
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { afterDiscard { renameValue = page.personaName; renameOpen = true } },
                            enabled = enabled && page.canRenamePersona) {
                            Icon(Icons.Filled.Edit, stringResource(R.string.game_ui_rename_persona))
                        }
                        Box {
                            IconButton(
                                onClick = { createMenu = true },
                                enabled = enabled && (page.canClonePersona || page.canCreateBlankPersona),
                            ) { Icon(Icons.Filled.PersonAdd, stringResource(R.string.game_ui_create_persona)) }
                            DropdownMenu(expanded = createMenu, onDismissRequest = { createMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.game_ui_clone_persona)) }, enabled = page.canClonePersona,
                                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                                    onClick = { createMenu = false; afterDiscard { createKind = "clone" } })
                                DropdownMenuItem(text = { Text(stringResource(R.string.game_ui_blank_persona)) }, enabled = page.canCreateBlankPersona,
                                    leadingIcon = { Icon(Icons.Filled.PersonAdd, null) },
                                    onClick = { createMenu = false; afterDiscard { createKind = "blank" } })
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (page.level.isNotBlank()) Text(gameText(page.level), style = MaterialTheme.typography.labelLarge)
                        if (page.usedPersonaCount != null && page.personaSlotCapacity != null) {
                            Text(stringResource(R.string.game_ui_persona_count, page.usedPersonaCount, page.personaSlotCapacity), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (actionError != null) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(messageText(actionError), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = { afterDiscard(onRefresh) }) { Text(stringResource(R.string.game_ui_refresh_status)) }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.game_ui_attributes), Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.titleLarge)
                    SingleChoiceSegmentedButtonRow {
                        listOf(1, 10, 100).forEachIndexed { index, amount ->
                            val stepDescription = stringResource(R.string.game_ui_adjust_step, amount)
                            SegmentedButton(selected = step == amount, onClick = { step = amount },
                                enabled = enabled && page.canEditAttributes,
                                shape = SegmentedButtonDefaults.itemShape(index, 3),
                                icon = {}, modifier = Modifier.semantics { contentDescription = stepDescription }) {
                                Text(amount.toString())
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.game_ui_available_exp), Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(gameNumber(remaining), style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold)
                }
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth()) {
                        page.attributes.forEachIndexed { index, attribute ->
                            val current = AttributeAllocation.currentValue(attribute, deltas)
                            val delta = (deltas[attribute.key] ?: attribute.pendingDelta) - attribute.pendingDelta
                            AttributeRow(
                                attribute = attribute, current = current, delta = delta,
                                nextCost = AttributeAllocation.nextExperienceCost(current),
                                canDecrease = enabled && page.canEditAttributes && AttributeAllocation.canDecrease(attribute, deltas),
                                canIncrease = enabled && page.canEditAttributes && AttributeAllocation.canIncrease(page, attribute, deltas),
                                onAdjust = { direction ->
                                    draft = AllocationDraft(snapshotKey,
                                        AttributeAllocation.adjustBatch(page, attribute, deltas, direction, step))
                                },
                            )
                            if (index < page.attributes.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                        }
                    }
                }
                if (!page.canEditAttributes) Text(stringResource(R.string.game_ui_attributes_unavailable), color = MaterialTheme.colorScheme.error)
            }
            page.proficiencyGroups.forEach { StatGroup(it) }
            page.statGroups.forEach { StatGroup(it) }
        }
        if (dirty) Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(enabled = !busy, onClick = { resetDraft() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.game_ui_undo)) }
                Button(enabled = enabled && page.canEditAttributes && remaining >= 0, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    onClick = { onApplyAttributes(deltas) }) { Text(stringResource(R.string.game_ui_apply)) }
            }
        }
    }

    if (renameOpen) AlertDialog(
        onDismissRequest = { renameOpen = false }, title = { Text(stringResource(R.string.game_ui_rename_persona)) },
        text = { OutlinedTextField(value = renameValue, onValueChange = { renameValue = it },
            label = { Text(stringResource(R.string.game_ui_persona_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(enabled = renameValue.trim().isNotEmpty() && renameValue.trim() != page.personaName,
            onClick = { renameOpen = false; onRenamePersona(renameValue.trim()) }) { Text(stringResource(R.string.game_ui_save)) } },
        dismissButton = { TextButton(onClick = { renameOpen = false }) { Text(stringResource(R.string.game_ui_cancel)) } },
    )
    createKind?.let { kind ->
        AlertDialog(
            onDismissRequest = { createKind = null },
            title = { Text(stringResource(if (kind == "clone") R.string.game_ui_clone_persona_title else R.string.game_ui_blank_persona_title)) },
            text = { Text(if (kind == "clone") stringResource(R.string.game_ui_clone_persona_message) else stringResource(R.string.game_ui_blank_persona_message)) },
            confirmButton = { Button(onClick = { createKind = null; onCreatePersona(kind) }) { Text(stringResource(R.string.game_ui_create)) } },
            dismissButton = { TextButton(onClick = { createKind = null }) { Text(stringResource(R.string.game_ui_cancel)) } },
        )
    }
    discardAction?.let { action ->
        AlertDialog(onDismissRequest = { discardAction = null }, title = { Text(stringResource(R.string.game_ui_discard_attributes)) },
            confirmButton = { TextButton(onClick = { resetDraft(); discardAction = null; action() }) { Text(stringResource(R.string.game_ui_discard)) } },
            dismissButton = { TextButton(onClick = { discardAction = null }) { Text(stringResource(R.string.game_ui_continue_editing)) } })
    }
}

@Composable
private fun AttributeRow(
    attribute: CharacterAttribute, current: Int, delta: Int, nextCost: Long,
    canDecrease: Boolean, canIncrease: Boolean, onAdjust: (Int) -> Unit,
) {
    var details by remember { mutableStateOf(false) }
    val stackedControls = useStackedControls(360.dp)
    val attributeName = gameText(attribute.name)
    val detailDescription = stringResource(R.string.game_ui_named_details, attributeName)
    val label: @Composable () -> Unit = {
        Column(Modifier.padding(start = 8.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(gameText(attribute.name), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                if (delta != 0) Text(signedNumber(delta), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            Text(stringResource(R.string.game_ui_exp_amount, gameNumber(nextCost)), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        if (stackedControls) Box(Modifier.fillMaxWidth().padding(start = 8.dp, top = 12.dp, end = 16.dp)) { label() }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (stackedControls) Spacer(Modifier.weight(1f)) else Box(Modifier.weight(1f)) { label() }
            FilledTonalIconButton(onClick = { onAdjust(-1) }, enabled = canDecrease, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Remove, stringResource(R.string.game_ui_decrease_attribute, attributeName))
            }
            TextButton(onClick = { details = true }, contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.widthIn(min = 56.dp).semantics { contentDescription = detailDescription }) {
                Text(gameNumber(current.toLong()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            FilledTonalIconButton(onClick = { onAdjust(1) }, enabled = canIncrease, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Add, stringResource(R.string.game_ui_increase_attribute, attributeName))
            }
        }
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text(gameText(attribute.name)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.game_ui_base_attribute, gameNumber(current.toLong())))
            val bonuses = listOf(attribute.equipmentBonus, attribute.secondaryBonus).filter { it.isNotBlank() }
            if (bonuses.isNotEmpty()) Text(stringResource(R.string.game_ui_attribute_bonus, bonuses.joinToString(" / ")))
            Text(stringResource(R.string.game_ui_next_attribute, gameNumber(nextCost)))
        } }, confirmButton = { TextButton(onClick = { details = false }) { Text(stringResource(R.string.game_ui_close)) } })
}

@Composable
private fun StatGroup(group: CharacterStatGroup) {
    var expanded by rememberSaveable(group.name) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Surface(onClick = { expanded = !expanded }, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(messageText(group.name), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, stringResource(if (expanded) R.string.game_ui_collapse else R.string.game_ui_expand))
                }
            }
            if (expanded) Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                group.values.forEach { stat ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(messageText(stat.label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(messageText(stat.value), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun gameNumber(value: Long) = NumberFormat.getIntegerInstance(Locale.US).format(value)
private fun signedNumber(value: Int) = if (value > 0) "+$value" else value.toString()

@Composable
private fun useStackedControls(minimumWidth: Dp): Boolean {
    val width = LocalWindowInfo.current.containerSize.width
    return with(LocalDensity.current) { width.toDp() < minimumWidth || fontScale >= 1.3f }
}
