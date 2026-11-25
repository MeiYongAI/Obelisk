package com.mitahv.mobile

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mitahv.mobile.data.HvAuthCookies
import com.mitahv.mobile.data.PersistentCookieJar
import com.mitahv.mobile.i18n.ProvideLocalizedText
import com.mitahv.mobile.model.HvRoute
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageNavigationTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val requests = CopyOnWriteArrayList<String>()

    @Test fun reopenEquipmentSlots() = reopen(HvRoute.Equipment, "equip_slot", "Back to equipment") { id, detail ->
        val url = "?s=Character&amp;ss=eq"
        if (id == null) {
            val slots = listOf("Main Hand", "Body").mapIndexed { index, name ->
                val n = index + 1
                """<div class="eqb" onclick="document.location='$url&amp;equip_slot=$n'"><div>$name</div><div>Entry $n</div></div>"""
            }.joinToString("")
            """<div id="eqch_outer"><div id="eqsb">$slots</div></div>"""
        } else """<form id="equipform" method="post"><div id="equipselect_outer"><div id="equipselect_left">
            <div class="eqb" onclick="document.location='$url'"><div>Slot $id</div><div>Current item</div></div>
            <div id="equiplist"><table><tr><td><label><input name="eqids[]" type="checkbox" value="100">$detail</label></td></tr></table></div>
            <button name="action" value="equip">Equip</button><button name="action" value="unequip">Unequip</button></div></div></form>"""
    }

    @Test fun reopenMailMessages() = reopen(HvRoute.MoogleMail, "mid", "Back") { id, detail ->
        val rows = (1..2).joinToString("") { n -> """<tr onclick="document.location='?s=Bazaar&amp;ss=mm&amp;mid=$n'">
            <td>Sender</td><td>Entry $n</td><td>Today</td><td>Never</td></tr>""" }
        val content = if (id == null) """<table id="mmail_list">$rows</table>"""
            else """<form id="mailform" method="post"><textarea>$detail</textarea></form>"""
        """<div id="mmail_outer">$content</div>"""
    }

    @Test fun reopenMarketItems() = reopen(HvRoute.Market, "itemid", "Items") { id, detail ->
        val rows = (1..2).joinToString("") { n -> """<tr onclick="document.location='?s=Bazaar&amp;ss=mk&amp;screen=browseitems&amp;itemid=$n'"><td>Entry $n</td></tr>""" }
        val content = if (id == null) """<div id="market_itemlist"><table>$rows</table></div>"""
            else """<div id="market_itemheader"><a>$detail</a></div>"""
        """<div id="market_outer">$content</div>"""
    }

    @Test fun reopenArmoryEquipment() = reopen(HvRoute.Armory, "eqids[]", "Back") { id, detail ->
        val rows = (1..2).joinToString("") { n -> """<tr><td><label><input name="eqids[]" type="checkbox" value="$n">Entry $n</label></td></tr>""" }
        val content = if (id == null) """<script>var multiselect = false;</script>
            <form id="equipform" method="get"><input type="hidden" name="s" value="Bazaar"><input type="hidden" name="ss" value="am">
            <div id="equiplist"><table>$rows</table></div><input id="equipsubmit" type="submit" value="Modify"></form>"""
            else """<div id="equipmodify_outer"><form id="equipform" method="post"><div id="equipinfo"><div class="showequip"><div>$detail</div></div></div></form></div>"""
        """<div id="armory_outer">$content</div>"""
    }

    @Test fun reopenMonsters() = reopen(HvRoute.MonsterLab, "slot", "Monster list") { id, detail ->
        val url = "?s=Bazaar&amp;ss=ml"
        val rows = (1..2).joinToString("") { n -> """<div onclick="document.location='$url&amp;slot=$n'"><div>$n</div><div>Entry $n</div></div>""" }
        val content = if (id == null) """<div id="slot_pane">$rows</div>"""
            else """<div id="monster_head"><div onclick="document.location='$url'"></div>
                <div class="msl"><div>$id</div><div>$detail</div></div></div><div id="monsterstats_left"></div>"""
        """<div id="monster_outer">$content</div>"""
    }

    private fun reopen(route: HvRoute, idParameter: String, up: String, body: (String?, String) -> String) {
        val ctx = instrument.targetContext
        val prefs = "page-navigation-test"
        val isolated = object : ContextWrapper(ctx) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = ctx.getSharedPreferences(prefs, mode)
        }
        val jar = PersistentCookieJar(isolated)
        val refreshing = CountDownLatch(1)
        val releaseOverview = CountDownLatch(1)
        val releaseDetail = CountDownLatch(1)
        fun menuItem(page: HvRoute) = """<div onclick="document.location='?s=${page.section}&amp;ss=${page.subsection}'"></div>"""
        val menu = """<div id="navbar"><div id="child_Character">${menuItem(HvRoute.Equipment)}</div>
            <div id="child_Bazaar">${menuItem(if (route == HvRoute.Equipment) HvRoute.Bazaar else route)}</div>
            <div id="child_Battle">${menuItem(HvRoute.Battle)}</div></div>"""
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val subsection = request.url.queryParameter("ss")
            val id = request.url.queryParameter(idParameter)
            val page = if (subsection == route.subsection) id ?: "overview" else subsection
            val key = "${request.method} $page"
            requests += key
            val visit = requests.count { it == key }
            if (key == "GET overview" && visit == 2) {
                refreshing.countDown()
                releaseOverview.await(20, TimeUnit.SECONDS)
            }
            val revisiting = key == "GET 1" && visit == 2
            if (revisiting) releaseDetail.await(20, TimeUnit.SECONDS)
            val code = if (key == "GET overview" && visit == 3) 503 else 200
            val html = when (subsection) {
                route.subsection -> body(id, if (revisiting) "Updated detail" else "Detail $id")
                "ch" -> """<div id="mainpane"><table id="attr_table"></table></div>"""
                "is" -> """<div id="itshop_outer"></div>"""
                else -> error("Unexpected destination $subsection")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code)
                .message(if (code == 200) "OK" else "Service Unavailable").body((menu + html).toResponseBody()).build()
        }.build()
        try {
            jar.replaceHvSession(requireNotNull(HvAuthCookies.fromHeader("ipb_member_id=12345; ipb_pass_hash=test-only")), emptyList())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val config = Configuration(activity.resources.configuration).apply { setLocale(Locale.ENGLISH) }
                    val localized = activity.createConfigurationContext(config)
                    activity.setContent {
                        CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides config) {
                            ProvideLocalizedText { ObeliskApp(jar, activity.window, http) }
                        }
                    }
                }
                tap("Choose a ${route.section} page")
                // The menu animates. Use its semantic click, then physically tap the stationary page controls.
                assertTrue(control(route.label).performAction(AccessibilityNodeInfo.ACTION_CLICK))
                tap("Entry 1")
                find("Detail 1")
                tap(up)
                assertTrue("Overview did not refresh: $requests", refreshing.await(5, TimeUnit.SECONDS))
                tap("Entry 2")
                find("Detail 2")
                releaseOverview.countDown()
                back()
                find("Server returned HTTP 503")
                tap("Entry 1")
                find("Detail 1")
                if (route == HvRoute.Equipment) {
                    assertFalse("Cannot submit cached equipment", control("Unequip").isEnabled)
                    tap("Unequip")
                }
                releaseDetail.countDown()
                find("Updated detail")
                if (route == HvRoute.Equipment) assertTrue(control("Unequip").isEnabled)
                back()
                find("Entry 2")
                back()
                val root = "GET ${route.category.defaultRoute.subsection}"
                val deadline = SystemClock.elapsedRealtime() + 5000
                while (requests.lastOrNull() != root && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
                assertTrue("Unexpected history: $requests", requests.lastOrNull() == root)
                assertTrue("Unexpected write: $requests", requests.none { it.startsWith("POST") })
            }
        } finally {
            releaseOverview.countDown()
            releaseDetail.countDown()
            ctx.deleteSharedPreferences(prefs)
        }
    }

    private fun back() = instrument.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

    private fun find(label: String): AccessibilityNodeInfo {
        fun match(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isVisibleToUser && (node.contentDescription?.toString() == label ||
                    label in node.text?.toString()?.lines().orEmpty())) return node
            return (0 until node.childCount).firstNotNullOfOrNull { i -> node.getChild(i)?.let(::match) }
        }
        val deadline = SystemClock.elapsedRealtime() + 7000
        while (SystemClock.elapsedRealtime() < deadline) {
            instrument.uiAutomation.rootInActiveWindow?.let(::match)?.let { return it }
            SystemClock.sleep(50)
        }
        throw AssertionError("Missing $label after $requests")
    }

    private fun control(label: String) = generateSequence(find(label)) { it.parent }.first { it.isClickable }

    private fun tap(label: String) {
        val rect = Rect().also(find(label)::getBoundsInScreen)
        val result = instrument.uiAutomation.executeShellCommand("input tap ${rect.centerX()} ${rect.centerY()}")
        ParcelFileDescriptor.AutoCloseInputStream(result).use { it.readBytes() }
    }
}
