package com.mitahv.mobile

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mitahv.mobile.data.*
import com.mitahv.mobile.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal data class BattleSessionState(
    val request: RequestState,
    val loading: Boolean = false,
    val nativeBusy: Boolean = false,
    val error: String? = null,
    val needsRefresh: Boolean = false,
) {
    val page: BattlePage? get() = when (request) {
        is RequestState.BattleReady -> request.page
        is RequestState.BattleBusy -> request.page
        else -> null
    }
    val ready get() = !loading && !nativeBusy && error == null && !needsRefresh && request is RequestState.BattleReady
    val interrupted get() = page?.isComplete != false || nativeBusy || error != null || needsRefresh
}

internal class RuleRunner {
    var running by mutableStateOf(false); private set
    var notice by mutableStateOf<RuleTurn?>(null); private set
    private var submitted: Any? = null
    private var encounterKey: Any? = null
    private var round: Int? = null
    private var floor: Int? = null
    var generation by mutableLongStateOf(0L); private set

    fun start(page: BattlePage) {
        encounterKey = page.encounterKey
        round = page.round.current
        floor = page.round.floor
        generation++
        notice = null
        running = true
    }

    fun ownsRound(page: BattlePage) =
        page.encounterKey === encounterKey && page.round.current == round && page.round.floor == floor

    fun pause() { running = false }
    fun accept(key: Any, result: RuleTurn, submit: (BattleAction) -> Unit) {
        if (!running) return
        if (submitted === key) { pause(); return }
        if (result is RuleTurn.Submit) { submitted = key; submit(result.action) }
        else { notice = result; pause() }
    }
}

@Composable
internal fun RunBattleTurns(runner: RuleRunner, rules: BattleRules?,
    world: String, scene: BattleScene?, loadout: BattleLoadout?, playerLevel: Int?,
    session: () -> BattleSessionState, submit: (BattlePage, BattleAction) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val monsters = remember(world, rules, context) {
        if (rules?.needsMonsterInfo == true) BattleMonsterLookup(BattleMonsterDatabase.shared(context), world, scope) else null
    }
    val scripts = remember(context, rules, world) { BattleScriptSandbox(context) }
    DisposableEffect(scripts) { onDispose { scripts.close() } }
    val latestSubmit by rememberUpdatedState(submit)
    val latestSession by rememberUpdatedState(session)
    LaunchedEffect(monsters) {
        if (monsters != null) snapshotFlow { latestSession().page }.collect { page ->
            if (page != null && !page.isComplete) monsters.start(page)
        }
    }
    DisposableEffect(runner, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) runner.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); runner.pause() }
    }
    LaunchedEffect(runner, lifecycle, rules, scene, loadout, playerLevel, world) {
        if (rules == null) return@LaunchedEffect
        // Acknowledgements can wake the runner before the HUD renders the next frame.
        withContext(Dispatchers.Main.immediate) {
            snapshotFlow {
                if (runner.running) runner.generation to latestSession() else null
            }.collectLatest { active ->
                val (generation, state) = active ?: return@collectLatest
                val page = state.page
                if (state.interrupted || page == null || !runner.ownsRound(page) ||
                    !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    runner.pause()
                    return@collectLatest
                }
                if (!state.ready) return@collectLatest
                val decision = withContext(Dispatchers.Default) {
                    val info = monsters?.start(page)?.filterNotNull()?.first().orEmpty()
                    rules.next(page, world, scene, loadout?.school, playerLevel, scripts::matchesName, info) { code, ctx ->
                        scripts.evaluate(code, ctx, loadout)
                    }
                }
                val current = latestSession()
                if (current.ready && current.page?.turnKey === page.turnKey && runner.generation == generation &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    runner.accept(page.turnKey, decision) { latestSubmit(page, it) }
                }
            }
        }
    }
}

@Composable
internal fun RuleRunNotice(result: RuleTurn) {
    val message = when (result) {
        RuleTurn.NoAction -> stringResource(R.string.jpx_no_action)
        is RuleTurn.Stop -> stringResource(R.string.jpx_halt_rule, result.number, result.message.ifBlank { stringResource(R.string.jpx_stopped) })
        is RuleTurn.ScriptError -> stringResource(R.string.jpx_script_error, result.number, result.reason)
        is RuleTurn.Unsupported -> stringResource(R.string.jpx_unavailable_rule, result.number, fieldLabel(result.field))
        is RuleTurn.Submit -> return
    }
    Text(message, Modifier.padding(horizontal = 16.dp, vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
}
