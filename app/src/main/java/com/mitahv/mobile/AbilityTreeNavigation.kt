package com.mitahv.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.i18n.messageText
import com.mitahv.mobile.model.CharacterSectionLink

@Composable
internal fun AbilityTreeBar(links: List<CharacterSectionLink>, selectedId: String?, enabled: Boolean,
    onBrowse: () -> Unit, onSelect: (String) -> Unit) {
    if (links.isEmpty()) return
    val scroll = rememberLazyListState(initialFirstVisibleItemIndex = links.indexOfFirst { it.abilityTreeId == selectedId }.coerceAtLeast(0))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(state = scroll, modifier = Modifier.weight(1f).selectableGroup(), contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(links, key = { it.abilityTreeId ?: it.label }) { link ->
                AbilityTreeChoice(link, selectedId, enabled, onSelect)
            }
        }
        VerticalDivider(Modifier.height(24.dp))
        IconButton(onClick = onBrowse, enabled = enabled) { Icon(Icons.Default.GridView, stringResource(R.string.ability_ui_tree)) }
    }
}

@Composable
private fun AbilityTreeChoice(link: CharacterSectionLink, selectedId: String?, enabled: Boolean, onSelect: (String) -> Unit) {
    val id = link.abilityTreeId ?: return
    val selected = id == selectedId
    val colors = MaterialTheme.colorScheme
    val canSelect = enabled && (link.selected || link.action != null)
    Box(Modifier.selectable(selected, enabled = canSelect, role = Role.Tab) { onSelect(id) }
        .sizeIn(minWidth = 48.dp, minHeight = 48.dp), contentAlignment = Alignment.Center) {
        Surface(shape = MaterialTheme.shapes.small,
            color = if (selected) colors.secondaryContainer else colors.surface,
            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant)) {
            Box(Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 36.dp).padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center) {
                Text(messageText(link.label), textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                    style = MaterialTheme.typography.labelLarge, color = if (canSelect) colors.onSurface else colors.outline)
            }
        }
    }
}

private fun treeGroup(treeId: String?): Int = when (treeId) {
    "general" -> R.string.hv_settings_general
    "onehanded", "twohanded", "dualwield", "niten", "staff" -> R.string.hv_settings_weapons
    "cloth", "light", "heavy" -> R.string.hv_settings_armor
    "elemental", "divine", "forbidden", "deprecating1", "deprecating2", "supportive1", "supportive2" -> R.string.ability_ui_magic
    else -> R.string.ability_ui_other
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AbilityTreeSheet(links: List<CharacterSectionLink>, selectedId: String?, enabled: Boolean, onDismiss: () -> Unit,
    onSelect: (String) -> Unit) {
    val groups = remember(links) { links.groupBy { treeGroup(it.abilityTreeId) } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(stringResource(R.string.ability_ui_tree), Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleLarge)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            groups.forEach { (group, trees) -> item(key = group) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (groups.size > 1 && group != R.string.hv_settings_general) {
                        Text(stringResource(group), Modifier.padding(start = 4.dp).semantics { heading() },
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        trees.forEach { link -> AbilityTreeChoice(link, selectedId, enabled) { id ->
                            onDismiss()
                            onSelect(id)
                        } }
                    }
                }
            } }
        }
    }
}
