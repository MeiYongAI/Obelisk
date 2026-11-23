package com.mitahv.mobile

import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.google.gson.JsonParseException
import com.mitahv.mobile.data.*
import kotlinx.coroutines.*
import java.io.IOException

@Composable
internal fun BattleConfigsScreen(library: JpxLibrary, onBack: () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(emptyList<JpxEntry>()) }
    var draft by remember { mutableStateOf<JpxDraft?>(null) }
    var deleting by remember { mutableStateOf<JpxEntry?>(null) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableIntStateOf(0) }
    suspend fun perform(block: suspend () -> Unit) {
        busy = true
        error = 0
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (_: OversizedConfig) { error = R.string.jpx_large }
        catch (_: WrongConfigWorld) { error = R.string.jpx_wrong_world }
        catch (_: JsonParseException) { error = R.string.jpx_format }
        catch (_: IllegalArgumentException) { error = R.string.jpx_format }
        catch (_: Exception) { error = R.string.jpx_io }
        finally { busy = false }
    }
    suspend fun reload() { entries = withContext(Dispatchers.IO) { library.entries() } }
    fun change(block: () -> Unit) { scope.launch { perform { withContext(Dispatchers.IO) { block() }; reload() } } }
    LaunchedEffect(library) { perform { reload() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { perform {
            draft = withContext(Dispatchers.IO) {
                require(uri.scheme == "content")
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
                resolver.openInputStream(uri)?.use { JpxImport.read(it, name ?: "JPX", library.world) } ?: throw IOException()
            }
        } }
    }
    BackHandler(onBack = onBack)
    ConfigLibraryContent(
        world = library.world, entries = entries, busy = busy, error = error, onBack = onBack,
        onImport = { picker.launch(arrayOf("*/*")) },
        onSelect = { change { library.select(it.id) } }, onRemove = { deleting = it },
    )
    draft?.let { candidate -> AlertDialog(onDismissRequest = { draft = null }, title = { Text(stringResource(R.string.jpx_preview)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(candidate.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.jpx_summary, candidate.modes.size, candidate.rules))
            HorizontalDivider()
            for (mode in candidate.modes) Text(modeLabel(mode), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.jpx_preview_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }, confirmButton = { TextButton(onClick = { draft = null; change { library.save(candidate) } }) { Text(stringResource(R.string.jpx_save)) } },
        dismissButton = { TextButton(onClick = { draft = null }) { Text(stringResource(R.string.game_ui_cancel)) } }) }
    deleting?.let { entry -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.jpx_remove)) },
        text = { Text(stringResource(R.string.jpx_confirm_remove, entry.name)) },
        confirmButton = { TextButton(onClick = { deleting = null; change { library.remove(entry.id) } }) { Text(stringResource(R.string.jpx_remove)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.game_ui_cancel)) } }) }
}

@Composable
private fun ConfigLibraryContent(
    world: String, entries: List<JpxEntry>, busy: Boolean, error: Int,
    onBack: () -> Unit, onImport: () -> Unit, onSelect: (JpxEntry) -> Unit, onRemove: (JpxEntry) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.app_back)) }
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.jpx_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.jpx_scope, world), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                if (entries.isNotEmpty()) TextButton(onClick = onImport, enabled = !busy) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.jpx_import))
                }
            }
            LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) {
                if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (error != 0) item {
                    Text(stringResource(error), Modifier.padding(vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }, color = colors.error)
                }
                if (entries.isEmpty() && !busy) item {
                    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            Icon(Icons.Filled.FolderOpen, null, tint = colors.primary)
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.jpx_empty), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.jpx_hint), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onImport) { Text(stringResource(R.string.jpx_import)) }
                        }
                    }
                }
                if (entries.isNotEmpty()) item {
                    Text(stringResource(R.string.jpx_saved, entries.size), Modifier.padding(start = 8.dp, bottom = 8.dp).semantics { heading() },
                        style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                }
                items(entries, key = { it.id }) { entry ->
                    ConfigRow(entry, !busy, onSelect = { onSelect(entry) }, onRemove = { onRemove(entry) })
                }
            }
        }
    }
}

@Composable
private fun ConfigRow(entry: JpxEntry, enabled: Boolean, onSelect: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = if (entry.selected) colors.secondaryContainer else colors.surface) {
        Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).selectable(selected = entry.selected, enabled = enabled, role = Role.RadioButton,
                onClick = { if (!entry.selected) onSelect() }).heightIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RadioButton(selected = entry.selected, onClick = null, enabled = enabled)
                Column {
                    Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.jpx_summary, entry.modes.split(", ").size, entry.rules), style = MaterialTheme.typography.bodySmall)
                }
            }
            Box {
                IconButton(enabled = enabled, onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.jpx_options, entry.name))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.jpx_remove)) }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

@Composable
private fun modeLabel(key: String): String {
    val family = key.substringBeforeLast('_')
    val label = when (family) {
        "OneHanded", "1H_Mage" -> R.string.strategy_one
        "TwoHanded", "2H_Mage" -> R.string.strategy_two
        "DualWielding", "DW_Mage" -> R.string.strategy_dual
        "NitenIchiryu", "NI_Mage" -> R.string.strategy_niten
        "Staff" -> R.string.strategy_staff
        "Unarmed" -> R.string.strategy_unarmed
        else -> return key
    }
    val style = if (family.endsWith("_Mage")) stringResource(R.string.strategy_mage, stringResource(label)) else stringResource(label)
    val scene = when (key.substringAfterLast('_')) {
        "Tower" -> R.string.game_ui_tower
        "Arena" -> R.string.jpx_scene_arena
        "Encounter" -> R.string.jpx_scene_encounter
        "Colosseum" -> R.string.jpx_scene_colosseum
        "Item" -> R.string.jpx_scene_item
        "Battle1000" -> R.string.game_ui_grindfest
        "General" -> R.string.jpx_scene_general
        else -> return key
    }
    return stringResource(R.string.jpx_mode_label, style, stringResource(scene))
}
