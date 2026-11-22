package com.mitahv.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.i18n.LocalMessageTranslator
import com.mitahv.mobile.i18n.messageText
import kotlinx.coroutines.launch

internal data class MobileChoice(
    val value: String,
    val label: String,
    val enabled: Boolean = true,
    val group: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileChoiceSheet(
    title: String,
    choices: List<MobileChoice>,
    selectedValue: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    searchable: Boolean = choices.size > 12,
) {
    val translator = LocalMessageTranslator.current
    val sheetTitle = messageText(title)
    var search by rememberSaveable(title) { mutableStateOf("") }
    var closing by remember { mutableStateOf(false) }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    fun closeWith(action: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            sheetState.hide()
            if (!sheetState.isVisible) action() else closing = false
        }
    }
    val filtered = choices.filter { choice ->
        !searchable || search.isBlank() || listOf(choice.label, choice.group,
            translator.translate(choice.label), translator.translate(choice.group))
            .any { it.contains(search.trim(), ignoreCase = true) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.semantics { paneTitle = sheetTitle },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(sheetTitle, Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = { closeWith(onDismiss) }, enabled = !closing) {
                    Icon(Icons.Filled.Close, stringResource(R.string.game_ui_close_menu))
                }
            }
            if (searchable) OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text(stringResource(R.string.game_ui_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).selectableGroup(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (filtered.isEmpty()) item {
                    Text(stringResource(R.string.game_ui_no_matches), Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                filtered.groupBy { it.group }.forEach { (group, options) ->
                    if (group.isNotBlank()) item {
                        Text(messageText(group), Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    itemsIndexed(options) { _, choice ->
                        val selected = choice.value == selectedValue
                        Surface(
                            selected = selected,
                            onClick = { closeWith { onDismiss(); onSelect(choice.value) } },
                            enabled = choice.enabled && !closing,
                            shape = MaterialTheme.shapes.large,
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = if (!choice.enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                else if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(messageText(choice.label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                if (selected) Icon(Icons.Filled.Check, null, Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
