package com.mitahv.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.mitahv.mobile.model.BattleLoadout
import com.mitahv.mobile.model.SpellSchool
import com.mitahv.mobile.model.WeaponFamily

@Composable
internal fun BattleTopBar(onStrategy: () -> Unit, onSettings: () -> Unit, settingsEnabled: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.hv_settings_battle), Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onStrategy) { Text(stringResource(R.string.strategy_title)) }
        IconButton(onClick = onSettings, enabled = settingsEnabled, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Settings, stringResource(R.string.strategy_app_settings))
        }
    }
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BattleStrategySheet(loadout: BattleLoadout?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.strategy_title), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Close, stringResource(R.string.app_close)) }
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loadout == null) {
                Text(stringResource(R.string.strategy_no_snapshot), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.strategy_no_snapshot_hint), style = MaterialTheme.typography.bodyMedium)
            } else {
                val family = loadout.family
                val familyName = stringResource(when (family) {
                    WeaponFamily.One -> R.string.strategy_one
                    WeaponFamily.Two -> R.string.strategy_two
                    WeaponFamily.Dual -> R.string.strategy_dual
                    WeaponFamily.Niten -> R.string.strategy_niten
                    WeaponFamily.Unarmed -> R.string.strategy_unarmed
                    WeaponFamily.Staff -> R.string.strategy_staff
                    null -> R.string.strategy_unknown
                })
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.strategy_style), style = MaterialTheme.typography.labelLarge)
                        Text(when (loadout.isBattlemage) {
                            true -> stringResource(R.string.strategy_mage, familyName)
                            false -> stringResource(R.string.strategy_physical, familyName)
                            null -> familyName
                        }, style = MaterialTheme.typography.titleLarge)
                        val school = loadout.school
                        val affinity = when {
                            loadout.maximum == null -> stringResource(R.string.strategy_missing_bonus)
                            school != null -> stringResource(R.string.strategy_affinity, stringResource(school.label))
                            loadout.maximum == 0.0 -> stringResource(R.string.strategy_zero)
                            else -> stringResource(R.string.strategy_tie)
                        }
                        Text(affinity, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(loadout.set?.let { stringResource(R.string.strategy_source_set, it) } ?: stringResource(R.string.strategy_source),
                    style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.strategy_main, handName(loadout.main)))
                Text(stringResource(R.string.strategy_off, handName(loadout.off)))
                HorizontalDivider()
                Text(stringResource(R.string.strategy_bonuses), style = MaterialTheme.typography.titleMedium)
                family?.threshold?.let { Text(stringResource(R.string.strategy_rule, it), style = MaterialTheme.typography.bodySmall) }
                SpellSchool.entries.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { school ->
                            val value = loadout.bonuses[school]?.let { stringResource(R.string.strategy_percent, it) } ?: "—"
                            Text("${stringResource(school.label)}  $value", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun handName(name: String?): String = when (name) {
    null -> stringResource(R.string.strategy_unknown)
    "" -> stringResource(R.string.strategy_empty)
    else -> name
}

private val SpellSchool.label: Int get() = when (this) {
    SpellSchool.Fire -> R.string.strategy_fire
    SpellSchool.Cold -> R.string.strategy_cold
    SpellSchool.Elec -> R.string.strategy_elec
    SpellSchool.Wind -> R.string.strategy_wind
    SpellSchool.Holy -> R.string.strategy_holy
    SpellSchool.Dark -> R.string.strategy_dark
}
