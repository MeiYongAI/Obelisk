package com.mitahv.mobile

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.model.AppThemeMode
import com.mitahv.mobile.data.JpxLibrary
import androidx.compose.material.icons.filled.FolderOpen
import com.mitahv.mobile.model.AppLanguage
import com.mitahv.mobile.i18n.messageText

@Composable
internal fun AppSettingsScreen(
    configLibrary: JpxLibrary?,
    siteIsAlt: Boolean,
    onSiteChanged: (Boolean) -> Unit,
    themeMode: AppThemeMode,
    onThemeChanged: (AppThemeMode) -> Unit,
    language: AppLanguage,
    onLanguageChanged: (AppLanguage) -> Unit,
    dynamicColorEnabled: Boolean,
    onDynamicColorChanged: (Boolean) -> Unit,
    memberId: String,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    busy: Boolean = false,
    error: String? = null,
    onClearError: () -> Unit = {},
) {
    var showConfigs by rememberSaveable { mutableStateOf(false) }
    if (showConfigs && configLibrary != null) {
        key(configLibrary) { BattleConfigsScreen(configLibrary) { showConfigs = false } }
        return
    }
    var sitePickerOpen by rememberSaveable { mutableStateOf(false) }
    var themePickerOpen by rememberSaveable { mutableStateOf(false) }
    var languagePickerOpen by rememberSaveable { mutableStateOf(false) }
    var signOutOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        topBar = {
            Surface {
                Row(
                    Modifier.fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.app_back)) }
                    Text(stringResource(R.string.app_settings), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (busy) item("progress") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (error != null) item("error") {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(messageText(error), Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                            IconButton(onClick = onClearError) { Icon(Icons.Filled.Close, stringResource(R.string.app_dismiss_error)) }
                        }
                    }
                }
                item("site") {
                    AppSettingCard {
                        AppSettingChoiceRow(stringResource(R.string.app_site), if (siteIsAlt) "alt.hentaiverse.org" else "hentaiverse.org",
                            Icons.Filled.Public, !busy) { sitePickerOpen = true }
                    }
                }
                item("battle-configs") {
                    AppSettingCard {
                        AppSettingChoiceRow(stringResource(R.string.jpx_title),
                            stringResource(if (configLibrary == null) R.string.jpx_sign_in else R.string.jpx_manage),
                            Icons.Filled.FolderOpen, configLibrary != null && !busy) { showConfigs = true }
                    }
                }
                item("appearance-heading") { AppSettingsHeading(stringResource(R.string.app_appearance)) }
                item("appearance") {
                    AppSettingCard {
                        AppSettingChoiceRow(stringResource(R.string.app_language), language.title, Icons.Filled.Language, !busy) { languagePickerOpen = true }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        AppSettingChoiceRow(stringResource(R.string.app_theme), themeMode.title, Icons.Filled.BrightnessAuto, !busy) { themePickerOpen = true }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        val available = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 72.dp)
                                .toggleable(value = dynamicColorEnabled, enabled = !busy && available,
                                    role = Role.Switch, onValueChange = onDynamicColorChanged)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(Icons.Filled.Palette, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.app_dynamic_color), style = MaterialTheme.typography.bodyLarge)
                                if (!available) Text(stringResource(R.string.app_android_12), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = dynamicColorEnabled, onCheckedChange = null, enabled = !busy && available)
                        }
                    }
                }
                item("session") {
                    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Person, null)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.app_current_account), style = MaterialTheme.typography.labelMedium)
                            Text(memberId, style = MaterialTheme.typography.bodyLarge)
                        }
                        TextButton(onClick = { signOutOpen = true }, enabled = !busy) { Text(stringResource(R.string.app_logout)) }
                    }
                }
            }
        }
    }

    if (sitePickerOpen) AppSettingsChoiceDialog(
        title = stringResource(R.string.app_site),
        choices = listOf(AppSettingsChoice(stringResource(R.string.app_main_site), "hentaiverse.org"), AppSettingsChoice(stringResource(R.string.app_alt_site), "alt.hentaiverse.org")),
        selected = if (siteIsAlt) 1 else 0,
        busy = busy,
        onDismiss = { sitePickerOpen = false },
        onSelect = { index -> sitePickerOpen = false; if ((index == 1) != siteIsAlt) onSiteChanged(index == 1) },
    )
    if (themePickerOpen) AppSettingsChoiceDialog(
        title = stringResource(R.string.app_theme),
        choices = AppThemeMode.entries.map { AppSettingsChoice(it.title) },
        selected = AppThemeMode.entries.indexOf(themeMode),
        busy = busy,
        onDismiss = { themePickerOpen = false },
        onSelect = { index -> themePickerOpen = false; if (AppThemeMode.entries[index] != themeMode) onThemeChanged(AppThemeMode.entries[index]) },
    )
    if (languagePickerOpen) AppSettingsChoiceDialog(
        title = stringResource(R.string.app_language),
        choices = AppLanguage.entries.map { AppSettingsChoice(it.title) },
        selected = AppLanguage.entries.indexOf(language),
        busy = busy,
        onDismiss = { languagePickerOpen = false },
        onSelect = { index ->
            languagePickerOpen = false
            if (AppLanguage.entries[index] != language) onLanguageChanged(AppLanguage.entries[index])
        },
    )
    if (signOutOpen) AlertDialog(
        onDismissRequest = { signOutOpen = false },
        title = { Text(stringResource(R.string.app_sign_out_question)) },
        text = { Text(memberId) },
        confirmButton = { TextButton(onClick = { signOutOpen = false; onSignOut() }, enabled = !busy) { Text(stringResource(R.string.app_sign_out)) } },
        dismissButton = { TextButton(onClick = { signOutOpen = false }) { Text(stringResource(R.string.app_cancel)) } },
    )
}

@Composable
private fun AppSettingsHeading(title: String) {
    Text(title, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun AppSettingCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun AppSettingChoiceRow(title: String, value: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class AppSettingsChoice(val title: String, val detail: String = "")

@Composable
private fun AppSettingsChoiceDialog(
    title: String,
    choices: List<AppSettingsChoice>,
    selected: Int,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).selectableGroup()) {
                items(choices.size) { index ->
                    val choice = choices[index]
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                        .selectable(selected = selected == index, enabled = !busy, role = Role.RadioButton,
                            onClick = { onSelect(index) }).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = selected == index, onClick = null, enabled = !busy)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(choice.title, style = MaterialTheme.typography.bodyLarge)
                            if (choice.detail.isNotBlank()) Text(choice.detail, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.app_cancel)) } },
    )
}

private val AppThemeMode.title: String
    @Composable get() = when (this) {
        AppThemeMode.System -> stringResource(R.string.app_system)
        AppThemeMode.Light -> stringResource(R.string.app_light)
        AppThemeMode.Dark -> stringResource(R.string.app_dark)
    }

private val AppLanguage.title: String
    @Composable get() = when (this) {
        AppLanguage.System -> stringResource(R.string.app_system)
        AppLanguage.English -> stringResource(R.string.app_english)
        AppLanguage.SimplifiedChinese -> stringResource(R.string.app_chinese)
    }

