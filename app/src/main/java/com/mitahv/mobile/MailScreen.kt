package com.mitahv.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.i18n.messageText
import com.mitahv.mobile.model.*
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun MailScreen(page: BazaarSectionsPage, busy: Boolean, responseRevision: Int,
    onAction: (NativeAction) -> Unit, onBack: (NativeAction) -> Unit, onDraftChanged: (Boolean) -> Unit) {
    val mail = page.mail ?: return
    val folders = mail.folders.filter { it.action.url.queryParameter("filter") in setOf("inbox", "read", "sent") }
    val form = page.forms.firstOrNull { it.validation == "mail" }
    val initialValues = remember(form) { form?.fields?.associate { it.key to it.value }.orEmpty() }
    val snapshot = remember(page, initialValues) { bazaarDraftSnapshot(page, initialValues) }
    var savedDraft by rememberSaveable(snapshot, stateSaver = BazaarDraftSaver) {
        mutableStateOf(BazaarDraft(snapshot, "", initialValues))
    }
    val draft = if (savedDraft.snapshot == snapshot) savedDraft else remember(snapshot) { BazaarDraft(snapshot, "", initialValues) }
    SideEffect { if (savedDraft !== draft) savedDraft = draft }
    val values = draft.values.toMap()
    val dirty = mail.mode == MailViewMode.Compose && values != initialValues
    val draftCallback by rememberUpdatedState(onDraftChanged)
    LaunchedEffect(dirty, responseRevision) { draftCallback(dirty) }
    DisposableEffect(Unit) { onDispose { draftCallback(false) } }
    var attachmentsOpen by remember { mutableStateOf(false) }
    var codOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    val attached = page.sections.firstOrNull { it.title == "附件" }?.entries.orEmpty()
    val send = form?.actions?.firstOrNull { it.mailOperation == "send" }
    val codEntry = form?.attachments?.firstOrNull { it.key == "mail-cod" }
    val claimAll = page.actions.firstOrNull { it.mailOperation == "attach_remove" && it.fields.toMap()["action_value"] == "0" }
    val otherActions = if (mail.mode == MailViewMode.Compose) form?.actions.orEmpty().filterNot { it == send }
        else page.actions.filterNot { it.mailOperation == "attach_remove" }
    val removeAll = form?.attachments?.firstOrNull {
        it.action?.mailOperation == "attach_remove" && it.action.fields.toMap()["action_value"] == "0"
    }?.action
    val listStates = rememberSaveableStateHolder()
    fun submit(action: NativeAction) { if (!busy) onAction(if (form != null) form.submit(action, values) else action) }
    BackHandler(enabled = mail.mode != MailViewMode.List && mail.listAction != null) {
        if (!busy) onBack(mail.listAction!!)
    }

    Column(Modifier.fillMaxSize()) {
        if (mail.mode == MailViewMode.List) {
            if (folders.isNotEmpty()) PrimaryTabRow(selectedTabIndex = folders.indexOfFirst {
                it.action.url.queryParameter("filter") == mail.folder
            }.coerceAtLeast(0)) {
                folders.forEach { folder ->
                    val selected = folder.action.url.queryParameter("filter") == mail.folder
                    Tab(selected = selected, enabled = !busy, onClick = { if (!selected) onAction(folder.action) },
                        text = { Text(mailFolderLabel(folder.action.url.queryParameter("filter").orEmpty())) })
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                listStates.SaveableStateProvider("${mail.folder}:${mail.rows.firstOrNull()?.action?.url}") {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = if (mail.composeAction != null) 96.dp else 16.dp)) {
                        if (page.messages.isNotEmpty()) item { MailNotices(page.messages) }
                        if (mail.rows.isEmpty()) item {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 64.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.mail_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(mail.rows, key = { it.id }) { row ->
                            MailListItem(row, mail.folder == "sent", busy) { onAction(row.action) }
                        }
                        if (mail.previousPage != null || mail.nextPage != null) item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = { mail.previousPage?.let(onAction) }, enabled = !busy && mail.previousPage != null) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null)
                                    Text(stringResource(R.string.mail_previous))
                                }
                                TextButton(onClick = { mail.nextPage?.let(onAction) }, enabled = !busy && mail.nextPage != null) {
                                    Text(stringResource(R.string.mail_next))
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                }
                            }
                        }
                    }
                }
                mail.composeAction?.let { action ->
                    ExtendedFloatingActionButton(onClick = { if (!busy) onAction(action) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        icon = { Icon(Icons.Default.Edit, null) }, text = { Text(stringResource(R.string.mail_compose)) })
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { mail.listAction?.let(onBack) }, enabled = !busy && mail.listAction != null) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.app_back))
                }
                if (mail.mode == MailViewMode.Compose) Text(stringResource(R.string.mail_compose), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge)
                else Spacer(Modifier.weight(1f))
                if (otherActions.isNotEmpty() || removeAll != null) Box {
                    IconButton(onClick = { moreOpen = true }, enabled = !busy) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.game_ui_more_actions))
                    }
                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                        (otherActions + listOfNotNull(removeAll)).forEach { action ->
                            DropdownMenuItem(text = { Text(messageText(action.label)) },
                                onClick = { moreOpen = false; submit(action) },
                                enabled = !busy && (form == null || form.validationError(action, values) == null))
                        }
                    }
                }
            }
            listStates.SaveableStateProvider("${mail.mode}:${mail.listAction?.url}:${mail.detail?.subject}") {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (page.messages.isNotEmpty()) item { MailNotices(page.messages) }
                    if (mail.mode == MailViewMode.Compose && form != null) {
                        items(form.fields, key = { it.key }) { field ->
                            MailComposeField(field, values[field.key].orEmpty(), busy) { draft.values[field.key] = it }
                        }
                    } else mail.detail?.let { detail ->
                        item {
                            Text(detail.subject.ifBlank { stringResource(R.string.mail_no_subject) },
                                Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (detail.from.isNotBlank()) MailAddress(stringResource(R.string.mail_from), detail.from)
                                if (detail.to.isNotBlank()) MailAddress(stringResource(R.string.mail_to), detail.to)
                            }
                        }
                        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
                        if (detail.body.isNotBlank()) item {
                            SelectionContainer { Text(detail.body, Modifier.fillMaxWidth().padding(vertical = 8.dp), style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                    if (attached.isNotEmpty()) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.game_ui_attachments), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                                Text(if (mail.attachmentLimit != null) "${mail.attachmentCount ?: attached.size} / ${mail.attachmentLimit}"
                                    else (mail.attachmentCount ?: attached.size).toString(),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(attached, key = { it.key }) { entry ->
                            MailAttachmentRow(entry, busy, mail.mode == MailViewMode.Compose, ::submit)
                        }
                    }
                    if (codEntry != null || (mail.cod ?: 0) > 0) item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.mail_cod_amount, mailNumber(mail.cod ?: 0)), Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (codEntry != null) TextButton(onClick = { codOpen = true }, enabled = !busy) {
                                Text(stringResource(R.string.mail_edit))
                            }
                        }
                    }
                }
            }
            if (mail.mode == MailViewMode.Compose && form != null) Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (form.attachments.any { it.quantity?.action?.mailOperation == "attach_add" }) IconButton(
                        onClick = { attachmentsOpen = true }, enabled = !busy) {
                        Icon(Icons.Default.AttachFile, stringResource(R.string.mail_add_attachment))
                    }
                    if (mail.postage != null) Text(stringResource(R.string.mail_postage, mailNumber(mail.postage)),
                        Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Spacer(Modifier.weight(1f))
                    send?.let { action -> Button(onClick = { submit(action) }, enabled = !busy && form.validationError(action, values) == null,
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.mail_send))
                    } }
                }
            } else if (claimAll != null || mail.replyAction != null) Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    mail.replyAction?.let { action ->
                        OutlinedButton(onClick = { onAction(action) }, enabled = !busy,
                            modifier = if (claimAll == null) Modifier.weight(1f).heightIn(min = 48.dp) else Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Reply, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.mail_reply))
                        }
                    }
                    claimAll?.let { action -> Button(onClick = { onAction(action) }, enabled = !busy,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(if ((mail.cod ?: 0) > 0) stringResource(R.string.mail_pay_and_claim, mailNumber(mail.cod!!))
                            else stringResource(R.string.mail_claim_all))
                    } }
                }
            }
        }
    }
    if (attachmentsOpen && form != null) MailAttachmentPicker(form, busy, onDismiss = { attachmentsOpen = false },
        onAction = { attachmentsOpen = false; submit(it) })
    if (codOpen && codEntry != null) MailAttachmentAmountDialog(codEntry, busy, onDismiss = { codOpen = false },
        onAction = { codOpen = false; submit(it) })
}

private val NativeAction.mailOperation: String? get() = fields.firstOrNull { it.first == "action" }?.second
private fun mailNumber(value: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(value)

@Composable
private fun mailFolderLabel(folder: String): String = stringResource(when (folder) {
    "read" -> R.string.mail_folder_read
    "sent" -> R.string.mail_folder_sent
    else -> R.string.mail_folder_inbox
})

@Composable
private fun MailNotices(messages: List<String>) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        messages.forEach { message -> Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
            Text(messageText(message), Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
        } }
    }
}

@Composable
private fun MailListItem(row: MailListRow, sent: Boolean, busy: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = !busy, color = MaterialTheme.colorScheme.surface) {
        Column {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (row.unread && !sent) {
                        val unread = stringResource(R.string.mail_unread)
                        Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape).semantics { contentDescription = unread })
                    }
                    Text(row.correspondent, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall, fontWeight = if (row.unread) FontWeight.SemiBold else FontWeight.Normal)
                    Text(row.sentAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(row.subject.ifBlank { stringResource(R.string.mail_no_subject) }, Modifier.weight(1f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    if (sent) Text(stringResource(if (row.unread) R.string.mail_unread else R.string.mail_read),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { if (!row.unread) contentDescription = row.readAt })
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun MailAddress(label: String, address: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(address, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun MailComposeField(field: BazaarSectionsField, value: String, busy: Boolean, onChange: (String) -> Unit) {
    val body = field.name == "message_body"
    val label = when (field.name) {
        "message_to_name" -> stringResource(R.string.mail_to)
        "message_subject" -> stringResource(R.string.mail_subject)
        "message_body" -> stringResource(R.string.mail_body)
        else -> messageText(field.label)
    }
    OutlinedTextField(value, { if (field.maxLength == null || it.length <= field.maxLength) onChange(it) },
        Modifier.fillMaxWidth(), label = { Text(label) }, enabled = !busy,
        singleLine = !body, minLines = if (body) 8 else 1,
        keyboardOptions = KeyboardOptions(capitalization = if (body) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            imeAction = if (body) ImeAction.Default else ImeAction.Next))
}
