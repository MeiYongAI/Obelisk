package com.mitahv.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.i18n.gameText
import com.mitahv.mobile.i18n.messageText
import com.mitahv.mobile.model.*

private data class SettingsDraft(val snapshot: String, val values: Map<String, String>, val toggles: Map<String, Boolean>)
private val SettingsDraftSaver = mapSaver<SettingsDraft>(
    save = { mapOf("snapshot" to it.snapshot) + it.values.mapKeys { "value:${it.key}" } + it.toggles.mapKeys { "toggle:${it.key}" } },
    restore = { saved -> SettingsDraft(saved.getValue("snapshot") as String,
        saved.filterKeys { it.startsWith("value:") }.mapKeys { it.key.removePrefix("value:") }.mapValues { it.value as String },
        saved.filterKeys { it.startsWith("toggle:") }.mapKeys { it.key.removePrefix("toggle:") }.mapValues { it.value as Boolean }) },
)

@Composable
internal fun GameSettingsScreen(page: CharacterSectionsPage, busy: Boolean, onAction: (NativeAction) -> Unit,
    onDraftChanged: (Boolean) -> Unit, responseRevision: Int) {
    val form = page.combinedSettingsForm() ?: return
    val initialValues = form.fields.associate { it.name to it.value }
    val initialToggles = form.fields.associate { it.name to it.checked }
    val snapshot = "${form.action.url}:" + form.fields.joinToString { "${it.name}=${it.value}/${it.checked}" }
    var draft by rememberSaveable(snapshot, stateSaver = SettingsDraftSaver) {
        mutableStateOf(SettingsDraft(snapshot, initialValues, initialToggles))
    }
    val values = if (draft.snapshot == snapshot) draft.values else initialValues
    val toggles = if (draft.snapshot == snapshot) draft.toggles else initialToggles
    val dirty = values != initialValues || toggles != initialToggles
    val draftCallback by rememberUpdatedState(onDraftChanged)
    LaunchedEffect(dirty, responseRevision) { draftCallback(dirty) }
    DisposableEffect(Unit) { onDispose { draftCallback(false) } }
    var sectionTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var choiceName by rememberSaveable { mutableStateOf<String?>(null) }
    var infoTitle by remember { mutableStateOf<String?>(null) }
    var lootFamily by rememberSaveable { mutableIntStateOf(0) }
    val section = page.forms.firstOrNull { it.title == sectionTitle }
    val listStates = rememberSaveableStateHolder()
    fun updateValue(name: String, value: String) { draft = SettingsDraft(snapshot, values + (name to value), toggles) }
    fun updateToggle(name: String, value: Boolean) { draft = SettingsDraft(snapshot, values, toggles + (name to value)) }
    BackHandler(enabled = section != null && choiceName == null) { sectionTitle = null }

    Column(Modifier.fillMaxSize()) {
        if (section != null) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { sectionTitle = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.app_back)) }
            Text(settingsTitle(section.title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (section.description.isNotBlank()) IconButton(onClick = { infoTitle = section.title }) {
                Icon(Icons.Default.Info, stringResource(R.string.game_ui_details))
            }
        }
        if (section?.title == "Auto-Sell / Auto-Salvage") {
            SecondaryTabRow(selectedTabIndex = lootFamily) {
                listOf(R.string.hv_settings_armor, R.string.hv_settings_weapons, R.string.hv_settings_shields).forEachIndexed { index, label ->
                    Tab(selected = lootFamily == index, onClick = { lootFamily = index }, text = { Text(stringResource(label)) })
                }
            }
        }
        listStates.SaveableStateProvider("${section?.title}:$lootFamily") {
            if (section?.title in listOf("Quickbar Slots", "Auto-Cast Slots")) {
                LazyVerticalGrid(columns = GridCells.Adaptive(148.dp), modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (page.notices.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { SettingsNotices(page.notices) }
                    if (section!!.title == "Auto-Cast Slots" && page.summary.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(messageText(page.summary.joinToString("\n")), Modifier.padding(bottom = 8.dp),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(section.fields, key = { it.name }) { field ->
                        SettingsSlot(field, values[field.name] ?: field.value, busy) { choiceName = field.name }
                    }
                }
            } else LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (page.notices.isNotEmpty()) item { SettingsNotices(page.notices) }
                when {
                    section == null -> page.forms.groupBy { settingsCategory(it.title) }.toSortedMap().forEach { (category, groups) ->
                        item { SettingsHeading(stringResource(categoryLabel(category))) }
                        items(groups, key = { it.title }) { group ->
                            val field = group.fields.singleOrNull()?.takeIf { it.kind == CharacterFieldKind.Choice }
                            val summary = field?.options?.firstOrNull { it.value == (values[field.name] ?: field.value) }
                                ?.let { settingsOptionTitle(field, it) }.orEmpty()
                            SettingsNavigationRow(group, summary, enabled = !busy) {
                                if (field != null) choiceName = field.name else sectionTitle = group.title
                            }
                        }
                    }
                    section.title == "Auto-Sell / Auto-Salvage" -> {
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.hv_settings_equipment), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                                Text(stringResource(R.string.hv_settings_sell), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                                Text(stringResource(R.string.hv_settings_salvage), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        val pairs = section.fields.groupBy { it.name.removePrefix("as_c_").removePrefix("as_s_") }
                            .filter { (id, _) -> lootCategory(id) == lootFamily }.values.toList()
                        items(pairs, key = { it.first().name }) { fields ->
                            SettingsLootRow(fields, values, busy) { choiceName = it.name }
                        }
                    }
                    else -> items(section.fields, key = { it.name }) { field ->
                        when (field.kind) {
                            CharacterFieldKind.Toggle -> SettingsSwitch(field, toggles[field.name] ?: field.checked, !busy) { updateToggle(field.name, it) }
                            CharacterFieldKind.Text -> OutlinedTextField(value = values[field.name] ?: field.value,
                                onValueChange = { if (field.maxLength == null || it.length <= field.maxLength) updateValue(field.name, it) },
                                label = { Text(settingsFieldTitle(field)) },
                                supportingText = if (field.hint.isBlank()) null else ({ Text(messageText(field.hint)) }),
                                keyboardOptions = KeyboardOptions(keyboardType = if (field.name in listOf("fontsize", "fontoff")) KeyboardType.Decimal else KeyboardType.Text),
                                singleLine = true, enabled = !busy && field.enabled, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                            CharacterFieldKind.Choice -> SettingsSlot(field, values[field.name] ?: field.value, busy) { choiceName = field.name }
                        }
                    }
                }
            }
        }
        if (dirty) Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { draft = SettingsDraft(snapshot, initialValues, initialToggles) }, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.hv_settings_undo))
                }
                Button(onClick = { onAction(form.actionWith(values, toggles)) }, enabled = !busy && form.canSubmit(values),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.hv_settings_save)) }
            }
        }
    }
    page.forms.firstOrNull { it.title == infoTitle }?.let { group ->
        AlertDialog(onDismissRequest = { infoTitle = null }, title = { Text(settingsTitle(group.title)) },
            text = { Text(gameText(group.description), Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { infoTitle = null }) { Text(stringResource(R.string.game_ui_close)) } })
    }
    form.fields.firstOrNull { it.name == choiceName }?.let { field ->
        val group = page.forms.first { candidate -> candidate.fields.any { it.name == field.name } }
        SettingsChoiceSheet(field, values[field.name] ?: field.value, busy,
            onSelect = { updateValue(field.name, it); choiceName = null }, onDismiss = { choiceName = null },
            description = group.description.takeIf { group.fields.size == 1 }.orEmpty())
    }
}

private fun settingsCategory(title: String): Int = when (title) {
    "Quickbar Slots", "Auto-Cast Slots", "Equipment Sets" -> 1
    "Auto-Sell / Auto-Salvage", "Shrine Trophy Upgrades" -> 2
    "Font Engine", "Vital Bar Style" -> 3
    else -> 0
}
private fun categoryLabel(category: Int): Int = when (category) {
    1 -> R.string.hv_settings_battle
    2 -> R.string.hv_settings_loot
    3 -> R.string.hv_settings_web
    else -> R.string.hv_settings_general
}
// Original equipment type IDs: armor < 100, weapons/staves 100–216, shields 230–242.
private fun lootCategory(id: String): Int = when (id.toIntOrNull() ?: 0) {
    in 100..229 -> 1
    in 230..242 -> 2
    else -> 0
}

@Composable
private fun settingsTitle(title: String): String = when (title) {
    "Font Engine" -> stringResource(R.string.hv_settings_font)
    "Equipment Sets" -> stringResource(R.string.hv_settings_sets)
    else -> gameText(title)
}

@Composable
private fun settingsFieldTitle(field: CharacterSectionField): String = when (field.name) {
    "fontlocal" -> stringResource(R.string.hv_settings_custom_font)
    "fontface" -> stringResource(R.string.hv_settings_font_family)
    "fontsize" -> stringResource(R.string.hv_settings_font_size)
    "fontwght" -> stringResource(R.string.hv_settings_font_weight)
    "fontstyle" -> stringResource(R.string.hv_settings_font_style)
    "fontoff" -> stringResource(R.string.hv_settings_font_offset)
    "eqs_ability" -> stringResource(R.string.hv_settings_set_abilities)
    "eqs_item" -> stringResource(R.string.game_ui_battle_slots)
    "eqs_skillbar" -> stringResource(R.string.hv_settings_set_skills)
    else -> if (field.name.toIntOrNull() != null) stringResource(R.string.character_ui_slot, field.label)
        else if (field.name.startsWith("as_")) gameText(field.label.substringBefore(" · ")) + " · " +
            stringResource(if (field.name.startsWith("as_c_")) R.string.hv_settings_sell else R.string.hv_settings_salvage)
        else gameText(field.label)
}

@Composable
private fun settingsOptionTitle(field: CharacterSectionField, option: CharacterFieldOption): String = when {
    field.name.startsWith("as_") -> if (option.value == "0") stringResource(R.string.hv_settings_off)
        else "≤ " + gameText(option.label.removePrefix("Sell ").removePrefix("Salvage "))
    field.name == "trophyupgrade" -> when (option.value) {
        "0" -> stringResource(R.string.hv_settings_default)
        "1" -> stringResource(R.string.hv_settings_no_upgrade)
        else -> "T${option.value}"
    }
    field.name == "title_override" -> if (option.value == "0") stringResource(R.string.hv_settings_level_default)
        else gameText(option.label.substringBefore(" · "))
    option.label == "(Not Assigned)" -> stringResource(R.string.character_ui_empty)
    else -> gameText(option.label)
}

@Composable
private fun SettingsNotices(notices: List<String>) {
    notices.forEach { message ->
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
            Text(messageText(message), Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun SettingsHeading(title: String) {
    Text(title, Modifier.padding(horizontal = 4.dp, vertical = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingsNavigationRow(form: CharacterSectionForm, summary: String, enabled: Boolean, onClick: () -> Unit) {
    val icon = when (form.title) {
        "Challenge Level" -> Icons.Default.Speed
        "Display Title" -> Icons.Default.Badge
        "Equipment Sets" -> Icons.Default.Layers
        "Quickbar Slots" -> Icons.Default.ViewModule
        "Auto-Cast Slots" -> Icons.Default.AutoFixHigh
        "Auto-Sell / Auto-Salvage" -> Icons.Default.Recycling
        "Shrine Trophy Upgrades" -> Icons.Default.Redeem
        "Font Engine" -> Icons.Default.TextFields
        else -> Icons.Default.BarChart
    }
    Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(settingsTitle(form.title), style = MaterialTheme.typography.bodyLarge)
                if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SettingsSwitch(field: CharacterSectionField, checked: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .toggleable(checked, enabled = enabled && field.enabled, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(settingsFieldTitle(field), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = checked, onCheckedChange = null, enabled = enabled && field.enabled)
        }
    }
}

@Composable
private fun SettingsSlot(field: CharacterSectionField, value: String, busy: Boolean, onClick: () -> Unit) {
    val option = field.options.firstOrNull { it.value == value }
    Surface(onClick = onClick, enabled = !busy && field.enabled, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(settingsFieldTitle(field), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Default.Edit, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(option?.let { settingsOptionTitle(field, it) } ?: value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun SettingsLootRow(fields: List<CharacterSectionField>, values: Map<String, String>, busy: Boolean,
    onSelect: (CharacterSectionField) -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(gameText(fields.first().label.substringBefore(" · ")), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            listOf("as_c_", "as_s_").forEach { prefix ->
                val field = fields.firstOrNull { it.name.startsWith(prefix) }
                if (field != null) {
                    val option = field.options.firstOrNull { it.value == (values[field.name] ?: field.value) }
                    FilledTonalButton(onClick = { onSelect(field) }, enabled = !busy && field.enabled,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        Text(option?.let { settingsOptionTitle(field, it) }.orEmpty(), style = MaterialTheme.typography.labelLarge)
                    }
                } else Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsChoiceSheet(field: CharacterSectionField, value: String, busy: Boolean,
    onSelect: (String) -> Unit, onDismiss: () -> Unit, description: String) {
    val title = settingsFieldTitle(field)
    var showInfo by remember { mutableStateOf(false) }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.semantics { paneTitle = title }) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            if (description.isNotBlank()) IconButton(onClick = { showInfo = true }) { Icon(Icons.Default.Info, stringResource(R.string.game_ui_details)) }
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.game_ui_close)) }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).selectableGroup(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(field.options, key = { it.value }) { option ->
                val selected = option.value == value
                Surface(selected = selected, onClick = { onSelect(option.value) }, enabled = !busy && field.enabled && option.enabled,
                    shape = MaterialTheme.shapes.large,
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = if (!field.enabled || !option.enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        else if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(settingsOptionTitle(field, option), style = MaterialTheme.typography.bodyLarge)
                            if (field.name == "title_override" && option.value != "0") {
                                val effect = option.label.substringAfter(" · ", "")
                                if (effect.isNotBlank()) Text(gameText(effect), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (selected) Icon(Icons.Default.Check, null, Modifier.size(20.dp))
                    }
                }
            }
        }
    }
    if (showInfo) AlertDialog(onDismissRequest = { showInfo = false }, title = { Text(title) },
        text = { Text(gameText(description), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { showInfo = false }) { Text(stringResource(R.string.game_ui_close)) } })
}
