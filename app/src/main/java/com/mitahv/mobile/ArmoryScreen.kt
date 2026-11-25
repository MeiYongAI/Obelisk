package com.mitahv.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.i18n.messageText
import com.mitahv.mobile.model.*

@Composable
internal fun ArmoryScreen(page: BazaarSectionsPage, access: PageInteraction, responseRevision: Int,
    onAction: (NativeAction) -> Unit, onBack: (NativeAction) -> Unit) {
    val armory = page.armory ?: return
    val subpage = armory.mode != ArmoryViewMode.List
    val listStates = rememberSaveableStateHolder()
    BackHandler(enabled = subpage && armory.backAction != null) {
        if (access.canNavigate) onBack(armory.backAction!!)
    }
    Column(Modifier.fillMaxSize()) {
        if (subpage) Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { armory.backAction?.let(onBack) }, enabled = access.allows(armory.backAction)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.app_back))
            }
            Text(messageText(if (armory.mode == ArmoryViewMode.Fusion) "Stat Fusion" else "Modify"),
                style = MaterialTheme.typography.titleLarge)
        }
        Box(Modifier.weight(1f)) {
            if (armory.mode == ArmoryViewMode.Detail) ArmoryDetailContent(page, access, responseRevision, onAction)
            else ArmoryEquipmentList(page, access, responseRevision, listStates, onAction)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArmoryEquipmentList(page: BazaarSectionsPage, access: PageInteraction, responseRevision: Int, listStates: SaveableStateHolder,
    onAction: (NativeAction) -> Unit) {
    val armory = page.armory ?: return
    val busy = !access.canSubmit
    val form = page.forms.firstOrNull { f -> f.fields.any { it.name == "eqids[]" } }
    val initial = remember(form) { form?.fields?.associate { it.key to if (it.kind == "checkbox" && !it.checked) "" else it.value }.orEmpty() }
    val snapshot = remember(page, initial) { bazaarDraftSnapshot(page, initial) }
    var savedDraft by rememberSaveable(snapshot, stateSaver = BazaarDraftSaver) { mutableStateOf(BazaarDraft(snapshot, "", initial)) }
    val draft = if (savedDraft.snapshot == snapshot) savedDraft else remember(snapshot) { BazaarDraft(snapshot, "", initial) }
    SideEffect { if (savedDraft !== draft) savedDraft = draft }
    val fields = form?.fields?.filter { it.name == "eqids[]" }.orEmpty()
    val values = draft.values.toMap()
    val selectedCount = fields.count { !values[it.key].isNullOrBlank() }
    val directOpen = form?.singleEquipment == true && form.actions.singleOrNull()?.method == "GET"
    val canChoose = if (directOpen) access.canNavigate else access.canSubmit
    var operationsOpen by remember { mutableStateOf(false) }
    var filtersOpen by remember { mutableStateOf(false) }
    var reviewOpen by remember(snapshot, responseRevision) { mutableStateOf(false) }
    var protectedKey by remember(snapshot) { mutableStateOf<String?>(null) }
    var detailsKey by remember(snapshot) { mutableStateOf<String?>(null) }
    fun choose(field: BazaarSectionsField) {
        if (!canChoose || form == null) return
        if (directOpen) {
            val entered = values + fields.associate { it.key to if (it.key == field.key) it.value else "" }
            onAction(form.submit(form.actions.single(), entered))
        } else {
            val wasSelected = !draft.values[field.key].isNullOrBlank()
            if (form.singleEquipment) fields.forEach { draft.values[it.key] = "" }
            draft.values[field.key] = if (wasSelected) "" else field.value
        }
    }
    val operation = armory.operations.firstOrNull { it.action.url.queryParameter("screen") == armory.screen }
    val filter = armory.filters.firstOrNull { it.selected } ?: armory.filters.firstOrNull { it.action.url.queryParameter("filter") == armory.filter }
    Column(Modifier.fillMaxSize()) {
        if (armory.mode == ArmoryViewMode.List) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { operationsOpen = true }, enabled = armory.operations.any { access.allows(it.action) },
                    contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(messageText(operation?.label ?: armory.screen))
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ExpandMore, null, Modifier.size(20.dp))
                }
                if (filter != null) TextButton(onClick = { filtersOpen = true }, enabled = access.canNavigate,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(messageText(filter.label), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Default.ExpandMore, null, Modifier.size(20.dp))
                } else Spacer(Modifier.weight(1f))
                if (form != null && !form.singleEquipment && fields.isNotEmpty()) {
                    val eligible = fields.filter { it.selectionConfirmation == null }
                    val allSelected = eligible.isNotEmpty() && eligible.all { !values[it.key].isNullOrBlank() }
                    TextButton(onClick = {
                        if (allSelected) fields.forEach { draft.values[it.key] = "" }
                        else eligible.forEach { draft.values[it.key] = it.value }
                    }, enabled = !busy && eligible.isNotEmpty()) {
                        Text(stringResource(if (allSelected) R.string.game_ui_clear_selection else R.string.game_ui_select_all))
                    }
                }
            }
            if (armory.summary.isNotEmpty() || armory.screen == "purchase" && armory.balance != null) {
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    armory.summary.forEach { (label, value) ->
                        val name = when (label.removeSuffix(":")) {
                            "Inventory Capacity" -> stringResource(R.string.armory_inventory)
                            "Storage Capacity" -> stringResource(R.string.armory_storage)
                            else -> messageText(label)
                        }
                        Text("$name  $value", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (armory.screen == "purchase") armory.balance?.let { balance ->
                        Text("${java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(balance)} C",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        form?.armoryStatFusion?.let { fusion ->
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.armory_fusion_target), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                EquipmentNameText(fusion.targetName, style = MaterialTheme.typography.titleMedium)
            }
        }
        listStates.SaveableStateProvider(armory.currentUrl.toString()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(page.messages) { message ->
                    Surface(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium) {
                        Text(messageText(message), Modifier.fillMaxWidth().padding(12.dp))
                    }
                }
                if (fields.isEmpty()) item {
                    val empty = page.sections.firstOrNull { it.emptyText.isNotBlank() && it.title != "Credits" }?.emptyText
                    Text(empty?.let { messageText(it) } ?: stringResource(R.string.game_ui_no_items),
                        Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(fields, key = { it.key }) { field ->
                    val selected = !values[field.key].isNullOrBlank()
                    ArmoryEquipmentRow(field, selected, directOpen, form?.singleEquipment == true, !canChoose,
                        preview = if (armory.screen in setOf("purchase", "sell")) form?.equipmentCosts?.get(field.value)?.credits?.let { "$it C" } else null,
                        onDetails = { detailsKey = field.key }, onClick = {
                            if (!selected && field.selectionConfirmation != null) protectedKey = field.key else choose(field)
                        })
                }
            }
        }
        if (selectedCount > 0 && form != null && !directOpen) Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.game_ui_selection_count, selectedCount), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge)
                Button(onClick = { reviewOpen = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(messageText(operation?.label ?: form.title))
                }
            }
        }
    }
    if (operationsOpen) MobileChoiceSheet(stringResource(R.string.armory_operation),
        armory.operations.mapIndexed { index, link -> MobileChoice(index.toString(), link.label, access.allows(link.action)) },
        selectedValue = armory.operations.indexOf(operation).toString(), searchable = false,
        onSelect = { value -> armory.operations[value.toInt()].takeUnless { it == operation }?.let { onAction(it.action) } },
        onDismiss = { operationsOpen = false })
    if (filtersOpen) MobileChoiceSheet(stringResource(R.string.game_ui_filter),
        armory.filters.mapIndexed { index, link -> MobileChoice(index.toString(), link.label, access.allows(link.action)) },
        selectedValue = armory.filters.indexOf(filter).toString(), searchable = false,
        onSelect = { value -> armory.filters[value.toInt()].takeUnless { it == filter }?.let { onAction(it.action) } },
        onDismiss = { filtersOpen = false })
    if (reviewOpen && form != null) ArmorySelectionSheet(form, values, busy,
        onValueChange = { key, value -> draft.values[key] = value }, onDismiss = { reviewOpen = false },
        onSubmit = { reviewOpen = false; onAction(it) })
    fields.firstOrNull { it.key == detailsKey }?.let { field ->
        BazaarDetailsDialog(field.label, field.details, equipmentName = true, originalEquipmentName = field.originalEquipmentName) { detailsKey = null }
    }
    fields.firstOrNull { it.key == protectedKey }?.let { field ->
        AlertDialog(onDismissRequest = { protectedKey = null }, title = { Text(stringResource(R.string.game_ui_select_protected)) },
            text = { Text(messageText(field.selectionConfirmation.orEmpty())) },
            confirmButton = { TextButton(onClick = { protectedKey = null; choose(field) }, enabled = canChoose) { Text(stringResource(R.string.game_ui_select)) } },
            dismissButton = { TextButton(onClick = { protectedKey = null }) { Text(stringResource(R.string.game_ui_cancel)) } })
    }
}

@Composable
private fun ArmoryEquipmentRow(field: BazaarSectionsField, selected: Boolean, directOpen: Boolean, single: Boolean,
    busy: Boolean, preview: String?, onDetails: () -> Unit, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = !busy,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = if (directOpen) 20.dp else 8.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!directOpen) {
                if (single) RadioButton(selected, onClick = null, enabled = !busy)
                else Checkbox(selected, onCheckedChange = null, enabled = !busy)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EquipmentCandidateName(field.label, originalName = field.originalEquipmentName)
                if (preview != null) Text(preview, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else field.details.firstOrNull()?.second?.takeIf(String::isNotBlank)?.let {
                    Text(messageText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (directOpen) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.padding(12.dp).size(20.dp))
            else if (field.details.isNotEmpty()) IconButton(onClick = onDetails) {
                Icon(Icons.Default.Info, stringResource(R.string.game_ui_show_stats), Modifier.size(20.dp))
            }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ArmorySelectionSheet(form: BazaarSectionsForm, values: Map<String, String>, busy: Boolean,
    onValueChange: (String, String) -> Unit, onDismiss: () -> Unit, onSubmit: (NativeAction) -> Unit) {
    val sheet = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val selected = form.fields.filter { it.name == "eqids[]" && !values[it.key].isNullOrBlank() }
    val summary = form.equipmentSummary(values) + form.armoryStatFusion?.summary(values).orEmpty()
    val options = form.fields.filter { it.name != "eqids[]" }
    val error = form.actions.mapNotNull { form.validationError(it, values) }.firstOrNull()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(messageText(form.title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.game_ui_close)) }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    if (selected.size == 1) EquipmentCandidateName(selected.single().label, originalName = selected.single().originalEquipmentName)
                    else Text(stringResource(R.string.game_ui_selection_count, selected.size), style = MaterialTheme.typography.titleMedium)
                }
                items(options, key = { it.key }) { field ->
                    if (field.name in setOf("set_pinned", "set_stored", "set_locked")) {
                        ArmoryManagementField(field, values[field.key].orEmpty(), !busy) { onValueChange(field.key, it) }
                    } else BazaarField(field, values[field.key].orEmpty(), !busy) { onValueChange(field.key, it) }
                }
                if (summary.isNotEmpty()) item { BazaarFacts(summary) }
                if (error != null) item { Text(messageText(error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                form.actions.forEach { action ->
                    Button(onClick = { onSubmit(form.submit(action, values)) },
                        enabled = !busy && form.validationError(action, values) == null, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(messageText(action.label))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArmoryManagementField(field: BazaarSectionsField, value: String, enabled: Boolean,
    onValueChange: (String) -> Unit) {
    val heading = when (field.name) {
        "set_pinned" -> R.string.armory_pinning
        "set_stored" -> R.string.armory_location
        else -> R.string.armory_protection
    }
    Column {
        Text(stringResource(heading), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            field.options.forEach { (option, sourceLabel) ->
                val label = when (field.name to option) {
                    "set_pinned" to "1" -> R.string.armory_pin
                    "set_pinned" to "-1" -> R.string.armory_unpin
                    "set_stored" to "1" -> R.string.armory_storage
                    "set_stored" to "-1" -> R.string.armory_inventory
                    "set_locked" to "1" -> R.string.armory_protect
                    "set_locked" to "2" -> R.string.armory_lock
                    "set_locked" to "-1" -> R.string.armory_unprotect
                    else -> if (option == "0") R.string.armory_unchanged else null
                }
                FilterChip(selected = value == option, onClick = { onValueChange(option) }, enabled = enabled,
                    label = { Text(label?.let { stringResource(it) } ?: messageText(sourceLabel)) })
            }
        }
    }
}
