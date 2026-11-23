package com.mitahv.mobile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.data.JpxDefaults
import com.mitahv.mobile.data.JpxDraft
import com.mitahv.mobile.data.JpxEdition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
internal fun JpxDefaultsDialog(world: String, onPreview: (JpxDraft) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var edition by rememberSaveable { mutableStateOf(JpxEdition.Original) }
    var defaults by remember(edition, world) { mutableStateOf<JpxDefaults?>(null) }
    var feedback by remember { mutableIntStateOf(0) }
    LaunchedEffect(edition, world) {
        feedback = 0
        try { defaults = withContext(Dispatchers.IO) {
            context.assets.open(edition.asset("battle")).use { JpxDefaults(edition, it, world) }
        } } catch (e: IOException) { feedback = R.string.jpx_io }
    }
    var exportAsset by rememberSaveable { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val asset = exportAsset
        exportAsset = null
        if (uri != null && asset != null) scope.launch {
            feedback = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out -> context.assets.open(asset).use { it.copyTo(out) } } ?: throw IOException()
                }
                R.string.jpx_exported
            } catch (e: CancellationException) { throw e } catch (_: IOException) { R.string.jpx_io } catch (_: SecurityException) { R.string.jpx_io }
        }
    }
    fun export(section: String) {
        exportAsset = edition.asset(section)
        exporter.launch("${edition.directory}-$section.json")
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.jpx_defaults)) }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (option in JpxEdition.entries) FilterChip(selected = option == edition, onClick = { edition = option }, label = { Text(option.label) })
            defaults?.let { data ->
                HorizontalDivider()
                Text(stringResource(R.string.jpx_summary, data.full.modes.size, data.full.rules), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.jpx_defaults_hint), style = MaterialTheme.typography.bodySmall)
                for ((key, count) in data.ruleCounts) {
                    TextButton(onClick = { onPreview(data.mode(key)) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(defaultModeLabel(key))
                            Text(key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(stringResource(R.string.jpx_rule_count, count))
                    }
                }
                HorizontalDivider()
                Text(stringResource(R.string.jpx_export_defaults), style = MaterialTheme.typography.titleSmall)
                for ((section, label) in listOf("battle" to R.string.jpx_export_battle, "stats" to R.string.jpx_export_stats, "shortcuts" to R.string.jpx_export_shortcuts)) {
                    TextButton(onClick = { export(section) }) { Text(stringResource(label)) }
                }
            }
            if (feedback != 0) Text(stringResource(feedback), color = if (feedback == R.string.jpx_io) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
    }, confirmButton = { TextButton(enabled = defaults != null, onClick = { defaults?.let { onPreview(it.full) } }) { Text(stringResource(R.string.jpx_whole_preset)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.app_close)) } })
}

@Composable
private fun defaultModeLabel(key: String): String {
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
        "Tower" -> R.string.jpx_scene_tower
        "General" -> R.string.jpx_scene_general
        else -> return key
    }
    return stringResource(R.string.jpx_mode_label, style, stringResource(scene))
}
