package com.mitahv.mobile

import com.mitahv.mobile.model.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class RiddleRoutingTest {
    @Test fun riddleTakesPriorityOverOldBattleContentWithoutSiteNavigation() {
        val riddle = javaClass.getResource("/riddle-master/manual-form.html")!!.readText()
        val html = riddle.replace("<body>", "<body><div id='battle_main'><div id='pane_action'>Old attack controls</div></div>")
        val state = parseLoadedPage(HvRoute.Battle, HvWorld.Persistent, html, false,
            "https://alt.hentaiverse.org/?s=Battle&ss=ar".toHttpUrl(), emptyList())
        assertTrue(state is RequestState.RiddleReady)
    }

    @Test fun completedRiddleResponseIsParsedAccordingToTheActualReturnedHtml() {
        val url = HvRoute.GrindFest.url(HvWorld.Isekai)
        val before = javaClass.getResource("/riddle-master/isekai-manual-form.html")!!.readText()
        val pending = parseLoadedPage(HvRoute.GrindFest, HvWorld.Isekai, before, false, url, emptyList())
        assertTrue(pending is RequestState.RiddleReady)
        val riddle = (pending as RequestState.RiddleReady).page
        assertEquals(url, riddle.submitAction?.url)
        assertEquals(6, riddle.choices.size)
        assertTrue(riddle.choices.none { it.checked })
        assertNull(riddle.image.url)

        val html = javaClass.getResource("/riddle-master/isekai-after-submit.html")!!.readText()
        val state = parseLoadedPage(HvRoute.GrindFest, HvWorld.Isekai, html, false, url, emptyList())
        assertTrue(state is RequestState.BattleReady)
        val battle = (state as RequestState.BattleReady).page
        assertFalse(battle.isComplete)
        assertNull(battle.continuation)
        assertTrue(battle.controls.any { it.enabled })
        assertTrue(battle.playerEffects.any { it.name == "Blessing of the RiddleMaster" })
        assertEquals("You gain the effect Blessing of the RiddleMaster.", battle.log.first())
    }
}
