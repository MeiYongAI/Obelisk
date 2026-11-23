package com.mitahv.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun BattleTopBar(onSettings: () -> Unit, settingsEnabled: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.hv_settings_battle), Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onSettings, enabled = settingsEnabled, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Settings, stringResource(R.string.strategy_app_settings))
        }
    }
    HorizontalDivider()
}
