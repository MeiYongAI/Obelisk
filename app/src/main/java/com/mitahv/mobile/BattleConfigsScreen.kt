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
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import android.widget.Toast
import android.database.sqlite.SQLiteConstraintException
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
    val context = LocalContext.current
    val resolver = context.contentResolver
    fun notify(message: Int) = Toast.makeText(context,message,Toast.LENGTH_SHORT).show()
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    var document by remember { mutableStateOf<JpxDocument?>(null) }
    var applyId by rememberSaveable { mutableStateOf<Long?>(null) }
    var applyMode by rememberSaveable { mutableStateOf<String?>(null) }
    var exportId by rememberSaveable { mutableStateOf<Long?>(null) }
    var exportMode by rememberSaveable { mutableStateOf<String?>(null) }
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
        catch (_: SQLiteConstraintException) { error = R.string.jpx_both_digests }
        catch (_: OversizedConfig) { error = R.string.jpx_large }
        catch (_: WrongConfigWorld) { error = R.string.jpx_wrong_world }
        catch (_: JsonParseException) { error = R.string.jpx_format }
        catch (_: IllegalArgumentException) { error = R.string.jpx_format }
        catch (_: Exception) { error = R.string.jpx_io }
        finally { busy = false }
    }
    suspend fun reload() { entries = withContext(Dispatchers.IO) { library.entries() } }
    fun change(block: () -> Unit) { scope.launch { perform { withContext(Dispatchers.IO) { block() }; reload() } } }
    LaunchedEffect(library, openId) { perform {
        reload()
        val id = openId
        document = if (id == null) null else withContext(Dispatchers.IO) { JpxDocument(library.load(id)) }
    } }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val id = exportId; val mode = exportMode
        exportId = null; exportMode = null
        if (id != null && uri != null) scope.launch { perform {
            withContext(Dispatchers.IO) {
                val text = JpxDocument(library.load(id)).export(mode)
                resolver.openOutputStream(uri,"wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) } ?: throw IOException()
            }; notify(R.string.jpx_export_ok)
        } }
    }
    fun export(entry: JpxEntry, mode: String?) {
        exportId = entry.id; exportMode = mode
        val stem = entry.name.replace(Regex("(?i)\\.(txt|json)$"), "").replace(Regex("[^\\p{L}\\p{N}_. -]"), "_")
        exporter.launch(stem + (mode?.let { "_$it" } ?: "") + ".json")
    }
    fun confirmApply(entry: JpxEntry, mode: String?) { applyMode = mode; applyId = entry.id }
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
    val entry = entries.find { it.id == openId }; val doc = document
    if (entry != null && doc != null) JpxConfigDetails(doc, busy, error, onBack={ document=null; openId=null },
        onApply={ confirmApply(entry,it) },onExport={ export(entry,it) }) { mode, group, edit, done ->
        scope.launch { perform {
            document = withContext(Dispatchers.IO) {
                val edited = doc.update(mode,group,library.world,edit)
                library.update(entry.id,edited); JpxDocument(edited)
            }; reload(); done(); notify(R.string.jpx_edit_saved)
        } }
    } else ConfigLibraryContent(
        world = library.world, entries = entries, busy = busy, error = error, onBack = onBack,
        onImport = { picker.launch(arrayOf("*/*")) },
        onOpen = { document=null; openId=it.id }, onApply = { confirmApply(it,null) }, onExport={export(it,null)}, onRemove = { deleting = it },
    )
    val applying = entries.find { it.id == applyId }
    if (applying != null) AlertDialog(onDismissRequest={applyId=null},title={Text(stringResource(R.string.jpx_apply))},
        text={ Text(stringResource(R.string.jpx_apply_body,applyMode?.let { modeLabel(it) } ?: applying.name,optionLabel(library.world))) },
        confirmButton={TextButton(enabled=!busy,onClick={val mode=applyMode; applyId=null;scope.launch { perform { withContext(Dispatchers.IO) { library.apply(applying.id,mode) }; reload();notify(R.string.jpx_application_ok) } }}){Text(stringResource(R.string.jpx_apply))}},
        dismissButton={TextButton(onClick={applyId=null}){Text(stringResource(R.string.game_ui_cancel))}})
    draft?.let { candidate -> AlertDialog(onDismissRequest = { draft = null }, title = { Text(stringResource(R.string.jpx_preview)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(candidate.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.jpx_summary, candidate.modes.size, candidate.rules))
            HorizontalDivider()
            for (mode in candidate.modes) Text(modeLabel(mode), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.jpx_preview_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }, confirmButton = { TextButton(onClick = { draft = null; scope.launch { perform { val id = withContext(Dispatchers.IO) { library.save(candidate) }; reload(); openId = id } } }) { Text(stringResource(R.string.jpx_library_save)) } },
        dismissButton = { TextButton(onClick = { draft = null }) { Text(stringResource(R.string.game_ui_cancel)) } }) }
    deleting?.let { entry -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.jpx_remove)) },
        text = { Text(stringResource(R.string.jpx_confirm_remove, entry.name)) },
        confirmButton = { TextButton(onClick = { deleting = null; change { library.remove(entry.id) } }) { Text(stringResource(R.string.jpx_remove)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.game_ui_cancel)) } }) }
}

@Composable
private fun ConfigLibraryContent(
    world: String, entries: List<JpxEntry>, busy: Boolean, error: Int,
    onBack: () -> Unit, onImport: () -> Unit, onOpen: (JpxEntry) -> Unit, onApply: (JpxEntry) -> Unit, onExport: (JpxEntry) -> Unit, onRemove: (JpxEntry) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.app_back)) }
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.jpx_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.jpx_scope, optionLabel(world)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
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
                    ConfigRow(entry, !busy, onOpen = { onOpen(entry) }, onApply={onApply(entry)}, onExport={onExport(entry)}, onRemove = { onRemove(entry) })
                }
            }
        }
    }
}

@Composable
private fun ConfigRow(entry: JpxEntry, enabled: Boolean, onOpen: () -> Unit, onApply: () -> Unit, onExport: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = if (entry.selected) colors.secondaryContainer else colors.surface) {
        Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable(enabled=enabled,onClick=onOpen).heightIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if(entry.selected) Icons.Filled.CheckCircle else Icons.Filled.Description,null)
                Column {
                    Text(stringResource(if(entry.changed) R.string.jpx_modified else if(entry.selected) R.string.jpx_applied else R.string.jpx_stored), style=MaterialTheme.typography.labelMedium)
                    Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.jpx_summary, entry.modes.split(", ").size, entry.rules), style = MaterialTheme.typography.bodySmall)
                }
            }
            Box {
                IconButton(enabled = enabled, onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.jpx_options, entry.name))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text={Text(stringResource(R.string.jpx_apply))},onClick={menu=false;onApply()})
                    DropdownMenuItem(text={Text(stringResource(R.string.jpx_export))},onClick={menu=false;onExport()})
                    DropdownMenuItem(text = { Text(stringResource(R.string.jpx_remove)) }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

@Composable
internal fun modeLabel(key: String): String {
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
