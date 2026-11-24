package com.mitahv.mobile

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.Lifecycle
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

    @Test fun battleRefreshIsAvailableWithoutAnErrorAndWaitsForInFlightRequests() {
        val page = BattlePage("fixture", emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), "", 0f)
        val state = mutableStateOf<RequestState>(RequestState.BattleReady(page))
        val loading = mutableStateOf(false)
        val refreshes = java.util.concurrent.atomic.AtomicInteger()
        val label = context.getString(R.string.app_refresh_page)
        fun enabled() = generateSequence(find(label)) { it.parent }.any { it.isClickable && it.isEnabled }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { ProvideLocalizedText { MaterialTheme {
                BattleSessionScreen(null, state.value, null, "Isekai", BattleStrategy(null, null, null, true),
                    {}, true, false, loading.value, false, null, false, 0, { byteArrayOf() },
                    onRetry = { refreshes.incrementAndGet(); loading.value = true }, onRiddleAction = {},
                    onSubmitBattleAction = { _, _ -> error("Refresh must not submit a battle action") },
                    onContinue = {}, onLeaveBattle = {})
            } } } }
            click(label)
            await { refreshes.get() == 1 && !enabled() }
            scenario.onActivity { loading.value = false; state.value = RequestState.BattleBusy(page) }
            await { !enabled() }
            scenario.onActivity { state.value = RequestState.BattleReady(page) }
            await { enabled() }
            click(label)
            await { refreshes.get() == 2 }
        }
    }

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
            val refresh = context.getString(R.string.app_refresh_page)
            fun refreshEnabled() = generateSequence(find(refresh)) { it.parent }.any { it.isClickable && it.isEnabled }
            find(start); assertTrue(calls.isEmpty()); click(start); await { calls.size == 1 }
            val second = initial.copy(turnKey = Any())
            scenario.onActivity { state.value = RequestState.BattleReady(second) }; await { calls.size == 2 }
            await { !refreshEnabled() } // Confirm Busy was rendered before replaying the last acknowledgement.
            scenario.onActivity { state.value = RequestState.BattleReady(second) } // Replay must not submit twice.
            find(start); assertEquals(2, calls.size)
            scenario.onActivity { state.value = RequestState.BattleReady(initial.copy(turnKey = Any())) }
            click(start); await { calls.size == 3 }; click(pause)
            find(start)
            scenario.onActivity { state.value = RequestState.BattleReady(initial.copy(turnKey = Any())) }
            await { refreshEnabled() }; assertEquals(List(3) { BattleAction("attack", 7) }, calls.toList())
            click(start); await { calls.size == 4 }
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { state.value = RequestState.BattleReady(initial.copy(turnKey = Any())) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            find(start); await { refreshEnabled() }; assertEquals(4, calls.size)
            scenario.onActivity { state.value = RequestState.BattleReady(initial.copy(isComplete = true, turnKey = Any())) }
            find(context.getString(R.string.game_ui_return_to_challenge)); assertEquals(4, calls.size)
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

    @Test fun manualSpellTargetsTheChosenEnemyAndResetsOnANewTurn() {
        val spell = BattleActionOption("111", "Fixture bolt", "magic", 111, true)
        val shortcut = spell.copy(id = "qb11", name = "Quick bolt")
        val heal = BattleActionOption("311", "Fixture heal", "magic", 311, false)
        val enemy = BattleEnemy(1, "First foe", "1", 1f, targetable = true)
        val page = BattlePage(
            token = "fixture", playerVitals = emptyList(), enemies = listOf(enemy, enemy.copy(target = 7, name = "Second foe")),
            quickActions = listOf(shortcut), skills = emptyList(), spells = listOf(spell, heal), items = emptyList(),
            log = emptyList(), completion = "", experienceBarWidth = 0f,
            controls = listOf(BattleControl("attack", true), BattleControl("skill", true)),
        )
        val current = mutableStateOf(page)
        val calls = java.util.concurrent.CopyOnWriteArrayList<BattleAction>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { ProvideLocalizedText { MaterialTheme {
                BattleScreen(current.value, false, { calls.add(it) }, false, false, false, false, {}, null, {}, {})
            } } } }
            val magic = context.getString(R.string.game_ui_magic)
            fun nextTurn() {
                val round = (current.value.round.current ?: 1) + 1
                scenario.onActivity {
                    current.value = current.value.copy(turnKey = Any(), round = BattleRoundFacts(current = round, total = 5))
                }
                find(context.getString(R.string.game_ui_battle_round, round.toString(), "5"))
            }

            click(magic)
            click(spell.name)
            val prompt = find(context.getString(R.string.game_ui_choose_target, spell.name))
            assertFalse("The target prompt must not cancel the spell when tapped",
                generateSequence(prompt) { it.parent }.any { it.isClickable })
            assertTrue(calls.isEmpty())
            click("Second foe")
            await { calls.size == 1 }
            assertEquals(BattleAction("magic", 7, 111), calls.last())

            nextTurn()
            click(shortcut.name)
            find(context.getString(R.string.game_ui_choose_target, shortcut.name))
            click(shortcut.name)
            click("First foe")
            await { calls.size == 2 }
            assertEquals(BattleAction("magic", 1, 111), calls.last())

            nextTurn()
            click("Second foe")
            await { calls.size == 3 }
            assertEquals(BattleAction("attack", 7), calls.last())
            nextTurn()
            click(magic)
            click(heal.name)
            await { calls.size == 4 }
            assertEquals(BattleAction("magic", skill = 311), calls.last())
            nextTurn()
            click(shortcut.name)
            click(context.getString(R.string.game_ui_cancel_selection))
            click("First foe")
            await { calls.size == 5 }
            assertEquals(BattleAction("attack", 1), calls.last())
        }
    }

    @Test fun consecutiveRoundsKeepTheFieldAndRequireAnExplicitStartForEachWave() {
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
        val autoNext = mutableStateOf(true)
        val continuation = BattleContinuation("Next round")
        fun completed(page: BattlePage) = page.copy(isComplete = true, continuation = continuation, turnKey = Any(),
            enemies = emptyList(), controls = emptyList(), completionLines = listOf("Intermediate result"))
        fun nextRound(number: Int) = initial.copy(enemies = listOf(enemy.copy(target = number, name = "Round $number foe")),
            turnKey = Any(), round = BattleRoundFacts(current = number, total = 5))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { ProvideLocalizedText { MaterialTheme {
                BattleSessionScreen(
                    playerLevel = null, state = state.value, loadout = null, world = "Isekai", strategy = strategy,
                    onSettings = {}, settingsEnabled = true, autoNextRound = autoNext.value, loading = false, nativeBusy = false,
                    error = null, needsRefresh = false, responseRevision = 0, loadImage = { byteArrayOf() },
                    onRetry = {}, onRiddleAction = {},
                    onSubmitBattleAction = { page, action -> calls.add(action); state.value = RequestState.BattleBusy(page) },
                    onContinue = {
                        val page = (state.value as RequestState.BattleReady).page
                        assertEquals(continuation, it)
                        continuations.incrementAndGet()
                        state.value = RequestState.BattleBusy(page)
                    },
                    onLeaveBattle = {},
                )
            } } } }
            val auto = context.getString(R.string.jpx_auto_run)
            val pause = context.getString(R.string.jpx_pause)
            fun acknowledge(page: BattlePage) = scenario.onActivity { state.value = RequestState.BattleReady(page) }
            fun bounds(label: String) = android.graphics.Rect().also { find(label).getBoundsInScreen(it) }
            // Manual combat advances the round without starting an attack.
            val initialBounds = bounds(enemy.name)
            acknowledge(completed(initial))
            await { continuations.get() == 1 }
            await { generateSequence(find(enemy.name)) { it.parent }.none { it.isClickable && it.isEnabled } }
            assertEquals(initialBounds, bounds(enemy.name))
            assertFalse(flattened(instrument.uiAutomation.rootInActiveWindow).any { it.isVisibleToUser && it.text == "Intermediate result" })
            val second = nextRound(2)
            acknowledge(second)
            find("Round 2 foe"); find(auto); assertTrue(calls.isEmpty())

            // Completing a round stops even an explicitly started strategy.
            click(auto); await { calls.size == 1 }; find(pause)
            acknowledge(completed(second)); await { continuations.get() == 2 }
            find("Round 2 foe"); find(auto)
            val third = nextRound(3)
            acknowledge(third); find("Round 3 foe"); find(auto)
            assertEquals(listOf(BattleAction("attack", 2)), calls.toList())
            click(auto); await { calls.size == 2 }
            assertEquals(listOf(BattleAction("attack", 2), BattleAction("attack", 3)), calls.toList())

            // The strategy also stays idle after an in-flight action is paused.
            click(pause); find(auto)
            acknowledge(completed(third)); await { continuations.get() == 3 }
            val fourth = nextRound(4)
            acknowledge(fourth); find("Round 4 foe"); assertEquals(2, calls.size)

            scenario.onActivity { autoNext.value = false }
            click(auto); await { calls.size == 3 }
            acknowledge(completed(fourth)); find("Intermediate result")
            assertEquals(3, continuations.get())
            click(continuation.label); await { continuations.get() == 4 }
            val last = nextRound(5)
            acknowledge(last); find("Round 5 foe"); find(auto); assertEquals(3, calls.size)
            scenario.onActivity { autoNext.value = true }
            click(auto); await { calls.size == 4 }
            acknowledge(last.copy(isComplete = true, completionLines = listOf("Final result"), turnKey = Any()))
            find("Final result"); find(context.getString(R.string.game_ui_return_to_challenge))
            assertEquals(4, continuations.get()); assertEquals(4, calls.size)
        }
    }

    @Test fun crowdedBattleShowsRoundAndTargetsBeforeExpandingPlayerEffects() {
        val slow = BattleEffect("Freezing Limbs", "Reduces attack speed", "3", "coldslow")
        val longName = "Enemy with a very long name that exceeds the available width"
        val enemies = (1..10).map { BattleEnemy(it, if (it == 3) longName else "Enemy $it", "20", .75f, targetable = it <= 7,
            effects = if (it == 1) listOf(slow) else emptyList()) }
        val effects = (1..18).map { BattleEffect("Effect $it", "Effect details $it", "5", "regen") }
        val page = BattlePage(
            token = "fixture", playerVitals = listOf(BattleVital("Health", "2400", .8f), BattleVital("Mana", "560", 1f)), enemies = enemies,
            quickActions = listOf(BattleActionOption("qb1", "Fixture heal", "magic", 311, false, enabled = false)),
            skills = emptyList(), spells = emptyList(), items = emptyList(), remainingCooldowns = mapOf("Fixture heal" to 2),
            log = emptyList(), completion = "", experienceBarWidth = 0f,
            round = BattleRoundFacts(current = 3, total = 12), playerEffects = effects,
            controls = listOf("attack", "skill", "items", "spirit", "defend", "focus").map { BattleControl(it, true) },
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { ProvideLocalizedText { MaterialTheme {
                BattleScreen(page, false, {}, false, false, false, false, {}, null, {}, {})
            } } } }
            find(context.getString(R.string.game_ui_battle_round, "3", "12"))
            find(context.getString(R.string.game_ui_enemy_targets, 10, 7))
            find("Enemy 1")
            find("Enemy 4")
            fun nameHeight(label: String): Int = android.graphics.Rect().let { bounds ->
                find(label).getBoundsInScreen(bounds)
                bounds.height()
            }
            assertEquals(nameHeight("Enemy 4"), nameHeight(longName))
            find("Fixture heal · " + context.getString(R.string.game_ui_cd_left, 2))
            assertFalse(flattened(instrument.uiAutomation.rootInActiveWindow).any { it.text == "Effect 1" && it.isVisibleToUser })
            click(context.getString(R.string.game_ui_player_effect_count, 18))
            find("Effect 1")
            find("Effect details 1")
            click(context.getString(R.string.game_ui_close))
            click(context.getString(R.string.game_ui_named_details, "Enemy 1"))
            find("Freezing Limbs")
            click(context.getString(R.string.game_ui_close))
            find("Enemy 1")
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
