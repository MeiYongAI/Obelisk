package com.mitahv.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.i18n.LocalGameTranslator
import com.mitahv.mobile.i18n.gameText
import com.mitahv.mobile.i18n.messageText
import com.mitahv.mobile.model.*
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun MailAttachmentRow(entry: BazaarSectionsEntry, busy: Boolean, composing: Boolean,
    onAction: (NativeAction) -> Unit) {
    var detailsOpen by rememberSaveable(entry.key) { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).then(if (entry.moreDetails.isNotEmpty()) Modifier.clickable { detailsOpen = true } else Modifier)
                .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MailAttachmentName(entry, Modifier.weight(1f))
                if (entry.moreDetails.isNotEmpty()) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    stringResource(R.string.game_ui_show_details), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            entry.action?.let { action ->
                IconButton(onClick = { onAction(action) }, enabled = !busy && entry.enabled,
                    modifier = Modifier.padding(end = 4.dp)) {
                    Icon(if (composing) Icons.Default.Close else Icons.Default.Download,
                        stringResource(if (composing) R.string.mail_attach_remove else R.string.mail_attach_take))
                }
            }
        }
    }
    if (detailsOpen) BazaarDetailsDialog(entry.title, entry.moreDetails,
        equipmentName = entry.mailEquipment, originalEquipmentName = entry.originalEquipmentName,
        preserveTitle = entry.originalEquipmentName != null && entry.originalEquipmentName != entry.title) { detailsOpen = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MailAttachmentPicker(form: BazaarSectionsForm, busy: Boolean, onDismiss: () -> Unit,
    onAction: (NativeAction) -> Unit) {
    val candidates = form.attachments.filter { entry ->
        entry.quantity?.action?.fields?.any { it.first == "action" && it.second == "attach_add" } == true
    }
    val categories = listOf("道具", "装备", "货币").filter { category -> candidates.any { it.category == category } }
    var category by rememberSaveable { mutableStateOf(categories.firstOrNull().orEmpty()) }
    val activeCategory = category.takeIf { it in categories } ?: categories.firstOrNull().orEmpty()
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(activeCategory) { mutableStateOf("") }
    val selected = candidates.firstOrNull { it.key == selectedKey }
    val translator = LocalGameTranslator.current
    val categoryItems = candidates.filter { it.category == activeCategory }
    val visible = categoryItems.filter { query.isBlank() || it.title.contains(query, true) ||
        (it.originalEquipmentName == null && translator.translate(it.title).contains(query, true)) }
    val title = stringResource(R.string.mail_attach_add)
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.semantics { paneTitle = title }) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = if (selected == null) 24.dp else 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (selected != null) IconButton(onClick = { selectedKey = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.app_back))
                }
                Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                if (selected == null && categoryItems.size > 15) IconButton(onClick = { searchOpen = !searchOpen; query = "" }) {
                    Icon(Icons.Default.Search, stringResource(R.string.game_ui_search))
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.game_ui_close)) }
            }
            if (selected == null) {
                if (categories.size > 1) SecondaryTabRow(selectedTabIndex = categories.indexOf(activeCategory)) {
                    categories.forEach { option ->
                        Tab(selected = option == activeCategory, onClick = { category = option; searchOpen = false },
                            text = { Text(stringResource(mailAttachmentCategoryLabel(option))) })
                    }
                }
                if (searchOpen) OutlinedTextField(query, { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true, label = { Text(stringResource(R.string.game_ui_search)) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, stringResource(R.string.game_ui_clear_search))
                    } })
                key(activeCategory, query) {
                    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (visible.isEmpty()) item {
                            Text(stringResource(if (query.isBlank()) R.string.game_ui_no_items else R.string.game_ui_no_matching_items),
                                Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(visible, key = { it.key }) { entry ->
                            Surface(onClick = { selectedKey = entry.key }, enabled = !busy && entry.enabled,
                                shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    MailAttachmentName(entry, Modifier.weight(1f))
                                    if (!entry.mailEquipment) Text("× ${mailAttachmentNumber(entry.quantity!!.maximum)}",
                                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            } else key(selected.key) {
                MailAttachmentQuantityPage(selected, busy, onAction)
            }
        }
    }
}

@Composable
private fun ColumnScope.MailAttachmentQuantityPage(entry: BazaarSectionsEntry, busy: Boolean,
    onAction: (NativeAction) -> Unit) {
    val quantity = entry.quantity ?: return
    var value by rememberSaveable(entry.key) { mutableStateOf(quantity.initial.toString()) }
    val count = value.toLongOrNull()
    val valid = count != null && count in quantity.minimum..quantity.maximum
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MailAttachmentName(entry)
        if (entry.details.isNotEmpty()) BazaarFacts(entry.details)
        if (entry.moreDetails.isNotEmpty()) BazaarFacts(entry.moreDetails)
        if (!entry.mailEquipment) MailAttachmentAmountEditor(value, { value = it }, quantity, busy)
    }
    Button(onClick = { if (valid && !busy) onAction(quantity.submit(count!!)) }, enabled = !busy && entry.enabled && valid,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).heightIn(min = 52.dp)) {
        Text(stringResource(R.string.mail_attach_add))
    }
}

@Composable
internal fun MailAttachmentAmountDialog(entry: BazaarSectionsEntry, busy: Boolean, onDismiss: () -> Unit,
    onAction: (NativeAction) -> Unit) {
    val quantity = entry.quantity ?: return
    var value by rememberSaveable(entry.key, quantity.initial) { mutableStateOf(quantity.initial.toString()) }
    val count = value.toLongOrNull()
    val valid = count != null && count in quantity.minimum..quantity.maximum
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mail_attach_cod)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MailAttachmentAmountEditor(value, { value = it }, quantity, busy, cod = true)
                if (quantity.minimum == 0L) Text(stringResource(R.string.mail_attach_cod_zero),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { if (valid && !busy) onAction(quantity.submit(count!!)) }, enabled = !busy && valid) {
            Text(stringResource(R.string.mail_attach_apply))
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_ui_cancel)) } },
    )
}

@Composable
private fun MailAttachmentAmountEditor(value: String, onValueChange: (String) -> Unit,
    quantity: BazaarSectionsQuantity, busy: Boolean, cod: Boolean = false) {
    val count = value.toLongOrNull()
    val valid = count != null && count in quantity.minimum..quantity.maximum
    val focus = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!cod) FilledTonalIconButton(onClick = { onValueChange(((count ?: quantity.minimum) - 1).coerceAtLeast(quantity.minimum).toString()) },
                enabled = !busy && (count ?: quantity.minimum) > quantity.minimum, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Remove, stringResource(R.string.game_ui_decrease_quantity))
            }
            OutlinedTextField(value, { if (it.all(Char::isDigit)) onValueChange(it) }, modifier = Modifier.weight(1f),
                label = { Text(stringResource(if (cod) R.string.mail_attach_credits else R.string.game_ui_quantity)) },
                singleLine = true, enabled = !busy, isError = value.isNotBlank() && !valid,
                textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
            if (!cod) FilledTonalIconButton(onClick = { onValueChange(((count ?: 0).coerceAtMost(quantity.maximum - 1) + 1).toString()) },
                enabled = !busy && (count ?: 0) < quantity.maximum, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Add, stringResource(R.string.game_ui_increase_quantity))
            }
        }
        if (quantity.maximum != Long.MAX_VALUE) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.mail_attach_maximum, mailAttachmentNumber(quantity.maximum)), Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!cod) TextButton(onClick = { onValueChange(quantity.maximum.toString()); focus.clearFocus() }, enabled = !busy) {
                Text(stringResource(R.string.mail_attach_all))
            }
        }
    }
}

private val BazaarSectionsEntry.mailEquipment: Boolean
    get() = category == "装备" || originalEquipmentName != null

@Composable
private fun MailAttachmentName(entry: BazaarSectionsEntry, modifier: Modifier = Modifier) {
    if (entry.mailEquipment) EquipmentNameText(entry.title, modifier, originalName = entry.originalEquipmentName,
        style = MaterialTheme.typography.bodyLarge)
    else Text(gameText(entry.title), modifier, style = MaterialTheme.typography.bodyLarge)
}

private fun mailAttachmentCategoryLabel(category: String): Int = when (category) {
    "装备" -> R.string.mail_attach_equipment
    "货币" -> R.string.mail_attach_currency
    else -> R.string.mail_attach_items
}

private fun mailAttachmentNumber(value: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(value)
