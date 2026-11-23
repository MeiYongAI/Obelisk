package com.mitahv.mobile.data

import com.mitahv.mobile.model.BattleContinuation
import com.mitahv.mobile.model.BattlePage
import com.mitahv.mobile.model.BattleRoundFacts
import com.mitahv.mobile.model.BattleScene
import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvSite
import com.mitahv.mobile.model.HvWorld
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleCompletionParserTest {
    @Test fun suppliedItemWorldNextRoundReloadsCurrentBattleUrl() {
        val page = BattlePageParser.parseBattle(fixture("next-round"))

        assertTrue(page.isComplete)
        assertTrue(page.canContinue)
        assertEquals("下一轮", page.continuation?.label)
        assertNull(page.continuation?.action)
        assertNull(page.continuation?.destination)
        assertEquals(listOf("You are victorious!", "You gain 3643193 exp"), page.completionLines)
        assertTrue(page.controls.isNotEmpty())
        assertTrue(page.controls.none { it.enabled })
    }

    @Test fun explicitNextRoundResponseKeepsTheBattleEncounterEvenWithoutRoundLogFacts() {
        val previous = BattlePage(
            token = "token", playerVitals = emptyList(), enemies = emptyList(), quickActions = emptyList(),
            skills = emptyList(), spells = emptyList(), items = emptyList(),
            log = listOf("Initializing Item World (Round 1 / 5) ..."), completion = "victory", experienceBarWidth = 0f,
            scene = BattleScene.Item, round = BattleRoundFacts(current = 1, total = 5, sawStart = true),
            isComplete = true, continuation = BattleContinuation("下一轮"),
        )
        val next = previous.copy(
            log = emptyList(), isComplete = false, continuation = null,
            round = BattleRoundFacts(), scene = null, turnKey = Any(),
        )

        assertTrue(next.followsRound(previous))
        assertTrue(next.copy(round = next.round.copy(current = 1), scene = BattleScene.Arena).followsRound(previous))
        assertFalse(previous.followsRound(previous))
        assertFalse(next.copy(isComplete = true).followsRound(previous))
    }

    @Test fun suppliedItemWorldFinalOutcomesPreserveActualArmoryReturnTargets() {
        val cases = listOf(
            Triple("victory", "100001", listOf("You are victorious!", "You gain 4857591 exp", "Item world cleared!")),
            Triple("defeat", "100002", listOf("You have been defeated!", "You are ejected from the item world")),
            Triple("flee", "100002", listOf("You have run away!", "You escape from the item world")),
        )
        cases.forEach { (name, equipmentId, lines) ->
            val page = BattlePageParser.parseBattle(fixture(name))
            val continuation = requireNotNull(page.continuation)
            val action = requireNotNull(continuation.action)
            assertTrue(name, page.isComplete)
            assertTrue(name, page.canContinue)
            assertEquals(lines, page.completionLines)
            assertEquals(HvRoute.Armory, continuation.destination)
            assertEquals("GET", action.method)
            assertTrue(action.fields.isEmpty())
            assertEquals("https://hentaiverse.org/?s=Bazaar&ss=am&screen=modify&filter=weapon_staff&eqids%5B%5D=$equipmentId", action.url.toString())
            assertEquals(equipmentId, action.url.queryParameter("eqids[]"))
            assertTrue(page.controls.none { it.enabled })
        }
    }

    @Test fun completionFromDocumentedJsonPaneMergeRetainsTheActualAction() {
        // The pane is real supplied HTML; this JSON envelope is a contract test, not a captured response.
        val completion = Jsoup.parse(fixture("victory")).selectFirst("#pane_completion")!!.html()
        val active = requireNotNull(javaClass.getResource("/battle-active/ring-of-blood.html")).readText()
        val merged = BattlePageParser.mergeActionResponse(active, JSONObject().put("pane_completion", completion))
        val page = BattlePageParser.parseBattle(merged)

        assertTrue(page.isComplete)
        assertEquals(HvRoute.Armory, page.continuation?.destination)
        assertEquals("100001", page.continuation?.action?.url?.queryParameter("eqids[]"))
        assertEquals("Item world cleared!", page.completionLines.last())
    }

    @Test fun destinationComesFromCurrentControlRatherThanAnItemWorldDefault() {
        // Synthetic route variation verifies dispatch only; other modes still need real completion samples.
        val html = withHandler("document.location='?s=Battle&ss=ar';")
        val page = BattlePageParser.parseBattle(html, currentUrl = HvRoute.ItemWorld.url(HvWorld.Persistent, HvSite.Alt))

        assertEquals(HvRoute.Battle, page.continuation?.destination)
        assertEquals("https://alt.hentaiverse.org/?s=Battle&ss=ar", page.continuation?.action?.url.toString())
    }

    @Test fun userConfirmedDefeatDestinationsRemainTheirOwnBattleMode() {
        // Derived route cases based on Item World; Arena and Ring of Blood still lack
        // result HTML. GrindFest now also has the separate actual defeat fixture below.
        listOf(HvRoute.Battle, HvRoute.RingOfBlood, HvRoute.GrindFest).forEach { route ->
            val target = route.url(HvWorld.Persistent)
            val document = Jsoup.parse(fixture("defeat"))
            document.selectFirst("#btcp")!!.attr("onclick", "document.location=`$target`")
            val page = BattlePageParser.parseBattle(document.outerHtml(), currentUrl = route.url(HvWorld.Persistent))
            assertEquals(route, page.continuation?.destination)
            assertEquals(target, page.continuation?.action?.url)
            assertEquals("GET", page.continuation?.action?.method)
            assertEquals("You have been defeated!", page.completionLines.first())
        }
    }

    @Test fun suppliedGrindfestDefeatUsesOfficialReturnHelper() {
        val html = requireNotNull(javaClass.getResource("/battle-completion/grindfest-defeat.html")).readText()
        val url = HvRoute.GrindFest.url(HvWorld.Isekai)
        val page = BattlePageParser.parseBattle(html, world = HvWorld.Isekai, currentUrl = url)

        assertTrue(page.isComplete)
        assertTrue(page.canContinue)
        assertEquals(HvRoute.GrindFest, page.continuation?.destination)
        assertEquals(url, page.continuation?.action?.url)
        assertEquals("GET", page.continuation?.action?.method)
        assertEquals(2, page.completionLines.size)
        assertTrue(page.completionLines.first().contains("被击败"))
        assertEquals("压榨界已结束", page.completionLines.last())
        assertTrue(page.controls.none { it.enabled })
    }

    @Test fun finishDestinationMustBelongToTheCurrentWorldAndAnAvailableRoute() {
        val isekaiUrl = "https://alt.hentaiverse.org/isekai/?s=Battle&ss=iw".toHttpUrl()
        val persistentFinish = BattlePageParser.parseBattle(fixture("victory"), world = HvWorld.Isekai, currentUrl = isekaiUrl)
        assertTrue(persistentFinish.isComplete)
        assertNull(persistentFinish.continuation)

        val isekaiFinish = BattlePageParser.parseBattle(withHandler("document.location=`?s=Bazaar&ss=am&screen=modify&filter=weapon_staff&eqids%5B%5D=100001`"),
            world = HvWorld.Isekai, currentUrl = isekaiUrl)
        assertEquals(HvRoute.Armory, isekaiFinish.continuation?.destination)
        assertEquals("/isekai/", isekaiFinish.continuation?.action?.url?.encodedPath)

        val unavailable = BattlePageParser.parseBattle(withHandler("document.location='?s=Bazaar&ss=ml'"),
            world = HvWorld.Isekai, currentUrl = isekaiUrl)
        assertNull(unavailable.continuation)
    }

    @Test fun unknownOrDisabledCompletionDoesNotInventAContinueAction() {
        val handlers = listOf(
            "",
            "battle.battle_continue(); otherAction()",
            "document.location='https://example.test/?s=Bazaar&ss=am'",
            "document.location='https://alt.hentaiverse.org/isekai/?s=Bazaar&ss=am'",
            "document.location='?s=Bazaar&s=Battle&ss=am'",
            "document.location='?s=Bazaar&ss=unknown'",
            "document.location=`?s=Bazaar&ss=am&filter=\${value}`",
        )
        handlers.forEach { handler ->
            val page = BattlePageParser.parseBattle(withHandler(handler))
            assertTrue(handler, page.isComplete)
            assertFalse(handler, page.canContinue)
        }
        val disabled = Jsoup.parse(fixture("next-round"))
        disabled.selectFirst("#btcp")!!.attr("aria-disabled", "true")
        assertNull(BattlePageParser.parseBattle(disabled.outerHtml()).continuation)
        assertNotNull(BattlePageParser.parseBattle(fixture("next-round")).continuation)
    }

    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/battle-completion/item-world-$name.html")).readText()

    private fun withHandler(handler: String): String = Jsoup.parse(fixture("victory")).apply {
        selectFirst("#btcp")!!.attr("onclick", handler)
    }.outerHtml()
}
