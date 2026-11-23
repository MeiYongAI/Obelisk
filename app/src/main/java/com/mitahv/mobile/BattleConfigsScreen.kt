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
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.app_back)) }
            Text(stringResource(R.string.jpx_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(Modifier.widthIn(max = 720.dp).align(Alignment.CenterHorizontally).fillMaxWidth(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.jpx_scope, library.world), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.jpx_hint), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy) { Text(stringResource(R.string.jpx_import)) }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (error != 0) item { Text(stringResource(error), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error) }
            if (!busy && error == 0 && entries.isEmpty()) item { Text(stringResource(R.string.jpx_empty)) }
            items(entries, key = { it.id }) { entry ->
                Surface(shape = MaterialTheme.shapes.large, color = if (entry.selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (entry.selected) Text(stringResource(R.string.jpx_current), style = MaterialTheme.typography.labelLarge)
                        Text(entry.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.jpx_summary, entry.modes.split(", ").size, entry.rules), style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!entry.selected) TextButton(onClick = { change { library.select(entry.id) } }, enabled = !busy) { Text(stringResource(R.string.jpx_select)) }
                            TextButton(onClick = { deleting = entry }, enabled = !busy) { Text(stringResource(R.string.jpx_remove)) }
                        }
                    }
                }
            }
        }
    }
    draft?.let { candidate -> AlertDialog(onDismissRequest = { draft = null }, title = { Text(stringResource(R.string.jpx_preview)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(candidate.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.jpx_summary, candidate.modes.size, candidate.rules))
            Text(candidate.modes.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton(onClick = { draft = null; change { library.save(candidate) } }) { Text(stringResource(R.string.jpx_save)) } },
        dismissButton = { TextButton(onClick = { draft = null }) { Text(stringResource(R.string.game_ui_cancel)) } }) }
    deleting?.let { entry -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.jpx_remove)) },
        text = { Text(stringResource(R.string.jpx_confirm_remove, entry.name)) },
        confirmButton = { TextButton(onClick = { deleting = null; change { library.remove(entry.id) } }) { Text(stringResource(R.string.jpx_remove)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.game_ui_cancel)) } }) }
}
