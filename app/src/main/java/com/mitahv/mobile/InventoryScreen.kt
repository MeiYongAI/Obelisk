package com.mitahv.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.i18n.gameText
import com.mitahv.mobile.i18n.messageText
import com.mitahv.mobile.model.*

@Composable
internal fun InventoryScreen(page: CharacterSectionsPage, access: PageInteraction,
    onAction: (NativeAction) -> Unit) {
    val busy = !access.canSubmit
    val rows = page.groups.flatMap { it.rows }
    val slots = page.forms.filter { it.inventorySlot != null }
    val families = slots.map { it.inventorySlot!!.kind }.distinct()
    var showingSlots by rememberSaveable { mutableStateOf(false) }
    var family by rememberSaveable { mutableStateOf(CharacterInventoryKind.General) }
    val currentFamily = family.takeIf { it in families } ?: families.firstOrNull()
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var slotId by rememberSaveable { mutableStateOf<String?>(null) }
    val listStates = rememberSaveableStateHolder()

    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = if (showingSlots) 1 else 0) {
            listOf(R.string.game_ui_item_inventory, R.string.game_ui_battle_slots).forEachIndexed { index, label ->
                Tab(selected = showingSlots == (index == 1), onClick = { showingSlots = index == 1 },
                    text = { Text(stringResource(label)) })
            }
        }
        if (showingSlots && families.size > 1) SecondaryTabRow(selectedTabIndex = families.indexOf(currentFamily)) {
            families.forEach { kind ->
                Tab(selected = currentFamily == kind, onClick = { family = kind },
                    text = { Text(stringResource(kind.label)) })
            }
        }
        listStates.SaveableStateProvider(if (showingSlots) "slots:$currentFamily" else "items") {
            if (showingSlots) {
                val visibleSlots = slots.filter { it.inventorySlot?.kind == currentFamily }
                LazyVerticalGrid(columns = GridCells.Adaptive(156.dp), modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (page.notices.isNotEmpty()) item(key = "notices", span = { GridItemSpan(maxLineSpan) }) {
                        InventoryNotices(page.notices)
                    }
                    if (visibleSlots.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        InventoryEmpty(R.string.game_ui_no_slots)
                    }
                    items(visibleSlots, key = { it.inventorySlot!!.id }) { slot ->
                        InventorySlotTile(slot, !access.canNavigate) { slotId = slot.inventorySlot!!.id }
                    }
                }
            } else LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (page.notices.isNotEmpty()) item(key = "notices") { InventoryNotices(page.notices) }
                if (rows.isEmpty()) item { InventoryEmpty(R.string.game_ui_no_items) }
                rows.groupBy { it.inventory?.category.orEmpty() }.forEach { (category, items) ->
                    if (category.isNotBlank()) item(key = "category:$category") {
                        Text(gameText(category), Modifier.padding(horizontal = 4.dp, vertical = 8.dp).semantics { heading() },
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    items(items, key = { it.inventory?.id ?: it.title }) { row ->
                        val equipped = slots.any { it.fields.firstOrNull()?.value == row.inventory?.id }
                        InventoryItemRow(row, equipped) { detailId = row.inventory?.id ?: row.title }
                    }
                }
            }
        }
    }
    rows.firstOrNull { (it.inventory?.id ?: it.title) == detailId }?.let { row ->
        InventoryItemDetails(row, busy, onDismiss = { detailId = null },
            onAction = { detailId = null; onAction(it) })
    }
    slots.firstOrNull { it.inventorySlot?.id == slotId }?.let { form ->
        InventorySlotSheet(form, busy, onDismiss = { slotId = null },
            onAction = { slotId = null; onAction(it) })
    }
}

private val CharacterInventoryKind.label: Int
    get() = when (this) {
        CharacterInventoryKind.General -> R.string.inventory_ui_general
        CharacterInventoryKind.Scroll -> R.string.inventory_ui_scrolls
        CharacterInventoryKind.Infusion -> R.string.inventory_ui_infusions
    }

@Composable
private fun InventoryNotices(notices: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        notices.forEach { notice ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(messageText(notice), Modifier.fillMaxWidth().padding(12.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun InventoryEmpty(label: Int) {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InventoryQuantity(count: String) {
    if (count.isBlank()) return
    val description = stringResource(R.string.inventory_ui_quantity, count)
    Text("× $count", Modifier.semantics { contentDescription = description },
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun InventoryItemRow(row: CharacterSectionRow, equipped: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ItemArtwork(row.title, row.inventory?.category.orEmpty(), Modifier.size(32.dp))
            Text(gameText(row.title), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (equipped) Icon(Icons.Default.Check, stringResource(R.string.inventory_ui_equipped),
                Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            InventoryQuantity(row.value)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InventoryItemDetails(row: CharacterSectionRow, busy: Boolean, onDismiss: () -> Unit,
    onAction: (NativeAction) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ItemArtwork(row.title, row.inventory?.category.orEmpty(), Modifier.size(36.dp))
                Text(gameText(row.title))
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InventoryQuantity(row.value)
                if (row.expandedDetail.isNotBlank()) Text(gameText(row.expandedDetail))
            }
        },
        confirmButton = {
            row.actions.firstOrNull()?.let { action ->
                Button(onClick = { onAction(action) }, enabled = !busy) { Text(stringResource(R.string.inventory_ui_equip)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_ui_close)) } },
    )
}

@Composable
private fun InventorySlotTile(form: CharacterSectionForm, busy: Boolean, onClick: () -> Unit) {
    val field = form.fields.first()
    val option = field.options.firstOrNull { it.value == field.value }
    val empty = field.value == "0"
    Surface(onClick = onClick, enabled = !busy && field.enabled, shape = MaterialTheme.shapes.large,
        color = if (empty) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (empty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer) {
        Column(Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(characterLabel(form.title), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                if (empty) Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                else if (option != null) ItemArtwork(option.itemName, "", Modifier.size(28.dp))
            }
            Text(if (empty) stringResource(R.string.character_ui_empty) else gameText(option?.itemName.orEmpty()),
                style = MaterialTheme.typography.titleSmall)
        }
    }
}

// The parser keeps the original option label for other form consumers.
private val CharacterFieldOption.itemName: String get() = label.substringBeforeLast(" · ", label)
private val CharacterFieldOption.itemCount: String get() = label.substringAfterLast(" · ", "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventorySlotSheet(form: CharacterSectionForm, busy: Boolean, onDismiss: () -> Unit,
    onAction: (NativeAction) -> Unit) {
    val field = form.fields.first()
    var selected by rememberSaveable(form.inventorySlot?.id, field.value) { mutableStateOf(field.value) }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val title = characterLabel(form.title)
    val choices = remember(field) { field.options.sortedBy { if (it.value == field.value) 0 else 1 } }
    val values = mapOf(field.name to selected)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.semantics { paneTitle = title }) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.game_ui_close)) }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).selectableGroup(),
            contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(choices, key = { it.value }) { option ->
                val chosen = selected == option.value
                Surface(selected = chosen, onClick = { selected = option.value },
                    enabled = !busy && field.enabled && option.enabled,
                    shape = MaterialTheme.shapes.large,
                    color = if (chosen) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = if (chosen) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(Modifier.heightIn(min = 48.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (option.value != "0") ItemArtwork(option.itemName, "", Modifier.size(28.dp))
                            Text(if (option.value == "0") stringResource(R.string.character_ui_empty) else gameText(option.itemName),
                                Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            InventoryQuantity(option.itemCount)
                            if (chosen) Icon(Icons.Default.Check, null, Modifier.size(20.dp))
                        }
                        if (chosen && option.detail.isNotBlank()) Text(gameText(option.detail), Modifier.padding(bottom = 12.dp),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Button(onClick = { onAction(form.actionWith(values, emptyMap())) },
            enabled = !busy && field.enabled && selected != field.value && form.canSubmit(values),
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp)) {
            Text(stringResource(if (selected == "0" && field.value != "0") R.string.inventory_ui_remove else R.string.inventory_ui_equip))
        }
    }
}
