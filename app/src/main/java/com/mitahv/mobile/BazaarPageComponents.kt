package com.mitahv.mobile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.i18n.messageText
import com.mitahv.mobile.model.*

@Composable
internal fun MonsterStatusContent(name: String, facts: List<Pair<String, String>>) {
    val values = facts.toMap()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        val metadata = listOfNotNull(values["Slot"]?.let { "#$it" },
            values["Level"]?.let { stringResource(R.string.game_ui_level, it) }, values["Species"]?.let { messageText(it) })
        Text(metadata.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("Hunger", "Morale").forEach { label ->
                val value = values[label] ?: return@forEach
                val percent = value.removeSuffix("%").toFloatOrNull() ?: return@forEach
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(messageText(label), style = MaterialTheme.typography.labelMedium)
                        Text(value, style = MaterialTheme.typography.labelMedium)
                    }
                    LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
internal fun BazaarPageNavigation(page: BazaarSectionsPage, access: PageInteraction, onAction: (NativeAction) -> Unit) {
    var menuOpen by remember(page.links) { mutableStateOf(false) }
    if (page.links.size > 6) {
        OutlinedButton(onClick = { menuOpen = true }, enabled = page.links.any { access.allows(it.action) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
            Text(messageText(page.links.firstOrNull { it.selected }?.label ?: page.links.first().label),
                Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ExpandMore, null)
        }
    } else {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            page.links.forEach { link ->
                FilterChip(selected = link.selected, enabled = access.allows(link.action),
                    onClick = { if (!link.selected) onAction(link.action) }, label = { Text(messageText(link.label)) })
            }
        }
    }
    if (menuOpen) {
        val links = page.links
        MobileChoiceSheet(
            title = page.route.label,
            choices = links.mapIndexed { i, link -> MobileChoice(i.toString(), link.label, access.allows(link.action)) },
            selectedValue = links.indexOfFirst { it.selected }.takeIf { it >= 0 }?.toString(),
            onSelect = { value -> links[value.toInt()].takeUnless { it.selected }?.let { onAction(it.action) } },
            onDismiss = { menuOpen = false },
        )
    }
}

/** Edit a low-frequency operation without moving the equipment or monster underneath it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BazaarOperationDialog(
    form: BazaarSectionsForm,
    busy: Boolean,
    responseRevision: Int,
    onDismiss: () -> Unit,
    onSubmit: (NativeAction) -> Unit,
) {
    val initialValues = form.fields.associate { it.key to if (it.kind == "checkbox" && !it.checked) "" else it.value }
    val draft = rememberSaveable(form.key, responseRevision, saver = BazaarDraftSaver) {
        BazaarDraft(form.key, "", initialValues)
    }
    val entered = draft.values.toMap()
    var statsOpen by remember { mutableStateOf(false) }
    val details = form.armoryCharm?.details(entered).orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(messageText(form.title)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BazaarFacts(form.facts)
                form.fields.forEach { field ->
                    BazaarField(field, entered[field.key].orEmpty(), !busy) { draft.values[field.key] = it }
                }
                BazaarFacts(form.equipmentSummary(entered))
                form.armoryRepair?.let { BazaarFacts(it.summary(entered)) }
                form.armoryStatFusion?.let { BazaarFacts(it.summary(entered)) }
                form.armoryCharm?.let { BazaarFacts(it.summary(entered)) }
                if (details.isNotEmpty()) TextButton(onClick = { statsOpen = true }) {
                    Icon(Icons.Default.Info, null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.game_ui_show_stats))
                }
                val errors = form.actions.map { form.validationError(it, entered) }
                if ((entered != initialValues || form.armoryRepair != null) && errors.isNotEmpty() && errors.all { it != null }) Text(messageText(errors.first().orEmpty()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                form.actions.forEach { action ->
                    Button(onClick = { onSubmit(form.submit(action, entered)) },
                        enabled = !busy && form.validationError(action, entered) == null) {
                        Text(messageText(form.armoryCharm?.label(entered) ?: action.label))
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_ui_cancel)) } },
    )
    if (statsOpen) BazaarDetailsDialog(form.title, details) { statsOpen = false }
}
