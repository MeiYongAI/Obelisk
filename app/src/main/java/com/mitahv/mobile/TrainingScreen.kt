package com.mitahv.mobile

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.i18n.gameText
import com.mitahv.mobile.i18n.messageText
import com.mitahv.mobile.model.CharacterSectionRow
import com.mitahv.mobile.model.CharacterSectionsPage
import com.mitahv.mobile.model.CharacterTrainingInfo
import com.mitahv.mobile.model.CharacterTrainingProgress
import com.mitahv.mobile.model.NativeAction
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun TrainingScreen(page: CharacterSectionsPage, busy: Boolean, responseRevision: Int,
    onAction: (NativeAction) -> Unit) {
    val rows = page.groups.flatMap { it.rows }
    var detailName by rememberSaveable { mutableStateOf<String?>(null) }
    val compactHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() < 480.dp }
    Column(Modifier.fillMaxSize()) {
        if (!compactHeight) page.trainingProgress?.let {
            TrainingProgressPanel(it, busy, responseRevision, onAction,
                Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(page.notices) { message ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
                    Text(messageText(message), Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            if (compactHeight) page.trainingProgress?.let { progress ->
                item(key = "progress") { TrainingProgressPanel(progress, busy, responseRevision, onAction) }
            }
            if (page.summary.isNotEmpty()) item(key = "balance") {
                Text(messageText(page.summary.joinToString(" · ")),
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(rows, key = { it.title }) { row ->
                row.training?.let { info ->
                    TrainingRow(row, info, page.trainingProgress?.name == row.title, busy,
                        onDetails = { detailName = row.title }, onAction = onAction)
                }
            }
        }
    }
    rows.firstOrNull { it.title == detailName }?.let { row ->
        row.training?.let { info ->
            TrainingDetails(row, info, page.trainingProgress?.name == row.title, busy,
                onDismiss = { detailName = null }, onAction = { detailName = null; onAction(it) })
        }
    }
}

@Composable
private fun TrainingRow(row: CharacterSectionRow, info: CharacterTrainingInfo, active: Boolean,
    busy: Boolean, onDetails: () -> Unit, onAction: (NativeAction) -> Unit) {
    Surface(onClick = onDetails, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(gameText(row.title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TrainingLevel(row, info)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (info.atCurrentCap) {
                TrainingEffect(info.effect)
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (info.levelCap == 0) Icons.Default.Block else Icons.Default.CheckCircle,
                        null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(info.capLabel), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TrainingEffect(info.effect)
                    TrainingDuration(info.duration)
                }
                if (active) Text(stringResource(R.string.training_ui_in_progress), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                else TrainingStartButton(row, info, busy, onAction)
            }
        }
    }
}

private val CharacterTrainingInfo.atCurrentCap: Boolean
    get() = currentLevel != null && levelCap != null && currentLevel >= levelCap

private val CharacterTrainingInfo.capLabel: Int
    get() = if (levelCap == 0) R.string.training_ui_unavailable else R.string.training_ui_at_cap

@Composable
private fun TrainingEffect(effect: String) {
    // JPX appends explanatory notes in full-width parentheses; keep them in the dialog.
    Text(gameText(effect).substringBefore('（').trimEnd(), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun TrainingLevel(row: CharacterSectionRow, info: CharacterTrainingInfo) {
    val description = stringResource(R.string.training_ui_level, row.value)
    Text(row.value, Modifier.semantics { contentDescription = description },
        style = MaterialTheme.typography.labelLarge,
        color = if (info.atCurrentCap) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
}

@Composable
private fun TrainingDuration(duration: String?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (duration != null) {
            Icon(Icons.Default.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(gameText(duration), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrainingStartButton(row: CharacterSectionRow, info: CharacterTrainingInfo, busy: Boolean,
    onAction: (NativeAction) -> Unit) {
    val action = row.actions.firstOrNull()
    val cost = info.cost?.let { "$it C" } ?: stringResource(R.string.training_ui_start)
    val description = stringResource(R.string.training_ui_start_cost, gameText(row.title), cost)
    FilledTonalButton(onClick = { action?.let(onAction) }, enabled = !busy && action != null,
        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = description },
        contentPadding = PaddingValues(horizontal = 12.dp)) {
        Icon(Icons.Default.ArrowUpward, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(cost)
    }
}

@Composable
private fun TrainingDetails(row: CharacterSectionRow, info: CharacterTrainingInfo, active: Boolean,
    busy: Boolean, onDismiss: () -> Unit, onAction: (NativeAction) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(gameText(row.title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                TrainingLevel(row, info)
                TrainingDuration(info.duration)
            }
            Text(gameText(info.effect), style = MaterialTheme.typography.bodyLarge)
            if (active || info.atCurrentCap) Text(stringResource(if (active) R.string.training_ui_in_progress else info.capLabel),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }, confirmButton = {
        if (!info.atCurrentCap && !active) TrainingStartButton(row, info, busy, onAction)
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_ui_close)) } })
}

@Composable
private fun TrainingProgressPanel(progress: CharacterTrainingProgress, busy: Boolean, responseRevision: Int,
    onAction: (NativeAction) -> Unit, modifier: Modifier = Modifier) {
    val sampledAt = rememberSaveable(progress.endsAtServer, responseRevision) { SystemClock.elapsedRealtime() }
    var elapsed by remember(progress, responseRevision) { mutableLongStateOf(0L) }
    var refreshed by rememberSaveable(progress.endsAtServer) { mutableStateOf(false) }
    val percent = progress.percentAfter(elapsed)
    val remaining = progress.remainingSecondsAfter(elapsed)
    val currentOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(progress, responseRevision) {
        do {
            elapsed = SystemClock.elapsedRealtime() - sampledAt
            if (progress.percentAfter(elapsed) >= 100.0 || progress.totalSeconds == null) break
            delay(1000)
        } while (true)
    }
    LaunchedEffect(percent >= 100.0, busy, refreshed) {
        if (percent >= 100.0 && !busy && !refreshed) progress.refreshAction?.let {
            refreshed = true
            currentOnAction(it)
        }
    }
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(gameText(progress.name), style = MaterialTheme.typography.titleMedium)
                    val time = remaining?.let { String.format(Locale.ROOT, "%d:%02d:%02d", it / 3600, it / 60 % 60, it % 60) }
                    Text(if (time != null) stringResource(R.string.training_ui_remaining, time)
                        else stringResource(R.string.training_ui_in_progress), style = MaterialTheme.typography.labelLarge)
                }
                Text("${percent.toInt()}%", style = MaterialTheme.typography.labelLarge)
                progress.cancelAction?.let { action ->
                    IconButton(onClick = { onAction(action) }, enabled = !busy && percent < 100.0) {
                        Icon(Icons.Default.Close, stringResource(R.string.training_ui_cancel), Modifier.size(20.dp))
                    }
                }
            }
            LinearProgressIndicator(progress = { (percent / 100.0).toFloat() }, modifier = Modifier.fillMaxWidth().padding(end = 8.dp))
        }
    }
}
