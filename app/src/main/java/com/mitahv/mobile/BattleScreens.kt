package com.mitahv.mobile

import com.mitahv.mobile.data.BattleRules
import com.mitahv.mobile.data.BattleStrategy
import androidx.compose.ui.res.stringResource
import com.mitahv.mobile.i18n.gameText
import com.mitahv.mobile.i18n.gameActionText
import com.mitahv.mobile.i18n.battleLogText
import com.mitahv.mobile.i18n.messageText

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.mitahv.mobile.model.BattleEffect
import com.mitahv.mobile.model.NativeAction
import okhttp3.HttpUrl
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BattleSessionScreen(
    playerLevel: Int?,
    session: () -> BattleSessionState,
    loadout: BattleLoadout?,
    world: String,
    strategy: BattleStrategy,
    onSettings: () -> Unit,
    settingsEnabled: Boolean,
    autoNextRound: Boolean,
    responseRevision: Int,
    loadImage: suspend (HttpUrl) -> ByteArray,
    onRetry: () -> Unit,
    onRiddleAction: (NativeAction) -> Unit,
    onSubmitBattleAction: (BattlePage, BattleAction) -> Unit,
    onContinue: (BattleContinuation) -> Unit,
    onLeaveBattle: () -> Unit,
) {
    val snapshot = session()
    val (state, loading, nativeBusy, error, needsRefresh) = snapshot
    val page = snapshot.page
    val runner = remember(page?.encounterKey, page?.isComplete,
        strategy.match?.entry?.id, strategy.published, world) { RuleRunner() }
    val waiting = loading || nativeBusy || state is RequestState.BattleBusy
    var showProgress by remember(waiting, page?.turnKey) { mutableStateOf(waiting && page == null) }
    if (waiting && page != null) LaunchedEffect(page.turnKey) {
        delay(1_000)
        showProgress = true
    }
    // Lock input immediately. Only the appearance waits for a slow response.
    val shortTurnRequest = state is RequestState.BattleBusy && !showProgress && !nativeBusy && !needsRefresh && error == null
    val refreshEnabled = !loading && !nativeBusy && state !is RequestState.BattleBusy
    val refresh = { runner.pause(); onRetry() }
    val ready = snapshot.ready
    val playable = ready && page?.isComplete == false
    val betweenRounds = page?.isComplete == true && page.continuation?.isNextRound == true
    val advancing = betweenRounds && !nativeBusy && error == null && !needsRefresh &&
        ((autoNextRound && ready) || state is RequestState.BattleBusy)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val latestContinue by rememberUpdatedState(onContinue)
    LaunchedEffect(page?.turnKey, advancing, ready, lifecycleState) {
        if (advancing && ready && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            latestContinue(requireNotNull(page?.continuation))
    }
    val rules = remember(strategy.published) { strategy.published?.let(::BattleRules) }
    RunBattleTurns(runner, rules, world, strategy.scene, loadout, playerLevel, session, onSubmitBattleAction)
    BackHandler {
        val page = (state as? RequestState.BattleReady)?.page
        if (!loading && page?.isComplete == true) {
            val continuation = page.continuation
            if (continuation == null) onLeaveBattle()
            else if (continuation.destination != null) onContinue(continuation)
        } else if (refreshEnabled && state is RequestState.Failed) {
            refresh()
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            BattleTopBar(onRefresh = refresh, refreshEnabled = refreshEnabled,
                onSettings = { runner.pause(); onSettings() }, settingsEnabled = settingsEnabled,
                keepIconColors = shortTurnRequest)
            if (error != null) Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(messageText(error), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = refresh, enabled = refreshEnabled) {
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
                            busy = loading || nativeBusy || needsRefresh || state is RequestState.BattleBusy || (!page.isComplete && runner.running),
                            onSubmitAction = { if (!runner.running) onSubmitBattleAction(page, it) },
                            advancing = advancing,
                            autoAvailable = rules != null,
                            running = runner.running,
                            canStart = playable,
                            onToggleAuto = {
                                if (runner.running) runner.pause()
                                else {
                                    val current = session()
                                    val latest = current.page
                                    if (current.ready && latest != null && !latest.isComplete &&
                                        latest.encounterKey === page.encounterKey &&
                                        latest.round.current == page.round.current && latest.round.floor == page.round.floor) runner.start(latest)
                                }
                            },
                            notice = runner.notice,
                            onContinue = onContinue,
                            onLeaveBattle = onLeaveBattle,
                            keepActionColors = shortTurnRequest && !runner.running,
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
                        Button(onClick = refresh, enabled = refreshEnabled) {
                            Text(stringResource(R.string.app_retry))
                        }
                    }
                    else -> androidx.compose.material3.LoadingIndicator(Modifier.size(44.dp))
                }
                if (showProgress) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
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
    advancing: Boolean,
    autoAvailable: Boolean,
    running: Boolean,
    canStart: Boolean,
    onToggleAuto: () -> Unit,
    notice: com.mitahv.mobile.data.RuleTurn?,
    onContinue: (BattleContinuation) -> Unit,
    onLeaveBattle: () -> Unit,
    keepActionColors: Boolean = false,
) {
    var panel by remember(page.encounterKey, page.round.current, page.round.floor) { mutableStateOf(BattlePanel.Attack) }
    var pendingOption by remember(page.turnKey) { mutableStateOf<BattleActionOption?>(null) }
    var lastPlayablePage by remember(page.encounterKey) { mutableStateOf<BattlePage?>(null) }
    if (!page.isComplete) SideEffect { lastPlayablePage = page }
    // The completion pane may remove the targets and commands. Keep the last confirmed field
    // visible and locked until the server provides the new round's roster.
    val field = if (advancing) lastPlayablePage ?: page else page
    var detail by remember(page.turnKey) { mutableStateOf<BattleDetail?>(null) }
    var enemyTarget by remember(page.encounterKey, page.round.current, page.round.floor, page.isComplete) { mutableStateOf<Int?>(null) }
    val enemyDetail = enemyTarget?.let { id -> page.enemies.firstOrNull { it.target == id } }
    var showLog by remember(page.encounterKey) { mutableStateOf(false) }
    var showEffects by remember(page.encounterKey) { mutableStateOf(false) }
    val isComplete = page.isComplete
    val attackEnabled = page.controls.any { it.mode == "attack" && it.enabled }
    val actionEnabled = !busy && !isComplete

    val chooseOption: (BattleActionOption) -> Unit = { option ->
        if (option.enabled && actionEnabled) {
            panel = BattlePanel.Attack
            if (option.targetsEnemy) {
                pendingOption = option
            } else {
                pendingOption = null
                onSubmitAction(BattleAction(option.mode, skill = option.skill))
            }
        }
    }
    val onTarget: (BattleEnemy) -> Unit = { enemy ->
        if (actionEnabled && enemy.targetable) {
            val option = pendingOption
            if (option != null) {
                onSubmitAction(BattleAction(option.mode, enemy.target, option.skill))
            } else if (panel == BattlePanel.Attack && attackEnabled) {
                onSubmitAction(BattleAction("attack", enemy.target))
            }
        }
    }

    BattleLayout(
        battlefield = { modifier ->
            Column(modifier) {
                BattleHud(field, onOpenEffects = { showEffects = true }, onOpenLog = { showLog = true })
                HorizontalDivider()
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val enemyColumns = when {
                        maxWidth >= 840.dp -> 3
                        maxWidth >= 600.dp || (maxWidth >= 352.dp && field.enemies.size >= 5) -> 2
                        else -> 1
                    }
                    val effectLimit = if (maxWidth / enemyColumns < 230.dp) 1 else 2
                    if (!field.isComplete && panel != BattlePanel.Attack) {
                        val choices = when (panel) {
                            BattlePanel.Skill -> field.skills
                            BattlePanel.Magic -> field.spells
                            BattlePanel.Items -> field.items
                            BattlePanel.Attack -> emptyList()
                        }
                        BattleOptionPanel(
                            title = stringResource(panel.labelRes),
                            options = choices,
                            remainingCooldowns = field.remainingCooldowns,
                            busy = busy || isComplete,
                            modifier = Modifier.fillMaxSize(),
                            onSelect = chooseOption,
                            onDismiss = { panel = BattlePanel.Attack },
                            onDetail = { option, cost -> detail = BattleDetail(option.name, option.description, cost, option) },
                        )
                    } else LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (field.isComplete) {
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
                            items(
                                field.enemies.chunked(enemyColumns),
                                key = { row -> "enemy-row-${row.first().target}" },
                            ) { row ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    row.forEach { enemy ->
                                        BattleEnemyCard(
                                            enemy = enemy,
                                            onClick = { onTarget(enemy) },
                                            enabled = actionEnabled && enemy.targetable && (pendingOption != null || attackEnabled),
                                            onDetail = { enemyTarget = enemy.target },
                                            showDetail = !advancing,
                                            effectLimit = effectLimit,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(enemyColumns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        },
        controls = {
            if (field.isComplete) {
                val continuation = page.continuation
                Button(
                    onClick = { if (continuation != null) onContinue(continuation) else onLeaveBattle() },
                    enabled = !busy && !advancing,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 52.dp),
                ) {
                    val label = continuation?.let { messageText(it.label) }
                        ?: stringResource(R.string.game_ui_return_to_challenge)
                    Text(label)
                }
            } else {
                notice?.let { RuleRunNotice(it) }
                BattleControls(field, panel.takeIf { pendingOption == null }, pendingOption, busy || isComplete,
                    keepActionColors = keepActionColors,
                    canBrowse = !isComplete,
                    autoAvailable = autoAvailable, running = running, canStart = canStart, onToggleAuto = onToggleAuto,
                    onCancelPending = { pendingOption = null },
                    onPanelSelected = {
                        pendingOption = null
                        panel = it
                    },
                    onChooseOption = chooseOption, onSubmitAction = onSubmitAction)
            }
        },
    )
    if (showLog) BattleLogSheet(page.log, onDismiss = { showLog = false })
    if (showEffects) BattleEffectsDialog(page.playerEffects, onDismiss = { showEffects = false })
    enemyDetail?.let { enemy ->
        AlertDialog(
            onDismissRequest = { enemyTarget = null },
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
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (enemy.healthMeasured) BattleEnemyBar(stringResource(R.string.game_ui_hp), enemy.healthFraction, MaterialTheme.colorScheme.error)
                    enemy.magicFraction?.let {
                        BattleEnemyBar(stringResource(R.string.game_ui_mp), it, MaterialTheme.colorScheme.primary)
                    }
                    enemy.spiritFraction?.let {
                        BattleEnemyBar(stringResource(R.string.game_ui_sp), it, MaterialTheme.colorScheme.tertiary)
                    }
                    if (enemy.effects.isNotEmpty()) {
                        HorizontalDivider()
                        BattleEffectChips(enemy.effects) { info ->
                            enemyTarget = null
                            detail = info
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { enemyTarget = null }) { Text(stringResource(R.string.game_ui_close)) }
            },
        )
    }
    detail?.let { info ->
        AlertDialog(onDismissRequest = { detail = null }, title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                info.action?.let { BattleActionArtwork(it, Modifier.size(36.dp)) }
                Text(info.action?.displayName() ?: gameText(info.title))
            }
        },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (info.description.isNotBlank()) Text(gameText(info.description))
                if (info.meta.isNotBlank()) Text(messageText(info.meta), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } },
            confirmButton = { TextButton(onClick = { detail = null }) { Text(stringResource(R.string.game_ui_close)) } })
    }
}

@Composable
private fun BattleLayout(battlefield: @Composable (Modifier) -> Unit, controls: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 540.dp && maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                battlefield(Modifier.weight(1.25f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { controls() }
            }
        } else {
            val controlsMaxHeight = maxHeight * .48f
            Column(Modifier.fillMaxSize()) {
                battlefield(Modifier.weight(1f).fillMaxWidth())
                Column(Modifier.heightIn(max = controlsMaxHeight).verticalScroll(rememberScrollState())) { controls() }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BattleHud(page: BattlePage, onOpenEffects: () -> Unit, onOpenLog: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val round = page.round
            Text(
                if (round.floor != null && round.current == null && round.total == null)
                    stringResource(R.string.game_ui_battle_floor, round.floor)
                else stringResource(R.string.game_ui_battle_round, round.current?.toString() ?: "—", round.total?.toString() ?: "—"),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (page.isComplete) stringResource(R.string.game_ui_enemy_count, page.enemies.size)
                else stringResource(R.string.game_ui_enemy_targets, page.enemies.size, page.enemies.count { it.targetable }),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        if (page.playerVitals.isNotEmpty()) Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            page.playerVitals.forEach { vital -> BattleVitalCell(vital, Modifier.weight(1f)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (page.playerEffects.isNotEmpty()) {
                val label = stringResource(R.string.game_ui_player_effect_count, page.playerEffects.size)
                BoxWithConstraints(Modifier.weight(1f).clickable(role = Role.Button, onClick = onOpenEffects)
                    .semantics { contentDescription = label }.heightIn(min = 48.dp).padding(start = 4.dp),
                    contentAlignment = Alignment.CenterStart) {
                    BattleEffectStrip(page.playerEffects, limit = ((maxWidth.value - 28) / 34).toInt().coerceIn(1, 8))
                }
            } else {
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
            androidx.compose.material3.IconButton(onClick = onOpenLog) {
                Icon(Icons.Filled.History, contentDescription = stringResource(R.string.game_ui_battle_log))
            }
        }
        val expLabel = stringResource(R.string.game_ui_exp)
        LinearProgressIndicator(progress = { (page.experienceBarWidth / 1234f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(3.dp).semantics { contentDescription = expLabel },
            color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun BattleEffectsDialog(effects: List<BattleEffect>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_ui_player_effect_count, effects.size)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(effects) { effect ->
                    val info = effect.detail()
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            BattleEffectBadge(effect)
                            Text(gameText(info.title), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            if (info.meta.isNotBlank()) Text(messageText(info.meta), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        }
                        if (info.description.isNotBlank()) Text(gameText(info.description), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_ui_close)) } },
    )
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
private fun BattleEnemyCard(
    enemy: BattleEnemy, enabled: Boolean, onClick: () -> Unit, onDetail: () -> Unit,
    showDetail: Boolean, effectLimit: Int, modifier: Modifier = Modifier,
) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                    .heightIn(min = 56.dp).padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("#${enemy.target}", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false,
                        color = MaterialTheme.colorScheme.primary)
                    Text(enemy.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                        color = if (enemy.targetable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 22.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (enemy.healthMeasured) {
                        LinearProgressIndicator(progress = { enemy.healthFraction }, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f).height(4.dp))
                        Text(stringResource(R.string.game_ui_percent, (enemy.healthFraction * 100).toInt()),
                            style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                    }
                    if (enemy.effects.isNotEmpty()) BattleEffectStrip(enemy.effects, limit = effectLimit, compact = true)
                }
            }
            androidx.compose.material3.IconButton(onClick = onDetail, enabled = showDetail) {
                Icon(Icons.Filled.Info, stringResource(R.string.game_ui_named_details, enemy.name),
                    modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
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
    panel: BattlePanel?,
    pendingOption: BattleActionOption?,
    busy: Boolean,
    keepActionColors: Boolean,
    canBrowse: Boolean,
    autoAvailable: Boolean,
    running: Boolean,
    canStart: Boolean,
    onToggleAuto: () -> Unit,
    onCancelPending: () -> Unit,
    onPanelSelected: (BattlePanel) -> Unit,
    onChooseOption: (BattleActionOption) -> Unit,
    onSubmitAction: (BattleAction) -> Unit,
) {
    val tonal = ButtonDefaults.filledTonalButtonColors()
    val actionColors = if (keepActionColors) tonal.copy(
        disabledContainerColor = tonal.containerColor, disabledContentColor = tonal.contentColor,
    ) else tonal
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            pendingOption?.let { option ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.game_ui_choose_target, option.displayName()),
                        Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    androidx.compose.material3.IconButton(onClick = onCancelPending, enabled = !busy) {
                        Icon(Icons.Filled.Close, stringResource(R.string.game_ui_cancel_selection))
                    }
                }
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
                        val label = when (control.mode) {
                            "spirit" -> R.string.game_ui_spirit_stance
                            "defend" -> R.string.game_ui_defend
                            else -> R.string.game_ui_focus
                        }
                        FilledTonalButton(
                            onClick = { onSubmitAction(BattleAction(control.mode)) },
                            enabled = !busy && control.enabled,
                            colors = if (control.enabled) actionColors else tonal,
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            if (control.selected) Icon(Icons.Filled.Check, null, Modifier.size(20.dp))
                            else {
                                val (glyph, palette) = stanceGlyph(control.mode)
                                GameArt(glyph, palette, Modifier.size(20.dp).alpha(if (control.enabled) 1f else .55f))
                            }
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
                        val remaining = page.remainingCooldowns[option.name]?.takeUnless { option.enabled }
                        val selected = pendingOption?.id == option.id
                        val colors = FilterChipDefaults.filterChipColors()
                        val keepColors = keepActionColors && option.enabled
                        androidx.compose.material3.FilterChip(
                            selected = selected,
                            onClick = { onChooseOption(option) },
                            enabled = !busy && option.enabled,
                            colors = if (keepColors) colors.copy(disabledContainerColor = colors.containerColor,
                                disabledSelectedContainerColor = colors.selectedContainerColor,
                                disabledLabelColor = if (selected) colors.selectedLabelColor else colors.labelColor) else colors,
                            border = FilterChipDefaults.filterChipBorder(enabled = option.enabled && (!busy || keepColors), selected = selected),
                            leadingIcon = {
                                BattleActionArtwork(option, Modifier.size(22.dp).alpha(if (option.enabled) 1f else .55f))
                            },
                            label = {
                                val name = option.displayName()
                                Text(
                                    if (remaining != null) name + " · " + stringResource(R.string.game_ui_cd_left, remaining)
                                    else name,
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
                        selected = panel == item,
                        onClick = { onPanelSelected(item) },
                        enabled = canBrowse && control.enabled,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = if (panel == item) MaterialTheme.colorScheme.secondaryContainer
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
            if (autoAvailable) BattleAutoButton(running, canStart, onToggleAuto, colors = actionColors)
        }
    }
}

@Composable
private fun BattleAutoButton(
    running: Boolean,
    canStart: Boolean,
    onToggleAuto: () -> Unit,
    colors: androidx.compose.material3.ButtonColors,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onToggleAuto,
        enabled = running || canStart,
        colors = colors,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
        Text(stringResource(if (running) R.string.jpx_pause else R.string.jpx_auto_run),
            Modifier.padding(start = 8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BattleOptionPanel(
    title: String,
    options: List<BattleActionOption>,
    remainingCooldowns: Map<String, Int>,
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
                if (group.isNotBlank() && group != "Items") item(key = "group-$group") {
                    Text(battleActionGroupText(group), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall)
                }
                items(entries, key = { "option-${it.id}" }) { option ->
                    val detailCost = option.costText(includeCooldown = true)
                    BattleOptionRow(option, busy, remainingCooldowns[option.name]?.takeUnless { option.enabled },
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
    else -> gameText(group)
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
            androidx.compose.material3.AssistChip(onClick = { onDetail(detail) },
                leadingIcon = { BattleEffectBadge(effect) }, label = {
                Text(gameText(effect.name))
            }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable
private fun BattleOptionRow(option: BattleActionOption, busy: Boolean, remaining: Int?, onSelect: () -> Unit, onDetail: () -> Unit) {
    val displayName = option.displayName()
    val cost = option.costText()
    val cooldown = remaining?.let { stringResource(R.string.game_ui_cd_left, it) }
    val meta = listOfNotNull(cost.takeIf(String::isNotBlank), cooldown).joinToString(" · ")
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clickable(enabled = !busy && option.enabled, role = Role.Button, onClick = onSelect)
                    .heightIn(min = 52.dp).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BattleActionArtwork(option, Modifier.size(36.dp).alpha(if (option.enabled) 1f else .55f))
                Column(Modifier.weight(1f)) {
                    Text(displayName, style = MaterialTheme.typography.titleSmall,
                        color = if (option.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (option.description.isNotBlank() || option.costText(true).isNotBlank()) {
                androidx.compose.material3.IconButton(onClick = onDetail) {
                    Icon(Icons.Filled.Info, stringResource(R.string.game_ui_named_details, displayName),
                        Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BattleActionOption.displayName(): String = if (mode == "items") gameText(name) else gameActionText(name)

@Composable
private fun BattleActionOption.costText(includeCooldown: Boolean = false): String = listOfNotNull(
    manaCost?.takeIf { it > 0 }?.let { stringResource(R.string.game_ui_mp_cost, it) },
    chargeCost?.takeIf { it > 0 }?.let { stringResource(R.string.game_ui_charge_cost, it) },
    if (includeCooldown) cooldown?.takeIf { it > 0 }?.let { stringResource(R.string.game_ui_cooldown_turns, it) } else null,
).joinToString(" · ")

private data class BattleDetail(val title: String, val description: String, val meta: String, val action: BattleActionOption? = null)

@Composable
private fun com.mitahv.mobile.model.BattleEffect.detail() = BattleDetail(name, description, when (duration) {
    "autocast" -> stringResource(R.string.game_ui_autocast_duration)
    "permanent" -> stringResource(R.string.game_ui_permanent_duration)
    "decaying" -> stringResource(R.string.game_ui_decaying_duration)
    else -> duration.toIntOrNull()?.let { stringResource(R.string.game_ui_effect_duration, it) } ?: duration
})

private enum class BattlePanel(val labelRes: Int, val icon: ImageVector, val control: String) {
    Attack(R.string.game_ui_attack, Icons.Filled.SportsMartialArts, "attack"),
    Skill(R.string.game_ui_skills, Icons.Filled.Bolt, "skill"),
    Magic(R.string.game_ui_magic, Icons.Filled.AutoFixHigh, "skill"),
    Items(R.string.game_ui_items, Icons.Filled.Inventory2, "items"),
}
