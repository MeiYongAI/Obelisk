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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal class RuleRunner {
    var running by mutableStateOf(false); private set
    var notice by mutableStateOf<RuleTurn?>(null); private set
    private var submitted: Any? = null
    fun start() { notice = null; running = true }
    fun pause() { running = false }
    fun accept(key: Any, result: RuleTurn, submit: (BattleAction) -> Unit) {
        if (!running) return
        if (submitted === key) { pause(); return }
        if (result is RuleTurn.Submit) { submitted = key; submit(result.action) }
        else { notice = result; pause() }
    }
}

@Composable
internal fun RunBattleTurns(runner: RuleRunner, rules: BattleRules?, page: BattlePage?, world: String, scene: BattleScene?,
    school: SpellSchool?, playable: Boolean, interrupted: Boolean, submit: (BattlePage, BattleAction) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestSubmit by rememberUpdatedState(submit)
    DisposableEffect(runner, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) runner.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); runner.pause() }
    }
    LaunchedEffect(runner, runner.running, page?.turnKey, playable, interrupted, rules, scene, school, world) {
        if (interrupted) { runner.pause(); return@LaunchedEffect }
        if (!runner.running || !playable || page == null || rules == null) return@LaunchedEffect
        delay(300) // Leave a pause affordance between acknowledged turns, never poll the server.
        val decision = withContext(Dispatchers.Default) { rules.next(page, world, scene, school) }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) runner.accept(page.turnKey, decision) { latestSubmit(page, it) }
        else runner.pause()
    }
}

@Composable
internal fun RuleRunNotice(result: RuleTurn) {
    val message = when (result) {
        RuleTurn.NoAction -> stringResource(R.string.jpx_no_action)
        is RuleTurn.Stop -> stringResource(R.string.jpx_halt_rule, result.number, result.message.ifBlank { stringResource(R.string.jpx_stopped) })
        is RuleTurn.Unsupported -> stringResource(R.string.jpx_unavailable_rule, result.number, fieldLabel(result.field))
        is RuleTurn.Submit -> return
    }
    Text(message, Modifier.padding(horizontal = 16.dp, vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
}
