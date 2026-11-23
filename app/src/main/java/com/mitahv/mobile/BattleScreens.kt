package com.mitahv.mobile

import androidx.compose.ui.res.stringResource
import com.mitahv.mobile.i18n.gameText
import com.mitahv.mobile.i18n.gameActionText
import com.mitahv.mobile.i18n.battleLogText
import com.mitahv.mobile.i18n.messageText

import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.model.ArenaChallenge
import com.mitahv.mobile.model.ArenaPage
import com.mitahv.mobile.model.BattleAction
import com.mitahv.mobile.model.BattleActionOption
import com.mitahv.mobile.model.BattleLoadout
import com.mitahv.mobile.model.BattlePage
import com.mitahv.mobile.model.BattleContinuation
import com.mitahv.mobile.model.BattleEnemy
import com.mitahv.mobile.model.NativeAction
import okhttp3.HttpUrl

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BattleSessionScreen(
    state: RequestState,
    loadout: BattleLoadout?,
    onSettings: () -> Unit,
    settingsEnabled: Boolean,
    loading: Boolean,
    nativeBusy: Boolean,
    error: String?,
    needsRefresh: Boolean,
    responseRevision: Int,
    loadImage: suspend (HttpUrl) -> ByteArray,
    onRetry: () -> Unit,
    onRiddleAction: (NativeAction) -> Unit,
    onSubmitBattleAction: (BattlePage, BattleAction) -> Unit,
    onContinue: (BattleContinuation) -> Unit,
    onLeaveBattle: () -> Unit,
) {
    var strategyOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = !strategyOpen) {
        val page = (state as? RequestState.BattleReady)?.page
        if (!loading && page?.isComplete == true) {
            val continuation = page.continuation
            if (continuation == null) onLeaveBattle()
            else if (continuation.destination != null) onContinue(continuation)
        } else if (!loading && state is RequestState.Failed) {
            onRetry()
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            BattleTopBar(onStrategy = { strategyOpen = true }, onSettings = onSettings, settingsEnabled = settingsEnabled)
            if (error != null) Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(messageText(error), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry, enabled = !loading && !nativeBusy) {
                    Text(stringResource(R.string.app_retry))
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                when (state) {
                    is RequestState.BattleReady, is RequestState.BattleBusy -> {
                        val page = when (state) {
                            is RequestState.BattleReady -> state.page
                            is RequestState.BattleBusy -> state.page
                            else -> error("Expected battle state")
                        }
                        BattleScreen(
                            page = page,
                            busy = loading || state is RequestState.BattleBusy,
                            onSubmitAction = { onSubmitBattleAction(page, it) },
                            onContinue = onContinue,
                            onLeaveBattle = onLeaveBattle,
                        )
                    }
                    is RequestState.RiddleReady -> RiddleScreen(
                        page = state.page,
                        busy = loading || nativeBusy || needsRefresh,
                        onAction = onRiddleAction,
                        loadImage = loadImage,
                        responseRevision = responseRevision,
                        needsRefresh = needsRefresh,
                    )
                    is RequestState.Failed -> Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(messageText(state.message))
                        Button(onClick = onRetry, enabled = !loading) {
                            Text(stringResource(R.string.app_retry))
                        }
                    }
                    else -> androidx.compose.material3.LoadingIndicator(Modifier.size(44.dp))
                }
                if (loading || nativeBusy || state is RequestState.BattleBusy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
        }
    }
    if (strategyOpen) BattleStrategySheet(loadout) { strategyOpen = false }
}

@Composable
internal fun ArenaScreen(
    page: ArenaPage,
    busy: Boolean,
    onStartChallenge: (ArenaChallenge) -> Unit,
) {
    var pending by remember(page) { mutableStateOf<ArenaChallenge?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (page.challenges.isEmpty()) {
            item {
                Text(if (page.emptyMessage.isBlank()) stringResource(R.string.game_ui_no_challenges) else messageText(page.emptyMessage),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        itemsIndexed(page.challenges, key = { index, challenge -> "${challenge.id ?: index}:${challenge.name}" }) { _, challenge ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(gameText(challenge.name), style = MaterialTheme.typography.titleMedium)
                        val details = listOf(challenge.highestClear, challenge.minimumLevel,
                            challenge.rounds.takeIf(String::isNotBlank)?.let { stringResource(R.string.game_ui_round_count, it) }, challenge.experienceMultiplier)
                            .filterNotNull().filter(String::isNotBlank).joinToString(" · ")
                        if (details.isNotBlank()) Text(messageText(details), style = MaterialTheme.typography.bodySmall)
                        if (challenge.entryCost.isNotBlank() || challenge.clearBonus.isNotBlank()) {
                            Text(
                                messageText(listOf(challenge.entryCost, challenge.clearBonus)
                                    .filter(String::isNotBlank).joinToString(" · ")),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        if (challenge.availabilityInfo.isNotBlank()) {
                            Text(messageText(challenge.availabilityInfo), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (challenge.available && !busy) {
                        Button(onClick = { pending = challenge }) { Text(stringResource(R.string.game_ui_start)) }
                    } else {
                        OutlinedButton(onClick = {}, enabled = false) {
                            Text(stringResource(if (busy) R.string.game_ui_start else if (challenge.availabilityInfo.startsWith("Cooldown")) R.string.game_ui_cooldown else R.string.game_ui_unavailable))
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
        }
    }
    pending?.let { challenge ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(gameText(challenge.name)) },
            text = {
                val cost = challenge.entryCost.takeIf(String::isNotBlank)
                Text(if (cost == null) stringResource(R.string.game_ui_start_challenge) else stringResource(R.string.game_ui_entry_cost, messageText(cost)))
            },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    pending = null
                    onStartChallenge(challenge)
                }) { Text(stringResource(R.string.game_ui_start)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(stringResource(R.string.game_ui_cancel)) }
            },
        )
    }
}

@Composable
internal fun BattleScreen(
    page: BattlePage,
    busy: Boolean,
    onSubmitAction: (BattleAction) -> Unit,
    onContinue: (BattleContinuation) -> Unit,
    onLeaveBattle: () -> Unit,
) {
    var panel by remember(page.turnKey) { mutableStateOf(BattlePanel.Attack) }
    var pendingOptionId by remember(page.turnKey) { mutableStateOf<String?>(null) }
    val pendingOption = (page.quickActions + page.skills + page.spells + page.items)
        .firstOrNull { it.id == pendingOptionId }
    var detail by remember(page.turnKey) { mutableStateOf<BattleDetail?>(null) }
    var enemyDetail by remember(page.turnKey) { mutableStateOf<BattleEnemy?>(null) }
    var showLog by remember(page.turnKey) { mutableStateOf(false) }
    val isComplete = page.isComplete
    val attackEnabled = page.controls.any { it.mode == "attack" && it.enabled }
    val actionEnabled = !busy && !isComplete

    fun chooseOption(option: BattleActionOption) {
        selectOption(option, pendingOption, { pendingOptionId = it?.id }, onSubmitAction)
        panel = BattlePanel.Attack
    }

    Column(Modifier.fillMaxSize()) {
        BattleHud(page, busy, onOpenLog = { showLog = true })
        HorizontalDivider()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val enemyColumns = if (maxWidth >= 600.dp) 4 else 2
            if (!isComplete && panel != BattlePanel.Attack) {
                val choices = when (panel) {
                    BattlePanel.Skill -> page.skills
                    BattlePanel.Magic -> page.spells
                    BattlePanel.Items -> page.items
                    BattlePanel.Attack -> emptyList()
                }
                BattleOptionPanel(
                    title = stringResource(panel.labelRes),
                    options = choices,
                    selectedId = pendingOption?.id,
                    busy = busy,
                    modifier = Modifier.fillMaxSize(),
                    onSelect = ::chooseOption,
                    onDismiss = { panel = BattlePanel.Attack },
                    onDetail = { option, cost -> detail = BattleDetail(option.name, option.description, cost) },
                )
            } else LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            if (isComplete) {
                item(key = "completion") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (page.completionLines.isEmpty()) {
                                Text(stringResource(R.string.game_ui_battle_complete), style = MaterialTheme.typography.titleMedium)
                            } else {
                                page.completionLines.forEachIndexed { index, line ->
                                    Text(messageText(line), style = if (index == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            } else {
                if (page.playerEffects.isNotEmpty()) item(key = "player-effects") {
                    BattleEffectChips(page.playerEffects) { info -> detail = info }
                }
                if (page.enemies.isNotEmpty()) {
                    items(
                        page.enemies.chunked(enemyColumns),
                        key = { row -> "enemy-row-${row.first().target}" },
                    ) { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { enemy ->
                                val canTarget = actionEnabled && enemy.targetable &&
                                    (pendingOption?.targetsEnemy == true || panel == BattlePanel.Attack && attackEnabled)
                                ElevatedCard(
                                    onClick = {
                                        if (canTarget) {
                                            val chosen = pendingOption
                                            onSubmitAction(BattleAction(chosen?.mode ?: "attack", enemy.target, chosen?.skill ?: 0))
                                            pendingOptionId = null
                                        }
                                    },
                                    enabled = canTarget,
                                    modifier = Modifier.weight(1f).height(84.dp),
                                ) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text("#${enemy.target}", style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary)
                                            Text(enemy.name, modifier = Modifier.weight(1f).padding(start = 6.dp),
                                                style = MaterialTheme.typography.labelLarge, maxLines = 1,
                                                overflow = TextOverflow.Ellipsis)
                                            androidx.compose.material3.IconButton(
                                                onClick = { enemyDetail = enemy },
                                                modifier = Modifier.size(44.dp),
                                            ) {
                                                Icon(
                                                    Icons.Filled.Info,
                                                    contentDescription = stringResource(R.string.game_ui_named_details, enemy.name),
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                        BattleEnemyMiniBar(enemy.healthFraction, MaterialTheme.colorScheme.error)
                                        if (enemy.magicFraction != null || enemy.spiritFraction != null) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                enemy.magicFraction?.let { BattleEnemyMiniBar(it, MaterialTheme.colorScheme.primary, Modifier.weight(1f)) }
                                                enemy.spiritFraction?.let { BattleEnemyMiniBar(it, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f)) }
                                            }
                                        }
                                    }
                                }
                            }
                            repeat(enemyColumns - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            }
        }
        if (isComplete) {
            val continuation = page.continuation
            Button(
                onClick = { if (continuation != null) onContinue(continuation) else onLeaveBattle() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 52.dp),
            ) {
                val label = continuation?.let { messageText(it.label) }
                    ?: stringResource(R.string.game_ui_return_to_challenge)
                Text(label)
            }
        } else {
            BattleControls(page, panel, pendingOption, busy,
                onCancelPending = { pendingOptionId = null },
                onPanelSelected = { pendingOptionId = null; panel = it },
                onChooseOption = ::chooseOption, onSubmitAction = onSubmitAction)
        }
    }
    if (showLog) BattleLogSheet(page.log, onDismiss = { showLog = false })
    enemyDetail?.let { enemy ->
        AlertDialog(
            onDismissRequest = { enemyDetail = null },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(enemy.name)
                    if (enemy.level.isNotBlank()) Text(
                        stringResource(R.string.game_ui_level, enemy.level),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BattleEnemyBar(stringResource(R.string.game_ui_hp), enemy.healthFraction, MaterialTheme.colorScheme.error)
                    enemy.magicFraction?.let {
                        BattleEnemyBar(stringResource(R.string.game_ui_mp), it, MaterialTheme.colorScheme.primary)
                    }
                    enemy.spiritFraction?.let {
                        BattleEnemyBar(stringResource(R.string.game_ui_sp), it, MaterialTheme.colorScheme.tertiary)
                    }
                    if (enemy.effects.isNotEmpty()) {
                        HorizontalDivider()
                        BattleEffectChips(enemy.effects) { info ->
                            enemyDetail = null
                            detail = info
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { enemyDetail = null }) { Text(stringResource(R.string.game_ui_close)) }
            },
        )
    }
    detail?.let { info ->
        AlertDialog(onDismissRequest = { detail = null }, title = { Text(gameText(info.title)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (info.description.isNotBlank()) Text(gameText(info.description))
                if (info.meta.isNotBlank()) Text(messageText(info.meta), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } },
            confirmButton = { TextButton(onClick = { detail = null }) { Text(stringResource(R.string.game_ui_close)) } })
    }
}

@Composable
private fun BattleHud(page: BattlePage, busy: Boolean, onOpenLog: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (page.playerVitals.isNotEmpty()) Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            page.playerVitals.forEach { vital -> BattleVitalCell(vital, Modifier.weight(1f)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.game_ui_exp), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(
                    progress = { (page.experienceBarWidth / 1234f).coerceIn(0f, 1f) },
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
            }
            androidx.compose.material3.IconButton(onClick = onOpenLog, enabled = !busy) {
                Icon(Icons.Filled.History, contentDescription = stringResource(R.string.game_ui_battle_log))
            }
        }
    }
}

@Composable
private fun BattleVitalCell(vital: com.mitahv.mobile.model.BattleVital, modifier: Modifier = Modifier) {
    val label = when (vital.name) {
        "Health" -> R.string.game_ui_hp
        "Mana" -> R.string.game_ui_mp
        "Spirit" -> R.string.game_ui_sp
        "Overcharge" -> R.string.game_ui_overcharge
        else -> null
    }
    val color = when (vital.name) {
        "Health" -> MaterialTheme.colorScheme.error
        "Mana" -> MaterialTheme.colorScheme.primary
        "Spirit" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label?.let { stringResource(it) } ?: gameText(vital.name),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(vital.value, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1)
            }
            LinearProgressIndicator(
                progress = { vital.fraction },
                color = color,
                modifier = Modifier.fillMaxWidth().height(4.dp),
            )
        }
    }
}

@Composable
private fun BattleEnemyMiniBar(
    value: Float,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    LinearProgressIndicator(
        progress = { value.coerceIn(0f, 1f) },
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth().height(3.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BattleLogSheet(log: List<String>, onDismiss: () -> Unit) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.75f)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.game_ui_battle_log), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                androidx.compose.material3.IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, stringResource(R.string.game_ui_close))
                }
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (log.isEmpty()) {
                    item { Text(stringResource(R.string.game_ui_battle_log_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    itemsIndexed(log) { _, line -> Text(battleLogText(line), style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun BattleControls(
    page: BattlePage,
    panel: BattlePanel,
    pendingOption: BattleActionOption?,
    busy: Boolean,
    onCancelPending: () -> Unit,
    onPanelSelected: (BattlePanel) -> Unit,
    onChooseOption: (BattleActionOption) -> Unit,
    onSubmitAction: (BattleAction) -> Unit,
) {
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            pendingOption?.let { option ->
                androidx.compose.material3.InputChip(selected = true, onClick = onCancelPending,
                    enabled = !busy, label = { Text(stringResource(R.string.game_ui_choose_target, gameText(option.name)), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = { Icon(Icons.Filled.Close, stringResource(R.string.game_ui_cancel_selection)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp))
            }
            val commands = page.controls.filter { it.mode in setOf("spirit", "defend", "focus") }
            // Stances belong to the action pane. Quickbar skills are a separate
            // official quickbar and must not be allowed to clip as a fourth stance.
            if (commands.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    commands.forEach { control ->
                        val (label, icon) = when (control.mode) {
                            "spirit" -> R.string.game_ui_spirit_stance to Icons.Filled.Favorite
                            "defend" -> R.string.game_ui_defend to Icons.Filled.Shield
                            else -> R.string.game_ui_focus to Icons.Filled.CenterFocusStrong
                        }
                        FilledTonalButton(
                            onClick = { onSubmitAction(BattleAction(control.mode)) },
                            enabled = !busy && control.enabled,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Icon(if (control.selected) Icons.Filled.Check else icon, null)
                            Text(
                                stringResource(label),
                                Modifier.padding(start = 6.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (page.quickActions.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(end = 10.dp),
                ) {
                    items(page.quickActions, key = { "quick-${it.id}" }) { option ->
                        androidx.compose.material3.FilterChip(
                            selected = pendingOption?.id == option.id,
                            onClick = { onChooseOption(option) },
                            enabled = !busy && option.enabled,
                            label = {
                                Text(
                                    gameText(option.name),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier.heightIn(min = 44.dp),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BattlePanel.entries.forEach { item ->
                    val control = page.controls.firstOrNull { it.mode == item.control }
                    if (control != null) Surface(
                        selected = panel == item && pendingOption == null,
                        onClick = { onPanelSelected(item) },
                        enabled = !busy && control.enabled,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = if (panel == item && pendingOption == null) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(Modifier.padding(horizontal = 2.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                            Icon(item.icon, null, modifier = Modifier.size(20.dp))
                            Text(stringResource(item.labelRes), style = MaterialTheme.typography.labelMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BattleOptionPanel(
    title: String,
    options: List<BattleActionOption>,
    selectedId: String?,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (BattleActionOption) -> Unit,
    onDismiss: () -> Unit,
    onDetail: (BattleActionOption, String) -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            androidx.compose.material3.IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, stringResource(R.string.game_ui_close))
            }
        }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (options.isEmpty()) item {
                Text(stringResource(R.string.game_ui_no_actions), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            options.groupBy { it.group }.forEach { (group, entries) ->
                if (group.isNotBlank()) item(key = "group-$group") {
                    Text(battleActionGroupText(group), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall)
                }
                items(entries, key = { "option-${it.id}" }) { option ->
                    val detailCost = option.costText(includeCooldown = true)
                    BattleOptionRow(option, selectedId == option.id, busy,
                        onSelect = { onSelect(option) }, onDetail = { onDetail(option, detailCost) })
                }
            }
        }
    }
}

@Composable
private fun battleActionGroupText(group: String): String = when (group) {
    "Innate" -> stringResource(R.string.game_ui_action_group_innate)
    "Weapon" -> stringResource(R.string.game_ui_action_group_weapon)
    "Damage" -> stringResource(R.string.game_ui_action_group_damage)
    "Deprecating" -> stringResource(R.string.game_ui_action_group_deprecating)
    "Curative" -> stringResource(R.string.game_ui_action_group_curative)
    "Supportive" -> stringResource(R.string.game_ui_action_group_supportive)
    else -> gameActionText(group)
}

@Composable
private fun BattleEnemyBar(label: String, value: Float, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        LinearProgressIndicator(progress = { value }, color = color, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.game_ui_percent, (value * 100).toInt()), style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BattleEffectChips(effects: List<com.mitahv.mobile.model.BattleEffect>, onDetail: (BattleDetail) -> Unit) {
    if (effects.isEmpty()) return
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        effects.forEach { effect ->
            val detail = effect.detail()
            androidx.compose.material3.AssistChip(onClick = { onDetail(detail) }, label = {
                Text(gameText(effect.name) + (effect.duration.toIntOrNull()?.let { " · $it" } ?: ""))
            }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable
private fun BattleOptionRow(option: BattleActionOption, selected: Boolean, busy: Boolean, onSelect: () -> Unit, onDetail: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onSelect, enabled = !busy && option.enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(gameActionText(option.name), style = MaterialTheme.typography.labelLarge)
                option.costText().takeIf(String::isNotBlank)?.let { Text(messageText(it), style = MaterialTheme.typography.labelSmall) }
            }
            if (selected) Icon(Icons.Filled.Check, stringResource(R.string.game_ui_selected))
        }
        if (option.description.isNotBlank() || option.costText(true).isNotBlank()) {
            androidx.compose.material3.IconButton(onClick = onDetail) { Icon(Icons.Filled.Info, stringResource(R.string.game_ui_named_details, gameText(option.name))) }
        }
    }
}

@Composable
private fun BattleActionOption.costText(includeCooldown: Boolean = false): String = listOfNotNull(
    manaCost?.takeIf { it > 0 }?.let { stringResource(R.string.game_ui_mp_cost, it) },
    chargeCost?.takeIf { it > 0 }?.let { stringResource(R.string.game_ui_charge_cost, it) },
    if (includeCooldown) cooldown?.takeIf { it > 0 }?.let { stringResource(R.string.game_ui_cooldown_turns, it) } else null,
).joinToString(" · ")

private data class BattleDetail(val title: String, val description: String, val meta: String)

@Composable
private fun com.mitahv.mobile.model.BattleEffect.detail() = BattleDetail(name, description, when (duration) {
    "autocast" -> stringResource(R.string.game_ui_autocast_duration)
    "permanent" -> stringResource(R.string.game_ui_permanent_duration)
    "decaying" -> stringResource(R.string.game_ui_decaying_duration)
    else -> duration.toIntOrNull()?.let { stringResource(R.string.game_ui_effect_duration, it) } ?: duration
})

private fun selectOption(
    option: BattleActionOption,
    current: BattleActionOption?,
    onPending: (BattleActionOption?) -> Unit,
    onSubmit: (BattleAction) -> Unit,
) {
    if (!option.enabled) return
    if (option.targetsEnemy) onPending(if (current?.id == option.id) null else option)
    else { onPending(null); onSubmit(BattleAction(option.mode, skill = option.skill)) }
}

private enum class BattlePanel(val labelRes: Int, val icon: ImageVector, val control: String) {
    Attack(R.string.game_ui_attack, Icons.Filled.SportsMartialArts, "attack"),
    Skill(R.string.game_ui_skills, Icons.Filled.Bolt, "skill"),
    Magic(R.string.game_ui_magic, Icons.Filled.AutoFixHigh, "skill"),
    Items(R.string.game_ui_items, Icons.Filled.Inventory2, "items"),
}
