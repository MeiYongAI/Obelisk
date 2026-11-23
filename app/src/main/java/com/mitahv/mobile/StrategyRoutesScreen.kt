package com.mitahv.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.data.JpxCatalog
import com.mitahv.mobile.model.BattleScene
import com.mitahv.mobile.model.BattleStyle

@Composable
internal fun StrategyControls(automatic: Boolean, enabled: Boolean, onAutomatic: (Boolean) -> Unit, onRoutes: () -> Unit) {
    ListItem(modifier = Modifier.toggleable(value = automatic, enabled = enabled, role = Role.Switch, onValueChange = onAutomatic),
        content = { Text(stringResource(R.string.jpx_auto)) },
        trailingContent = { Switch(checked = automatic, onCheckedChange = null, enabled = enabled) })
    ListItem(modifier = Modifier.clickable(enabled = enabled, onClick = onRoutes),
        content = { Text(stringResource(R.string.jpx_routes)) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) })
    HorizontalDivider(Modifier.padding(bottom = 12.dp))
}

@Composable
internal fun StrategyRoutesScreen(catalog: JpxCatalog, detected: BattleStyle?, world: String, busy: Boolean, error: Int,
    onBack: () -> Unit, onAssign: (BattleStyle, BattleScene, Long?) -> Unit) {
    var style by rememberSaveable { mutableStateOf(detected ?: BattleStyle.entries.firstOrNull { s ->
        catalog.entries.any { it.mode.substringBeforeLast('_') == s.key }
    } ?: BattleStyle.OneHanded) }
    var pickingStyle by rememberSaveable { mutableStateOf(false) }
    var pickingScene by rememberSaveable { mutableStateOf<BattleScene?>(null) }
    val auto = catalog.copy(manual = false)
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.app_back)) }
                    Text(stringResource(R.string.jpx_routes), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                }
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    item {
                        if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
                        ListItem(modifier = Modifier.clickable { pickingStyle = true }, content = { Text(styleLabel(style)) },
                            overlineContent = { Text(stringResource(R.string.jpx_route_style)) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) })
                        HorizontalDivider()
                    }
                    items(BattleScene.entries.filter { it != BattleScene.Tower || world == "Isekai" }, key = { it.name }) { scene ->
                        val bound = catalog.routes[style.slot(scene)]
                        val entry = auto.resolve(style, scene)?.entry
                        val summary = entry?.let { profileLabel(it, catalog.variant(it)) } ?: stringResource(R.string.jpx_no_match)
                        ListItem(modifier = Modifier.clickable(enabled = !busy) { pickingScene = scene },
                            content = { Text(stringResource(scene.label)) },
                            supportingContent = { Text(stringResource(if (bound == null) R.string.jpx_route_auto else R.string.jpx_route_bound, summary)) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) })
                    }
                }
            }
        }
    }
    if (pickingStyle) ChoiceDialog(stringResource(R.string.jpx_route_style), BattleStyle.entries, style,
        label = { styleLabel(it) }, onClose = { pickingStyle = false }) { style = it; pickingStyle = false }
    pickingScene?.let { scene ->
        val candidates = catalog.entries.filter { it.mode.substringBeforeLast('_') == style.key && it.publishedRules > 0 }
        val target = style.slot(scene)
        ChoiceDialog(modeLabel(target), listOf(null) + candidates, candidates.find { it.id == catalog.routes[target] },
            label = { if (it == null) stringResource(R.string.jpx_auto_match) else profileLabel(it, catalog.variant(it)) },
            onClose = { pickingScene = null }) { entry -> pickingScene = null; onAssign(style, scene, entry?.id) }
    }
}

@Composable
private fun <T> ChoiceDialog(title: String, choices: List<T>, current: T, label: @Composable (T) -> String,
    onClose: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text(title) }, text = {
        LazyColumn(Modifier.heightIn(max = 420.dp).selectableGroup()) {
            items(choices) { choice ->
                Row(Modifier.fillMaxWidth().selectable(choice == current, role = Role.RadioButton) { onPick(choice) }
                    .heightIn(min = 52.dp).padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadioButton(selected = choice == current, onClick = null)
                    Text(label(choice), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.game_ui_cancel)) } })
}
