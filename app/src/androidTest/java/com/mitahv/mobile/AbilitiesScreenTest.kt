package com.mitahv.mobile

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mitahv.mobile.i18n.ProvideLocalizedText
import com.mitahv.mobile.model.*
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AbilitiesScreenTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val ctx = instrument.targetContext
    private val zh get() = ctx.resources.configuration.locales[0].language == "zh"
    private val url = HvRoute.Abilities.url(HvWorld.Persistent)
    private val unlock = NativeAction(url, fields = listOf("unlock_ability" to "1"), label = "解锁")
    private val upgrade = unlock.copy(label = "升级")
    private val trees = listOf("general", "onehanded", "cloth", "elemental", "unfamiliar")
    private val categories = trees.mapIndexed { index, id ->
        CharacterSectionLink("Category $index", NativeAction(url.newBuilder().addQueryParameter("tree", id).build(), "GET", label = id),
            selected = false, abilityTreeId = id)
    }
    private fun row(id: String, learned: Int, action: NativeAction? = null, req: CharacterAbilityUpgradeRequirements? = null): CharacterSectionRow {
        val tiers = (1..2).map { CharacterAbilityTier(it, if (it <= learned) CharacterAbilityTierState.Acquired else CharacterAbilityTierState.Locked) }
        return CharacterSectionRow("Ability $id", actions = listOfNotNull(action), ability = CharacterAbilityInfo(id, "r", learned > 0,
            upgradeCost = if (action != null) "1 AP" else "", tiers = tiers, description = "Inspect only", upgradeRequirements = req))
    }
    private fun page(index: Int = 0, learned: Int = 0) = CharacterSectionsPage(HvRoute.Abilities,
        summary = listOf("ABILITY POINTS: 3", "MASTERY POINTS: 1"),
        navigation = categories.mapIndexed { i, link -> if (index == i) link.copy(selected = true, action = null) else link },
        groups = listOf(CharacterSectionGroup("", listOf(row("1", learned, if (learned == 0) unlock else upgrade), row("2", 2),
            row("3", 0, req = CharacterAbilityUpgradeRequirements(10, 5, 4, 3)), row("4", 1)))))

    @Test fun inspectWithoutSpendingAndAdvanceFromTheListOnlyOnServerAction() {
        val page = mutableStateOf(page())
        val busy = mutableStateOf(false)
        val calls = CopyOnWriteArrayList<NativeAction>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { ProvideLocalizedText { MaterialTheme {
                AbilitiesScreen(page.value, busy.value, { calls += it; busy.value = true })
            } } } }
            find(ctx.getString(R.string.ability_ui_maxed), exact = false)
            find(ctx.getString(R.string.ability_ui_need_points, 2), exact = false)
            find(ctx.getString(R.string.ability_ui_need_level, 10), exact = false)
            find(ctx.getString(R.string.ability_ui_upgrade_unavailable), exact = false)
            click("Ability 1")
            find("Inspect only"); click(ctx.getString(R.string.game_ui_close)); assertTrue(calls.isEmpty())
            val cost = ctx.getString(R.string.ability_ui_point_cost, "1")
            val first = "Ability 1, ${if (zh) "解锁" else "Unlock"} · $cost"
            val next = "Ability 1, ${if (zh) "升级" else "Upgrade"} · $cost"
            click(first)
            await("disabled action while awaiting response") { !control(first).isEnabled }
            assertEquals(listOf(unlock), calls.toList())
            scenario.onActivity { page.value = page(learned = 1); busy.value = false }
            find(ctx.getString(R.string.ability_ui_rank, 1, 2)); click(next)
            await("upgrade") { calls.toList() == listOf(unlock, upgrade) }
        }
    }

    @Test fun switchInlineOrFromDirectoryAndReturnFromSlotsWithoutAnotherRequest() {
        val page = mutableStateOf(page())
        val calls = CopyOnWriteArrayList<NativeAction>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { ProvideLocalizedText { MaterialTheme {
                AbilitiesScreen(page.value, false, { action ->
                    calls += action
                    page.value = page(trees.indexOf(action.url.queryParameter("tree")))
                })
            } } } }
            click("Category 1")
            click(ctx.getString(R.string.ability_ui_tree))
            find(ctx.getString(R.string.hv_settings_weapons)); find(ctx.getString(R.string.hv_settings_armor))
            click("Category 2")
            click(ctx.getString(R.string.ability_ui_slots)); find(ctx.getString(R.string.ability_ui_major))
            click(ctx.getString(R.string.app_back)); find("Category 2")
            click("Category 2") // already selected
            assertEquals(listOf(categories[1].action, categories[2].action), calls.toList())
        }
    }

    private fun nodes(n: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
        yield(n); for (i in 0 until n.childCount) n.getChild(i)?.let { yieldAll(nodes(it)) }
    }
    private fun await(label: String, check: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 6000
        do { if (check()) return; SystemClock.sleep(60) } while (SystemClock.elapsedRealtime() < until)
        fail("Missing $label among " + instrument.uiAutomation.rootInActiveWindow?.let { root -> nodes(root).mapNotNull { it.text ?: it.contentDescription }.toList() })
    }
    private fun find(label: String, exact: Boolean = true): AccessibilityNodeInfo {
        var result: AccessibilityNodeInfo? = null
        await(label) {
            result = instrument.uiAutomation.rootInActiveWindow?.let { root -> nodes(root).firstOrNull { node ->
                node.isVisibleToUser && listOfNotNull(node.text, node.contentDescription).any { value ->
                    if (exact) value.toString().lines().contains(label) else label in value.toString()
                }
            } }; result != null
        }
        return requireNotNull(result)
    }
    private fun control(label: String): AccessibilityNodeInfo = generateSequence(find(label)) { it.parent }.first { it.isClickable }
    private fun click(label: String) = await("click $label") {
        val node = control(label)
        node.isEnabled && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}
