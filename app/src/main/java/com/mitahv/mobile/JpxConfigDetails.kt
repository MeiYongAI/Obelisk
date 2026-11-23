package com.mitahv.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.mitahv.mobile.data.JpxDocument

internal data class RuleAddress(val group: String, val index: Int) {
    val key get() = "$group#$index"
}
internal typealias RuleEdit = (MutableList<JsonObject>) -> Unit

@Composable
internal fun JpxConfigDetails(doc: JpxDocument, subtitle: String, busy: Boolean, error: Int, onBack: () -> Unit,
    onApply: () -> Unit, onEdit: (String, RuleEdit, () -> Unit) -> Unit) {
    val mode = doc.draft.modes.single()
    var editing by remember { mutableStateOf<RuleAddress?>(null) }
    var deleting by remember { mutableStateOf<RuleAddress?>(null) }
    val list = rememberLazyListState()
    val groups = remember(doc) { doc.groups(mode).associateWith { doc.rules(mode, it).map { row -> row.asJsonObject } } }
    val offsets = remember(groups) {
        var offset = 0
        groups.mapValues { (group, rows) -> if (group.startsWith("kb_")) 0 else offset.also { offset += rows.size } }
    }
    val drag = remember(doc) { RuleReorder(list, groups.flatMap { (group, rows) -> rows.indices.map { RuleAddress(group, it) } }.associateBy { it.key }) }
    fun move(from: RuleAddress, to: Int) {
        if (!busy && from.index != to) onEdit(from.group, { it.add(to, it.removeAt(from.index)) }, {})
    }
    editing?.let { at ->
        JpxRuleEditor(groups.getValue(at.group).getOrNull(at.index), busy, error, onBack = { editing = null }) { value ->
            onEdit(at.group, { if (at.index < 0) it.add(value) else it[at.index] = value }, { editing = null })
        }
        return
    }
    BackHandler { if (!busy) onBack() }
    Surface(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = !busy, onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.app_back)) }
            Text(listOf(modeLabel(mode), subtitle).filter(String::isNotBlank).joinToString(" · "),
                style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (error != 0) Text(stringResource(error), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.weight(1f).widthIn(max = 720.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
            .ruleDrag(drag, !busy, ::move), state = list) {
            groups.forEach { (group, rows) ->
                item(key = "header:$group") {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (group.startsWith("kb_")) stringResource(R.string.jpx_keybind, group.removePrefix("kb_")) else fieldLabel(group),
                            Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(enabled = !busy, onClick = { editing = RuleAddress(group, -1) }) { Text(stringResource(R.string.jpx_add_rule)) }
                    }
                }
                items(rows.size, key = { RuleAddress(group, it).key }) { index ->
                    val at = RuleAddress(group, index)
                    val number = offsets.getValue(group) + index + 1
                    val rule = rows[index]
                    val enabled = rule.text("disabled") != "true"
                    val colors = MaterialTheme.colorScheme
                    val dragging = drag.from == at
                    val drop = drag.to == at && !dragging
                    var menu by remember(doc) { mutableStateOf(false) }
                    Surface(color = if (dragging) colors.secondaryContainer else colors.surface) {
                        Box {
                            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.width(48.dp).heightIn(min = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center) {
                                    Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                                    Icon(Icons.Filled.DragHandle, stringResource(R.string.jpx_reorder, number), Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                                }
                                Column(Modifier.weight(1f).clickable(enabled = !busy) { editing = at }.heightIn(min = 48.dp), verticalArrangement = Arrangement.Center) {
                                    val type = rule.text("type")
                                    val name = rule.text("name").ifBlank { rule.text("priorityRule") }
                                    Text(if (name.isBlank()) fieldLabel(type) else optionLabel(name), style = MaterialTheme.typography.titleSmall,
                                        color = if (enabled) colors.onSurface else colors.onSurfaceVariant)
                                    val count = rule.getAsJsonArray("conditions")?.size() ?: 0
                                    if (count > 0) Text(stringResource(R.string.jpx_cond_count, count), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                                val switchLabel = stringResource(R.string.jpx_enable_row, number)
                                Switch(checked = enabled, enabled = !busy, onCheckedChange = { on -> onEdit(group, { it[index].addProperty("disabled", !on) }, {}) },
                                    modifier = Modifier.semantics { contentDescription = switchLabel })
                                Box {
                                    IconButton(enabled = !busy, onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.jpx_options_rule, number)) }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        for ((offset, label) in listOf(-1 to R.string.jpx_up, 1 to R.string.jpx_down)) DropdownMenuItem(
                                            text = { Text(stringResource(label)) }, enabled = index + offset in rows.indices,
                                            onClick = { menu = false; move(at, index + offset) })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.jpx_remove)) }, onClick = { menu = false; deleting = at })
                                    }
                                }
                            }
                            HorizontalDivider(Modifier.align(if (drop && index < (drag.from?.index ?: index)) Alignment.TopCenter else Alignment.BottomCenter),
                                thickness = if (drop) 3.dp else 1.dp, color = if (drop) colors.primary else colors.outlineVariant)
                        }
                    }
                }
            }
        }
        Button(enabled = !busy, onClick = onApply, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { Text(stringResource(R.string.jpx_apply)) }
    } }
    deleting?.let { at -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.jpx_delete_rule)) },
        confirmButton = { TextButton(enabled = !busy, onClick = { onEdit(at.group, { it.removeAt(at.index) }, { deleting = null }) }) { Text(stringResource(R.string.jpx_remove)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.game_ui_cancel)) } }) }
}
