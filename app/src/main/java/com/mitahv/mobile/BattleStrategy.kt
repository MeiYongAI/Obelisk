package com.mitahv.mobile

import android.database.SQLException
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.data.*
import com.mitahv.mobile.model.BattleScene
import com.mitahv.mobile.model.BattleStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared by the battle and both settings entry points. No network, DB writes or turn-based effects. */
@Composable
internal fun rememberStrategy(library: JpxLibrary?, style: BattleStyle?, scene: BattleScene?, inBattle: Boolean): BattleStrategy {
    return key(style, scene, inBattle) {
        val state by produceState(BattleStrategy(null, style, scene, inBattle), library) {
            if (library != null) library.revision.collect {
                value = try { withContext(Dispatchers.IO) { library.strategy(style, scene, inBattle) } }
                    catch (_: SQLException) { BattleStrategy(JpxCatalog(readFailed = true), style, scene, inBattle) }
            }
        }
        state
    }
}

@Composable
internal fun StrategySummary(strategy: BattleStrategy) {
    val catalog = strategy.catalog
    val match = strategy.match
    val colors = MaterialTheme.colorScheme
    val style = strategy.style?.let { styleLabel(it) } ?: stringResource(R.string.strategy_unknown)
    val scene = strategy.scene?.let { stringResource(it.label) } ?: stringResource(
        if (strategy.inBattle) R.string.strategy_unknown else R.string.jpx_no_field)
    Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.jpx_detection, style, scene), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(if (strategy.inBattle) R.string.jpx_current else R.string.jpx_preview_strategy),
                Modifier.padding(top = 6.dp).semantics { heading() }, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(when {
                catalog == null -> "—"
                catalog.readFailed -> stringResource(R.string.jpx_io)
                match == null -> stringResource(if (catalog.manual) R.string.jpx_pick_manual else R.string.jpx_no_match)
                else -> profileLabel(match.entry, catalog.variant(match.entry))
            }, style = MaterialTheme.typography.titleMedium)
            if (match != null) {
                val reason = stringResource(when (match.reason) {
                    MatchReason.Manual -> R.string.jpx_manual
                    MatchReason.Assigned -> R.string.jpx_reason_assigned
                    MatchReason.Exact -> R.string.jpx_reason_exact
                    MatchReason.General -> R.string.jpx_reason_general
                    MatchReason.AssignedGeneral -> R.string.jpx_reason_assigned_general
                })
                val rules = strategy.published?.let { stringResource(R.string.jpx_rows, it.rules) }
                Text(listOfNotNull(reason, rules).joinToString(" · "), color = colors.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

internal val BattleScene.label: Int get() = when (this) {
    BattleScene.General -> R.string.jpx_scene_general
    BattleScene.Arena -> R.string.jpx_scene_arena
    BattleScene.Encounter -> R.string.jpx_scene_encounter
    BattleScene.Colosseum -> R.string.jpx_scene_colosseum
    BattleScene.Battle1000 -> R.string.game_ui_grindfest
    BattleScene.Item -> R.string.jpx_scene_item
    BattleScene.Tower -> R.string.game_ui_tower
}

@Composable
internal fun styleLabel(style: BattleStyle): String {
    val family = stringResource(when (style) {
        BattleStyle.OneHanded, BattleStyle.OneMage -> R.string.strategy_one
        BattleStyle.TwoHanded, BattleStyle.TwoMage -> R.string.strategy_two
        BattleStyle.DualWielding, BattleStyle.DualMage -> R.string.strategy_dual
        BattleStyle.NitenIchiryu, BattleStyle.NitenMage -> R.string.strategy_niten
        BattleStyle.Staff -> R.string.strategy_staff
        BattleStyle.Unarmed -> R.string.strategy_unarmed
    })
    return if (style.key.endsWith("_Mage")) stringResource(R.string.strategy_mage, family) else family
}
