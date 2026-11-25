package com.mitahv.mobile

import android.content.res.Configuration
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mitahv.mobile.data.CharacterSectionsParser
import com.mitahv.mobile.i18n.ProvideGameTextScope
import com.mitahv.mobile.i18n.ProvideLocalizedText
import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvWorld
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EquipmentScreenTest {
    @Test fun unavailableOffhandAndEmptySlotFollowTheSelectedLanguage() {
        val blocked = "(unavailable with current mainhand)"
        val customName = "Fire Shield"
        val html = """
            <div id="eqch_outer"><div id="eqsb">
              <div class="eqb eqdisabled"><div>Off Hand</div><div class="eqempty">$blocked</div></div>
              <div class="eqb"><div>Helmet</div><div class="eqempty">(empty)</div></div>
              <div class="eqb"><div>Body</div><div data-eid="1">$customName</div></div>
            </div></div>
            <script>var dynjs_equip = {"1":{"n":"Average Leather Breastplate","t":"$customName"}};</script>
        """.trimIndent()
        val url = HvRoute.Equipment.url(HvWorld.Persistent)
        val page = CharacterSectionsParser.parse(html, HvRoute.Equipment, HvWorld.Persistent, url)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for ((locale, expected) in listOf(
                Locale.SIMPLIFIED_CHINESE to listOf("副手", "当前主手无法搭配副手装备", "空", customName),
                Locale.ENGLISH to listOf("Off Hand", blocked, "Empty", customName),
            )) {
                scenario.onActivity { activity ->
                    val config = Configuration(activity.resources.configuration).apply { setLocale(locale) }
                    val localized = activity.createConfigurationContext(config)
                    activity.setContent {
                        CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides config) {
                            ProvideLocalizedText { ProvideGameTextScope(url.toString()) { MaterialTheme {
                                EquipmentScreen(page, false, {}, {}, 0)
                            } } }
                        }
                    }
                }
                val deadline = SystemClock.elapsedRealtime() + 6000
                var shown: List<String>
                do {
                    shown = automation.rootInActiveWindow?.let { visibleText(it).toList() }.orEmpty()
                    if (shown.containsAll(expected)) break
                    SystemClock.sleep(60)
                } while (SystemClock.elapsedRealtime() < deadline)
                assertTrue("Expected $expected in $locale, saw $shown", shown.containsAll(expected))
            }
        }
    }

    private fun visibleText(node: AccessibilityNodeInfo): Sequence<String> = sequence {
        if (node.isVisibleToUser) node.text?.let { yieldAll(it.toString().lineSequence()) }
        for (i in 0 until node.childCount) node.getChild(i)?.let { yieldAll(visibleText(it)) }
    }
}
