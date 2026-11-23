package com.mitahv.mobile

import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.google.gson.JsonParseException
import com.mitahv.mobile.data.*
import com.mitahv.mobile.model.BattleScene
import com.mitahv.mobile.model.BattleStyle
import kotlinx.coroutines.*
import java.io.IOException

@Composable
internal fun BattleConfigsScreen(library: JpxLibrary, strategy: BattleStrategy, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val resolver = context.contentResolver
    fun notify(message: Int) = Toast.makeText(context,message,Toast.LENGTH_SHORT).show()
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    var document by remember { mutableStateOf<JpxDocument?>(null) }
    var applyId by rememberSaveable { mutableStateOf<Long?>(null) }
    var exportId by rememberSaveable { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val catalog = strategy.catalog
    val entries = catalog?.entries.orEmpty()
    var configuringRoutes by rememberSaveable { mutableStateOf(false) }
    var draft by remember { mutableStateOf<JpxDraft?>(null) }
    var deleting by remember { mutableStateOf<JpxEntry?>(null) }
    var renaming by remember { mutableStateOf<JpxEntry?>(null) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableIntStateOf(0) }
    suspend fun perform(block: suspend () -> Unit) {
        busy = true
        error = 0
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (_: OversizedConfig) { error = R.string.jpx_large }
        catch (_: EmptyConfig) { error = R.string.jpx_no_importable }
        catch (_: WrongConfigWorld) { error = R.string.jpx_wrong_world }
        catch (_: JsonParseException) { error = R.string.jpx_format }
        catch (_: IllegalArgumentException) { error = R.string.jpx_format }
        catch (_: Exception) { error = R.string.jpx_io }
        finally { busy = false }
    }
    fun change(block: () -> Unit) { scope.launch { perform { withContext(Dispatchers.IO) { block() } } } }
    LaunchedEffect(library, openId) { perform {
        val id = openId
        document = if (id == null) null else withContext(Dispatchers.IO) { JpxDocument(library.load(id)) }
    } }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val id = exportId
        exportId = null
        if (id != null && uri != null) scope.launch { perform {
            withContext(Dispatchers.IO) {
                val text = library.load(id).source
                resolver.openOutputStream(uri,"wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) } ?: throw IOException()
            }; notify(R.string.jpx_export_ok)
        } }
    }
    fun export(entry: JpxEntry) {
        exportId = entry.id
        val suffix = entry.nickname.replace(Regex("[^\\p{L}\\p{N}_. -]"), "_").let { if (it.isEmpty()) "" else "_$it" }
        exporter.launch("${entry.mode}$suffix.json")
    }
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
    fun variant(entry: JpxEntry) = catalog?.variant(entry).orEmpty()
    if (configuringRoutes && catalog != null) {
        StrategyRoutesScreen(catalog, strategy.style, library.world, busy, error, onBack = { configuringRoutes = false }) { style, scene, id ->
            change { library.assign(style, scene, id) }
        }
        return
    }
    val entry = entries.find { it.id == openId }; val doc = document
    if (entry != null && doc != null) JpxConfigDetails(doc, variant(entry), busy, error,
        onBack={ document=null; openId=null }, onApply={ applyId=entry.id }) { group, edit, done ->
        scope.launch { perform {
            document = withContext(Dispatchers.IO) {
                val edited = doc.update(entry.mode,group,library.world,edit)
                library.update(entry.id,edited); JpxDocument(edited)
            }; done()
        } }
    } else if (openId != null) {
        BackHandler { openId = null }
        Column(Modifier.safeDrawingPadding()) {
            IconButton(onClick = { openId = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.app_back)) }
            val failure = if (error != 0) error else if (catalog?.readFailed == true || (catalog != null && entry == null)) R.string.jpx_io else 0
            if (failure != 0) Text(stringResource(failure)) else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    } else ConfigLibraryContent(
        strategy = strategy, onRoutes = { configuringRoutes = true },
        onAutomatic = { enabled -> change { library.setAutomatic(enabled, strategy.match?.entry?.id) } },
        world = library.world, entries = entries, busy = busy || catalog == null, error = if (catalog?.readFailed == true) R.string.jpx_io else error, onBack = onBack, listState = listState, variant = ::variant,
        onImport = { picker.launch(arrayOf("*/*")) }, onOpen = { document=null; openId=it.id }, onApply = { applyId=it.id },
        onExport=::export, onRename={ renaming=it }, onRemove = { deleting = it },
    )
    val applying = entries.find { it.id == applyId }
    if (applying != null) AlertDialog(onDismissRequest={applyId=null},title={Text(stringResource(R.string.jpx_apply))},
        text={ Text(stringResource(R.string.jpx_apply_body, profileLabel(applying, variant(applying)), optionLabel(library.world))) },
        confirmButton={TextButton(enabled=!busy,onClick={applyId=null;scope.launch { perform { withContext(Dispatchers.IO) { library.apply(applying.id) };notify(R.string.jpx_application_ok) } }}){Text(stringResource(R.string.jpx_apply))}},
        dismissButton={TextButton(onClick={applyId=null}){Text(stringResource(R.string.game_ui_cancel))}})
    draft?.let { candidate -> AlertDialog(onDismissRequest = { draft = null }, title = { Text(stringResource(R.string.jpx_preview)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(candidate.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.jpx_summary, candidate.modes.size, candidate.rules))
            HorizontalDivider()
            for (mode in candidate.modes) Text(modeLabel(mode), style = MaterialTheme.typography.bodyMedium)
        } }, confirmButton = { TextButton(onClick = {
            draft = null
            scope.launch { perform {
                val added = withContext(Dispatchers.IO) { library.save(candidate) }
                listState.scrollToItem(0)
                val message = if (added == 0) resources.getString(R.string.jpx_already_imported) else resources.getQuantityString(R.plurals.jpx_imported, added, added)
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            } }
        }) { Text(stringResource(R.string.jpx_library_save)) } },
        dismissButton = { TextButton(onClick = { draft = null }) { Text(stringResource(R.string.game_ui_cancel)) } }) }
    renaming?.let { target ->
        var text by rememberSaveable(target.id) { mutableStateOf(target.nickname) }
        AlertDialog(onDismissRequest={renaming=null}, title={Text(modeLabel(target.mode))},
            text={ OutlinedTextField(value=text, onValueChange={text=it.take(80)}, singleLine=true, label={Text(stringResource(R.string.jpx_nickname))}) },
            confirmButton={ TextButton(onClick={renaming=null; change { library.rename(target.id,text) }}) { Text(stringResource(R.string.jpx_save_edit)) } },
            dismissButton={ TextButton(onClick={renaming=null}) { Text(stringResource(R.string.game_ui_cancel)) } })
    }
    deleting?.let { entry -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.jpx_remove)) },
        text = { Text(stringResource(R.string.jpx_confirm_remove, profileLabel(entry, variant(entry)))) },
        confirmButton = { TextButton(onClick = { deleting = null; change { library.remove(entry.id) } }) { Text(stringResource(R.string.jpx_remove)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.game_ui_cancel)) } }) }
}

@Composable
private fun ConfigLibraryContent(
    strategy: BattleStrategy, onRoutes: () -> Unit, onAutomatic: (Boolean) -> Unit,
    world: String, entries: List<JpxEntry>, busy: Boolean, error: Int,
    listState: LazyListState, variant: (JpxEntry) -> String, onRename: (JpxEntry) -> Unit,
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
                state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) {
                if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (error != 0) item {
                    Text(stringResource(error), Modifier.padding(vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }, color = colors.error)
                }
                item {
                    StrategySummary(strategy)
                    StrategyControls(automatic = strategy.catalog?.manual != true, enabled = !busy && strategy.catalog?.readFailed == false,
                        onAutomatic = onAutomatic, onRoutes = onRoutes)
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
                    ConfigRow(entry, variant(entry), strategy.detected(entry), strategy.match?.entry?.id == entry.id, !busy, onRename = { onRename(entry) }, onOpen = { onOpen(entry) }, onApply={onApply(entry)}, onExport={onExport(entry)}, onRemove = { onRemove(entry) })
                }
            }
        }
    }
}

@Composable
private fun ConfigRow(entry: JpxEntry, variant: String, detected: Boolean, active: Boolean, enabled: Boolean, onRename: () -> Unit, onOpen: () -> Unit, onApply: () -> Unit, onExport: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = if (active) colors.secondaryContainer else colors.surface) {
        Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable(enabled=enabled,onClick=onOpen).heightIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if(active) Icons.Filled.CheckCircle else Icons.Filled.Description,null)
                Column {
                    Text(modeLabel(entry.mode), style = MaterialTheme.typography.titleMedium)
                    if (variant.isNotBlank()) Text(variant, style=MaterialTheme.typography.bodySmall, maxLines=1, overflow=TextOverflow.Ellipsis)
                    val status = listOfNotNull(stringResource(R.string.jpx_rows, entry.rules),
                        if (detected) stringResource(R.string.jpx_detected_badge) else null,
                        if (active) stringResource(R.string.jpx_applied) else null,
                        if (entry.changed) stringResource(R.string.jpx_modified) else null)
                    Text(status.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
            }
            Box {
                IconButton(enabled = enabled, onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.jpx_options, profileLabel(entry, variant)))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text={Text(stringResource(R.string.jpx_apply))},onClick={menu=false;onApply()})
                    DropdownMenuItem(text={Text(stringResource(R.string.jpx_nickname))},onClick={menu=false;onRename()})
                    DropdownMenuItem(text={Text(stringResource(R.string.jpx_export))},onClick={menu=false;onExport()})
                    DropdownMenuItem(text = { Text(stringResource(R.string.jpx_remove)) }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

@Composable
internal fun profileLabel(entry: JpxEntry, variant: String): String =
    if (variant.isBlank()) modeLabel(entry.mode) else "${modeLabel(entry.mode)} · $variant"

@Composable
internal fun modeLabel(key: String): String {
    val style = BattleStyle.entries.find { it.key == key.substringBeforeLast('_') } ?: return key
    val scene = BattleScene.entries.find { it.name == key.substringAfterLast('_') } ?: return key
    return stringResource(R.string.jpx_mode_label, styleLabel(style), stringResource(scene.label))
}
