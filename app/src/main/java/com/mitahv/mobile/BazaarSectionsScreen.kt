package com.mitahv.mobile

import androidx.compose.ui.res.stringResource
import com.mitahv.mobile.i18n.LocalMessageTranslator
import com.mitahv.mobile.i18n.messageText

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.model.*
import java.security.MessageDigest

internal class BazaarDraft(val snapshot: String, query: String, initialValues: Map<String, String>) {
    val query = mutableStateOf(query)
    val values = mutableStateMapOf<String, String>().apply { putAll(initialValues) }
}

internal val BazaarDraftSaver = mapSaver<BazaarDraft>(
    save = { draft -> mapOf("snapshot" to draft.snapshot, "query" to draft.query.value) +
        draft.values.mapKeys { "field:${it.key}" } },
    restore = { saved -> BazaarDraft(saved.getValue("snapshot") as String, saved.getValue("query") as String,
        saved.filterKeys { it.startsWith("field:") }.mapKeys { it.key.removePrefix("field:") }.mapValues { it.value as String }) },
)

/** Bind restored edits to the server form and world, while keeping hidden controls out of saved state. */
internal fun bazaarDraftSnapshot(page: BazaarSectionsPage, initialValues: Map<String, String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    fun add(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        digest.update(bytes.size.toString().toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        digest.update(bytes)
    }
    fun addAction(action: NativeAction) {
        add(action.url.toString())
        add(action.method)
        action.fields.filterNot { it.first.contains("token", ignoreCase = true) }
            .forEach { (name, value) -> add(name); add(value) }
    }
    fun addEntry(entry: BazaarSectionsEntry) {
        add(entry.key)
        entry.action?.let(::addAction)
        entry.quantity?.action?.let(::addAction)
        entry.choices.forEach(::addAction)
    }
    add(page.route.name)
    page.forms.forEach { form ->
        add(form.key)
        form.actions.forEach(::addAction)
        form.attachments.forEach(::addEntry)
        form.fields.forEach { field -> add(field.key); add(field.name); add(field.kind); add(field.value) }
    }
    initialValues.toSortedMap().forEach { (name, value) -> add(name); add(value) }
    page.links.forEach { addAction(it.action) }
    page.actions.forEach(::addAction)
    page.sections.forEach { it.entries.forEach(::addEntry) }
    return digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun BazaarSectionsScreen(page: BazaarSectionsPage, access: PageInteraction, onAction: (NativeAction) -> Unit,
    onDraftChanged: (Boolean) -> Unit = {}, responseRevision: Int = 0) {
    val busy = !access.canSubmit
    val translator = LocalMessageTranslator.current
    val listState = rememberLazyListState()
    val initialValues = remember(page) { buildMap {
        page.forms.forEach { form -> form.fields.forEach { field ->
            put("${form.key}/${field.key}", if (field.kind == "checkbox" && !field.checked) "" else field.value)
        } }
    } }
    val snapshot = remember(page, initialValues) { bazaarDraftSnapshot(page, initialValues) }
    // Keep UI selections across detail enrichment, and resolve their data from the current page.
    var selected by remember(snapshot, responseRevision) { mutableStateOf<BazaarSelection?>(null) }
    var savedDraft by rememberSaveable(snapshot, stateSaver = BazaarDraftSaver) {
        mutableStateOf(BazaarDraft(snapshot, "", initialValues))
    }
    val draft = if (savedDraft.snapshot == snapshot) savedDraft else remember(snapshot) {
        BazaarDraft(snapshot, "", initialValues)
    }
    SideEffect { if (savedDraft !== draft) savedDraft = draft }
    var query by draft.query
    val values = draft.values
    var operationFormKey by remember(snapshot, responseRevision) { mutableStateOf<String?>(null) }
    val operationForms = page.forms.filter { form ->
        page.route == HvRoute.MonsterLab && form.key == "monster-rename"
    }
    val inlineForms = page.forms.filterNot { it in operationForms }
    val lottery = page.route in setOf(HvRoute.WeaponLottery, HvRoute.ArmorLottery)
    val ticketEntry = if (lottery) page.sections.flatMap { it.entries }.firstOrNull { it.key == "tickets" } else null
    val summaryIndex = page.sections.indexOfFirst { group ->
        page.route == HvRoute.MonsterLab && group.facts.any { it.first == "Slot" }
    }.takeIf { it >= 0 }
    val topPageActions = page.route == HvRoute.MonsterLab && summaryIndex == null
    val menuPageActions = operationForms.isNotEmpty()
    val changed = values.toMap() != initialValues
    // Unchanged server fields can still be a new response after a rejected edit.
    LaunchedEffect(snapshot, changed, responseRevision) { onDraftChanged(changed) }
    fun formValues(form: BazaarSectionsForm) = form.fields.associate { it.key to values["${form.key}/${it.key}"].orEmpty() }
    fun submit(action: NativeAction, form: BazaarSectionsForm?) {
        if (access.allows(action)) onAction(if (form == null) action else form.submit(action, formValues(form)))
    }
    fun canOpen(entry: BazaarSectionsEntry) = entry.enabled && when {
        entry.quantity != null -> access.allows(entry.quantity.action)
        entry.choices.isNotEmpty() -> entry.choices.any(access::allows)
        else -> access.allows(entry.action)
    }
    fun choose(entry: BazaarSectionsEntry, groupIndex: Int) {
        if (!canOpen(entry)) return
        if (entry.quantity != null || entry.choices.isNotEmpty()) selected = BazaarSelection(entry.key, groupIndex)
        else entry.action?.let { submit(it, null) }
    }
    val showSearch = page.sections.sumOf { it.entries.size } > 15
    Column(Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(page.messages) { message ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Text(messageText(message), Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            if (page.links.isNotEmpty()) item { BazaarPageNavigation(page, access, onAction) }
            summaryIndex?.let { index -> item(key = "summary-$index") {
                val group = page.sections[index]
                MonsterStatusContent(group.title, group.facts)
            } }
            if (operationForms.isNotEmpty()) item {
                var moreOpen by remember { mutableStateOf(false) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    operationForms.forEach { form ->
                        OutlinedButton(onClick = { operationFormKey = form.key }, enabled = !busy) { Text(messageText(form.title)) }
                    }
                    if (page.actions.isNotEmpty()) Box {
                        IconButton(onClick = { moreOpen = true }, enabled = page.actions.any(access::allows)) { Icon(Icons.Default.MoreVert, stringResource(R.string.game_ui_more_actions)) }
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            page.actions.forEach { action ->
                                DropdownMenuItem(text = { Text(messageText(action.label)) }, enabled = access.allows(action),
                                    onClick = { moreOpen = false; onAction(action) })
                            }
                        }
                    }
                }
            }
            if (topPageActions && page.actions.isNotEmpty()) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    page.actions.forEach { action -> FilledTonalButton(onClick = { onAction(action) }, enabled = access.allows(action)) { Text(messageText(action.label)) } }
                }
            }
            if (showSearch) item {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.game_ui_search)) }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) })
            }
            inlineForms.forEach { form ->
                item(key = "${form.key}-heading") {
                    Text(messageText(form.title), style = MaterialTheme.typography.titleLarge)
                    BazaarFacts(form.facts)
                    if (form.presets.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        form.presets.forEach { preset -> OutlinedButton(onClick = { values.putAll(preset.applyTo(values)) }, enabled = !busy) { Text(messageText(preset.label)) } }
                    }
                }
                items(form.fields, key = { "${form.key}/${it.key}" }) { field ->
                    BazaarField(field, values["${form.key}/${field.key}"].orEmpty(), !busy) { newValue ->
                        values["${form.key}/${field.key}"] = newValue
                    }
                }
                item(key = "${form.key}-actions") {
                    val entered = formValues(form)
                    val firstError = form.actions.map { form.validationError(it, entered) }.firstOrNull()
                    val edited = form.fields.any { values["${form.key}/${it.key}"] != initialValues["${form.key}/${it.key}"] }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (edited && firstError != null && form.actions.all { form.validationError(it, entered) != null }) {
                            Text(messageText(firstError), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            form.actions.forEach { action ->
                                Button(onClick = { submit(action, form) }, enabled = access.allows(action) && form.validationError(action, entered) == null) { Text(messageText(action.label)) }
                            }
                        }
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                    }
                }
            }
            page.sections.forEachIndexed { groupIndex, group ->
                if (groupIndex == summaryIndex) return@forEachIndexed
                val entries = group.entries.filter { entry ->
                    entry !== ticketEntry && (query.isBlank() || entry.title.contains(query, true) ||
                        (!entry.hasUserTitle(page.route) && translator.translate(entry.title).contains(query, true)))
                }
                if (group.entries.isEmpty() && group.facts.isEmpty() && group.emptyText.isBlank()) return@forEachIndexed
                item(key = "group-$groupIndex") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (group.title.isNotBlank()) {
                            val title = if (page.route == HvRoute.MonsterLab && group.facts.any { it.first == "Slot" }) group.title
                            else messageText(group.title)
                            Text(title, style = MaterialTheme.typography.titleLarge)
                        }
                        BazaarFacts(group.facts)
                        if (group.emptyText.isNotBlank()) Text(messageText(group.emptyText), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else if (entries.isEmpty() && query.isNotBlank()) Text(
                            stringResource(R.string.game_ui_no_matching_items),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(entries, key = { "$groupIndex/${it.key}" }) { entry ->
                    if (page.route == HvRoute.MonsterLab && entry.key.startsWith("monster-")) {
                        Surface(onClick = { choose(entry, groupIndex = groupIndex) }, enabled = canOpen(entry),
                            shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Box(Modifier.padding(16.dp)) { MonsterStatusContent(entry.title, entry.details) }
                        }
                    }
                    else BazaarEntry(entry, !canOpen(entry), preserveTitle = entry.hasUserTitle(page.route), onClick = { choose(entry, groupIndex = groupIndex) })
                }
            }
            if (page.actions.isNotEmpty() && !topPageActions && !menuPageActions) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    page.actions.forEach { action -> FilledTonalButton(onClick = { onAction(action) }, enabled = access.allows(action)) { Text(messageText(action.label)) } }
                }
            }
        }
        ticketEntry?.let { entry ->
            Surface(tonalElevation = 3.dp) {
                Button(onClick = { choose(entry, groupIndex = page.sections.indexOfFirst { entry in it.entries }) },
                    enabled = canOpen(entry),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 48.dp)) {
                    Text(if (entry.enabled) messageText(entry.title) else stringResource(R.string.game_ui_lottery_insufficient_gp))
                }
            }
        }
    }
    operationForms.firstOrNull { it.key == operationFormKey }?.let { form ->
        BazaarOperationDialog(form, busy, responseRevision, onDismiss = { operationFormKey = null },
            onSubmit = { action -> operationFormKey = null; onAction(action) })
    }
    selected?.let { selection ->
        val entry = page.sections.getOrNull(selection.groupIndex)?.entries?.firstOrNull { it.key == selection.entryKey }
        if (entry != null) key(selection) {
            entry.quantity?.let { quantity ->
                BazaarQuantityDialog(entry, quantity, busy || !entry.enabled, onDismiss = { selected = null }) { count ->
                    selected = null
                    submit(quantity.submit(count), null)
                }
            } ?: AlertDialog(onDismissRequest = { selected = null }, title = {
                if (entry.isEquipmentName()) EquipmentNameText(entry.title, originalName = entry.originalEquipmentName)
                else Text(if (entry.hasUserTitle(page.route)) entry.title else messageText(entry.title))
            }, text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(entry.choices) { action ->
                        TextButton(onClick = { selected = null; submit(action, null) }, enabled = access.allows(action) && entry.enabled, modifier = Modifier.fillMaxWidth()) { Text(messageText(action.label)) }
                    }
                }
            }, confirmButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.game_ui_cancel)) } })
        }
    }
}

private fun BazaarSectionsEntry.hasUserTitle(route: HvRoute): Boolean =
    route == HvRoute.MonsterLab && key.startsWith("monster-")

private fun BazaarSectionsEntry.isEquipmentName(): Boolean =
    key == "lottery-prize" || category == "装备" || originalEquipmentName != null

private data class BazaarSelection(val entryKey: String, val groupIndex: Int)

@Composable
internal fun BazaarSearchDialog(query: String, onDismiss: () -> Unit, onSearch: (String) -> Unit) {
    var entered by rememberSaveable { mutableStateOf(query) }
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_ui_search)) },
        text = {
            OutlinedTextField(
                value = entered,
                onValueChange = { entered = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                label = { Text(stringResource(R.string.game_ui_search_items)) },
                singleLine = true,
                trailingIcon = {
                    if (entered.isNotEmpty()) IconButton(onClick = { entered = "" }) {
                        Icon(Icons.Default.Close, stringResource(R.string.game_ui_clear_search))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(entered.trim()) }),
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        },
        confirmButton = {
            TextButton(onClick = { onSearch(entered.trim()) }) { Text(stringResource(R.string.game_ui_search)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_ui_cancel)) } },
    )
}

@Composable
internal fun BazaarFacts(facts: List<Pair<String, String>>) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        facts.forEach { (label, value) ->
            // Player names and mail content stay verbatim; only their field labels are localized.
            val preserveValue = label in setOf("收件人", "寄信人", "主题", "正文", "To", "From", "Subject", "Body", "Sender", "Recipient",
                "Equip Winner", "Core Winner", "Winner")
            val displayValue = if (preserveValue) value else messageText(value)
            if (label == "正文" || label == "Body") SelectionContainer { Text(value, Modifier.fillMaxWidth().padding(vertical = 8.dp), style = MaterialTheme.typography.bodyLarge) }
            else if (label.isBlank()) Text(displayValue, style = MaterialTheme.typography.bodyMedium)
            else if (largeText || displayValue.length > 42 || '\n' in displayValue) Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(messageText(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(displayValue, style = MaterialTheme.typography.bodyMedium)
            }
            else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(messageText(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(displayValue, Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun BazaarEntry(entry: BazaarSectionsEntry, busy: Boolean, preserveTitle: Boolean = false, onClick: () -> Unit) {
    var showDetails by rememberSaveable(entry.key) { mutableStateOf(false) }
    val interactive = entry.action != null || entry.quantity != null || entry.choices.isNotEmpty()
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().then(when {
            interactive -> Modifier.clickable(enabled = !busy && entry.enabled, onClick = onClick)
            entry.moreDetails.isNotEmpty() -> Modifier.clickable { showDetails = true }
            else -> Modifier
        }).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (entry.isEquipmentName()) EquipmentNameText(entry.title, Modifier.weight(1f),
                    originalName = entry.originalEquipmentName, preserveName = preserveTitle, style = MaterialTheme.typography.titleMedium)
                else Text(if (preserveTitle) entry.title else messageText(entry.title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    color = if (entry.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                if (entry.action?.method == "GET") Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                if (entry.moreDetails.isNotEmpty()) IconButton(onClick = { showDetails = true }) {
                    Icon(Icons.Default.Info, stringResource(R.string.game_ui_show_details))
                }
            }
            BazaarFacts(entry.details)
            val actionLabel = when {
                entry.quantity != null -> messageText(entry.quantity.action.label)
                entry.choices.isNotEmpty() -> stringResource(R.string.game_ui_select)
                entry.action?.method == "POST" -> messageText(entry.action.label)
                else -> null
            }
            if (actionLabel != null) Text(actionLabel, Modifier.align(Alignment.End), style = MaterialTheme.typography.labelLarge,
                color = if (entry.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showDetails) BazaarDetailsDialog(entry.title, entry.moreDetails, preserveTitle = preserveTitle,
        equipmentName = entry.isEquipmentName(), originalEquipmentName = entry.originalEquipmentName) { showDetails = false }
}

@Composable
internal fun BazaarDetailsDialog(title: String, facts: List<Pair<String, String>>, preserveTitle: Boolean = false,
    equipmentName: Boolean = false, originalEquipmentName: String? = null, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            if (equipmentName) EquipmentNameText(title, originalName = originalEquipmentName,
                preserveName = preserveTitle, showOriginal = true)
            else Text(if (preserveTitle) title else messageText(title))
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            ) {
                BazaarFacts(facts)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_ui_close)) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BazaarField(field: BazaarSectionsField, value: String, enabled: Boolean, singleChoice: Boolean = false, onValueChange: (String) -> Unit) {
    var showDetails by rememberSaveable(field.key) { mutableStateOf(false) }
    when (field.kind) {
        "checkbox" -> Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled) { onValueChange(if (value.isBlank()) field.value else "") }, verticalAlignment = Alignment.CenterVertically) {
                if (singleChoice) RadioButton(selected = value.isNotBlank(), onClick = { onValueChange(field.value) }, enabled = enabled)
                else Checkbox(checked = value.isNotBlank(), onCheckedChange = { onValueChange(if (it) field.value else "") }, enabled = enabled)
                if (field.name == "eqids[]") EquipmentNameText(field.label, Modifier.weight(1f),
                    originalName = field.originalEquipmentName, style = MaterialTheme.typography.bodyLarge)
                else Text(messageText(field.label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                if (field.details.isNotEmpty()) IconButton(onClick = { showDetails = true }) {
                    Icon(Icons.Default.Info, stringResource(R.string.game_ui_show_stats))
                }
            }
            if (showDetails) BazaarDetailsDialog(field.label, field.details, equipmentName = field.name == "eqids[]",
                originalEquipmentName = field.originalEquipmentName) { showDetails = false }
        }
        "select" -> Column {
            Text(messageText(field.label), style = MaterialTheme.typography.labelLarge)
            if (field.options.size > 6) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(messageText(field.options.firstOrNull { it.first == value }?.second ?: field.label), Modifier.weight(1f))
                        Icon(Icons.Default.ExpandMore, null)
                    }
                    if (expanded) MobileChoiceSheet(title = field.label,
                        choices = field.options.map { (option, label) -> MobileChoice(option, label, enabled) },
                        selectedValue = value, onSelect = onValueChange, onDismiss = { expanded = false })
                }
            } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                field.options.forEach { (option, label) -> FilterChip(selected = value == option, onClick = { onValueChange(option) }, label = { Text(messageText(label)) }, enabled = enabled) }
            }
        }
        else -> OutlinedTextField(value, { candidate ->
            if ((field.maxLength == null || candidate.length <= field.maxLength) && (field.kind != "number" || candidate.all(Char::isDigit))) onValueChange(candidate)
        }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text(messageText(field.label)) }, singleLine = field.kind != "textarea",
            minLines = if (field.kind == "textarea") 5 else 1,
            keyboardOptions = KeyboardOptions(keyboardType = if (field.kind == "number") KeyboardType.Number else KeyboardType.Text))
    }
}

@Composable
private fun BazaarQuantityDialog(entry: BazaarSectionsEntry, quantity: BazaarSectionsQuantity, busy: Boolean, onDismiss: () -> Unit, onSubmit: (Long) -> Unit) {
    var value by remember(quantity.field, quantity.initial) { mutableStateOf(quantity.initial.coerceIn(quantity.minimum, quantity.maximum).toString()) }
    val count = value.toLongOrNull()
    val valid = count != null && count in quantity.minimum..quantity.maximum
    AlertDialog(onDismissRequest = onDismiss, title = {
        if (entry.isEquipmentName()) EquipmentNameText(entry.title, originalName = entry.originalEquipmentName)
        else Text(messageText(entry.title))
    }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { value = ((count ?: quantity.minimum) - 1).coerceAtLeast(quantity.minimum).toString() }, enabled = !busy && (count ?: 0) > quantity.minimum) { Icon(Icons.Default.Remove, stringResource(R.string.game_ui_decrease_quantity)) }
                OutlinedTextField(value, { if (it.all(Char::isDigit)) value = it }, Modifier.weight(1f), label = { Text(stringResource(if (quantity.field == "action_value") R.string.game_ui_cod else R.string.game_ui_quantity)) }, singleLine = true,
                    isError = !valid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                IconButton(onClick = { value = ((count ?: 0) + 1).coerceAtMost(quantity.maximum).toString() }, enabled = !busy && (count ?: 0) < quantity.maximum) { Icon(Icons.Default.Add, stringResource(R.string.game_ui_increase_quantity)) }
            }
            if (quantity.maximum != Long.MAX_VALUE) Text(stringResource(R.string.game_ui_maximum_quantity, quantity.maximum), style = MaterialTheme.typography.bodyMedium)
            if (valid && quantity.unitCost != null) Text(stringResource(R.string.game_ui_cost_amount, count!! * quantity.unitCost, messageText(quantity.currency)), style = MaterialTheme.typography.headlineSmall)
        }
    }, confirmButton = { Button(onClick = { count?.let(onSubmit) }, enabled = !busy && valid) { Text(messageText(quantity.action.label)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_ui_cancel)) } })
}
