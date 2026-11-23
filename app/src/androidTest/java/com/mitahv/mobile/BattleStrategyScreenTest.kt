package com.mitahv.mobile

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mitahv.mobile.data.JpxImport
import com.mitahv.mobile.data.JpxLibrary
import com.mitahv.mobile.i18n.ProvideLocalizedText
import com.mitahv.mobile.model.*
import com.mitahv.mobile.data.BattleStrategy
import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

@RunWith(AndroidJUnit4::class)
class BattleStrategyScreenTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val context = instrument.targetContext

    @Test fun assigningGeneralToItemWorldUpdatesTheRunningBattleAndManualPinSurvivesTheSwitchBack() {
        val filename = "strategy-ui-${UUID.randomUUID()}.db"
        try {
            JpxLibrary.Database(context, filename).use { db ->
                val library = JpxLibrary(db, "fixture", "Isekai")
                val raw = """{"version":1,"Staff_Item":{"rules":[{"type":"stop"}]},"Staff_General":{"rules":[{"type":"stop"}]}}"""
                library.save(JpxImport.read(raw.byteInputStream(), "fixture", library.world))
                val general = library.catalog().entries.single { it.mode == "Staff_General" }
                val item = library.catalog().entries.single { it.mode == "Staff_Item" }
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity { activity -> activity.setContent {
                        ProvideLocalizedText { MaterialTheme {
                            val strategy = rememberStrategy(library, BattleStyle.Staff, BattleScene.Item, true)
                            BattleConfigsScreen(library, strategy) {}
                        } }
                    } }
                    fun text(id: Int) = context.getString(id)
                    find(text(R.string.jpx_reason_exact))
                    click(text(R.string.jpx_routes))
                    click(text(R.string.jpx_scene_item))
                    click(context.getString(R.string.jpx_mode_label, text(R.string.strategy_staff), text(R.string.jpx_scene_general)))
                    await { library.catalog().routes["Staff_Item"] == general.id }
                    click(text(R.string.app_back))
                    find(text(R.string.jpx_reason_assigned))
                    click(text(R.string.jpx_auto))
                    await { library.catalog().manualId == general.id }
                    // Other battlefield assignments can change without replacing a manual pin.
                    library.assign(BattleStyle.Staff, BattleScene.Item, item.id)
                    find(text(R.string.jpx_manual))
                    click(text(R.string.jpx_auto))
                    find(text(R.string.jpx_reason_assigned))
                    assertEquals(item.id, library.strategy(BattleStyle.Staff, BattleScene.Item, true).match?.entry?.id)
                }
            }
        } finally { context.deleteDatabase(filename) }
    }

    @Test fun dragAndSwitchChangeOnlyTheDraftUntilApplied() {
        val filename = "editor-${UUID.randomUUID()}.db"
        try { JpxLibrary.Database(context, filename).use { db ->
            val library = JpxLibrary(db, "fixture", "Isekai")
            val mode = "Staff_General"
            val raw = """{"version":1,"$mode":{"rules":[{"type":"stop"},{"type":"target"},{"type":"normalAttack"}]},"Staff_Item":{}}"""
            assertEquals(1, library.save(JpxImport.read(raw.byteInputStream(), "battle.json", library.world)))
            val entry = library.catalog().entries.single()
            fun rules(published: Boolean = false) = com.mitahv.mobile.data.JpxDocument(library.load(entry.id, published)).rules(mode,"rules").map { it.asJsonObject }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    ProvideLocalizedText { MaterialTheme { BattleConfigsScreen(library, rememberStrategy(library, null, null, false)) {} } }
                } }
                click(context.getString(R.string.jpx_mode_label, context.getString(R.string.strategy_staff), context.getString(R.string.jpx_scene_general)))
                fun handle(n: Int) = find(context.getString(R.string.jpx_reorder, n)).let { node -> android.graphics.Rect().also(node::getBoundsInScreen) }
                find("1"); find("3")
                val from = handle(1); val to = handle(3)
                assertFalse(instrument.uiAutomation.rootInActiveWindow.let(::flattened).any { it.text == context.getString(R.string.jpx_import) })
                instrument.uiAutomation.executeShellCommand("input swipe ${from.centerX()} ${from.centerY()} ${to.centerX()} ${to.centerY()} 450").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
                await { rules().last()["type"].asString == "stop" }
                click(context.getString(R.string.jpx_enable_row, 3))
                await { rules().last()["disabled"]?.asBoolean == true }
                assertEquals("stop", rules(true).first()["type"].asString)
                click(context.getString(R.string.jpx_apply)); await { instrument.uiAutomation.rootInActiveWindow?.let { flattened(it).any { n -> n.text?.contains(context.getString(R.string.game_ui_cancel)) == true } } == true }
                click(context.getString(R.string.jpx_apply)); await { rules(true).last()["disabled"]?.asBoolean == true }
            }
        } } finally { context.deleteDatabase(filename) }
    }

    @Test fun battleOnlySubmitsAfterStartAndWaitsForFreshAcknowledgedTurn() = verifyTurnLoop("""{"type":"target"},{"type":"normalAttack"}""")

    @Test fun importedScriptUsesTheSameAcknowledgedTurnLoop() = verifyTurnLoop(
        """{"type":"kamisamaniNattaHi","code":"return jpx.doAttackGoNext(jpx.monstersObj.activeMonsters[0])"}""")

    private fun verifyTurnLoop(rows: String) {
        val draft = JpxImport.read("""{"version":1,"Staff_General":{"rules":[$rows]}}""".byteInputStream(), "fixture", "Isekai")
        val enemy = BattleEnemy(7,"Fixture", "1", 1f, targetable = true)
        val initial = BattlePage("fixture", emptyList(), listOf(enemy), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), "", 0f,
            controls = listOf(BattleControl("attack", true)))
        val strategy = BattleStrategy(null, BattleStyle.Staff, BattleScene.Item, true, published = draft)
        val state = mutableStateOf<RequestState>(RequestState.BattleReady(initial))
        val calls = java.util.concurrent.CopyOnWriteArrayList<BattleAction>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { ProvideLocalizedText { MaterialTheme {
                BattleSessionScreen(null, state.value, null, "Isekai", strategy, {}, true, false, false, false, null, false, 0,
                    { byteArrayOf() }, {}, {}, { page, action -> calls.add(action); state.value = RequestState.BattleBusy(page) }, {}, {})
            } } } }
            val start = context.getString(R.string.jpx_auto_run); val pause = context.getString(R.string.jpx_pause)
            find(start); assertTrue(calls.isEmpty()); click(start); await { calls.size == 1 }
            val second = initial.copy(turnKey = Any())
            scenario.onActivity { state.value = RequestState.BattleReady(second) }; await { calls.size == 2 }
            find(pause); SystemClock.sleep(180) // Let the busy composition commit before replaying the same page.
            scenario.onActivity { state.value = RequestState.BattleReady(second) } // Replay must not submit twice.
            SystemClock.sleep(650); find(start); assertEquals(2, calls.size)
            scenario.onActivity { state.value = RequestState.BattleReady(initial.copy(turnKey = Any())) }
            click(start); await { calls.size == 3 }; click(pause)
            scenario.onActivity { state.value = RequestState.BattleReady(initial.copy(turnKey = Any())) }
            find(start); SystemClock.sleep(500); assertEquals(List(3) { BattleAction("attack", 7) }, calls.toList())
            scenario.onActivity { state.value = RequestState.BattleReady(initial.copy(isComplete = true, turnKey = Any())) }
            SystemClock.sleep(500); assertEquals(3, calls.size)
        }
    }

    @Test fun battleActionsRemainAccessibleBesideAutoControl() {
        val page = BattlePage(
            token = "fixture", playerVitals = emptyList(), enemies = emptyList(), quickActions = emptyList(),
            skills = listOf(BattleActionOption("skill-1", "Fixture skill", "skill", 1, false)),
            spells = listOf(BattleActionOption("spell-1", "Fixture spell", "magic", 2, false)),
            items = listOf(BattleActionOption("item-1", "Fixture item", "items", 3, false)),
            log = emptyList(), completion = "", experienceBarWidth = 0f,
            controls = listOf(BattleControl("attack", true), BattleControl("skill", true), BattleControl("items", true)),
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { ProvideLocalizedText { MaterialTheme {
                BattleScreen(page, false, {}, false, true, false, true, {}, null, {}, {})
            } } } }
            listOf(R.string.game_ui_attack, R.string.game_ui_skills, R.string.game_ui_magic, R.string.game_ui_items)
                .forEach { find(context.getString(it)) }
            find(context.getString(R.string.jpx_auto_run))
            click(context.getString(R.string.game_ui_skills)); find("Fixture skill")
            click(context.getString(R.string.game_ui_items)); find("Fixture item")
        }
    }

    @Test fun autoRunnerStopsAtRoundBoundaryAndNeedsManualRestart() {
        val draft = JpxImport.read(
            """{"version":1,"Staff_General":{"rules":[{"type":"target"},{"type":"normalAttack"}]}}""".byteInputStream(),
            "fixture", "Isekai",
        )
        val enemy = BattleEnemy(7, "Fixture", "1", 1f, targetable = true)
        val initial = BattlePage("fixture", emptyList(), listOf(enemy), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), "", 0f,
            controls = listOf(BattleControl("attack", true)))
        val strategy = BattleStrategy(null, BattleStyle.Staff, BattleScene.General, true, published = draft)
        val state = mutableStateOf<RequestState>(RequestState.BattleReady(initial))
        val calls = java.util.concurrent.CopyOnWriteArrayList<BattleAction>()
        val continuations = java.util.concurrent.atomic.AtomicInteger()
        val completed = initial.copy(isComplete = true, continuation = BattleContinuation("下一轮"), completion = "victory")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { ProvideLocalizedText { MaterialTheme {
                BattleSessionScreen(
                    playerLevel = null, state = state.value, loadout = null, world = "Isekai", strategy = strategy,
                    onSettings = {}, settingsEnabled = true, autoNextRound = true, loading = false, nativeBusy = false,
                    error = null, needsRefresh = false, responseRevision = 0, loadImage = { byteArrayOf() },
                    onRetry = {}, onRiddleAction = {},
                    onSubmitBattleAction = { page, action -> calls.add(action); state.value = RequestState.BattleBusy(page) },
                    onContinue = { continuations.incrementAndGet(); state.value = RequestState.BattleBusy(completed) },
                    onLeaveBattle = {},
                )
            } } } }
            val auto = context.getString(R.string.jpx_auto_run)
            val pause = context.getString(R.string.jpx_pause)
            find(auto); click(auto); await { calls.size == 1 }
            scenario.onActivity { state.value = RequestState.BattleReady(completed) }
            await { continuations.get() == 1 }
            val nextRound = initial.copy(turnKey = Any(), round = BattleRoundFacts(current = 2), encounterKey = initial.encounterKey)
            scenario.onActivity { state.value = RequestState.BattleReady(nextRound) }
            find(auto); SystemClock.sleep(600)
            assertEquals("Automation must stop when the next round is loaded", 1, calls.size)
            click(auto); await { calls.size == 2 }; find(pause)
        }
    }

    private fun flattened(node: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
        yield(node)
        for (i in 0 until node.childCount) node.getChild(i)?.let { yieldAll(flattened(it)) }
    }

    private fun find(label: String): AccessibilityNodeInfo {
        var node: AccessibilityNodeInfo? = null
        var visible = emptyList<String>()
        await({ "Missing '$label' among $visible" }) {
            val nodes = instrument.uiAutomation.rootInActiveWindow?.let { flattened(it).toList() }.orEmpty()
            visible = nodes.mapNotNull { it.text?.toString() }
            node = nodes.firstOrNull { n -> n.isVisibleToUser && (n.contentDescription?.toString() == label ||
                n.text?.toString()?.lineSequence()?.any { line -> line == label || line.startsWith("$label · ") } == true) }
            node != null
        }
        return requireNotNull(node)
    }

    private fun click(label: String) {
        await({ "No clickable control for '$label'" }) {
            val root = instrument.uiAutomation.rootInActiveWindow ?: return@await false
            val control = flattened(root).filter { n -> n.isVisibleToUser && (n.contentDescription?.toString() == label ||
                n.text?.toString()?.lineSequence()?.any { it == label || it.startsWith("$label · ") } == true) }
                .firstNotNullOfOrNull { generateSequence(it) { child -> child.parent }.firstOrNull { parent -> parent.isClickable && parent.isEnabled } }
            control?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
    }

    private fun await(message: () -> String = { "Timed out waiting for the strategy screen" }, check: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 6000
        do { if (check()) return; SystemClock.sleep(80) } while (SystemClock.elapsedRealtime() < until)
        fail(message())
    }
}
