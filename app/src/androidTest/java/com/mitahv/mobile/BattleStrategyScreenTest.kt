package com.mitahv.mobile

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mitahv.mobile.data.JpxImport
import com.mitahv.mobile.data.JpxLibrary
import com.mitahv.mobile.i18n.ProvideLocalizedText
import com.mitahv.mobile.model.BattleScene
import com.mitahv.mobile.model.BattleStyle
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
        val node = generateSequence(find(label)) { it.parent }.first { it.isClickable }
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun await(message: () -> String = { "Timed out waiting for the strategy screen" }, check: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 6000
        do { if (check()) return; SystemClock.sleep(80) } while (SystemClock.elapsedRealtime() < until)
        fail(message())
    }
}
