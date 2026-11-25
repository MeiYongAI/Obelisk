package com.mitahv.mobile

import com.mitahv.mobile.data.HentaiVerseClient
import com.mitahv.mobile.model.HvSite
import com.mitahv.mobile.model.HvWorld
import com.mitahv.mobile.model.BattlePage
import com.mitahv.mobile.model.BazaarSectionsPage
import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.RiddleImage
import com.mitahv.mobile.model.RiddlePage
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.CookieJar
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PageSnapshotCacheTest {
    private fun snapshot(index: Int) = PageSnapshot(
        route = HvRoute.Bazaar,
        url = "https://hentaiverse.org/?s=Bazaar&ss=is&page=$index".toHttpUrl(),
        navigation = null,
        state = RequestState.BazaarSectionReady(BazaarSectionsPage(HvRoute.Bazaar)),
    )

    @Test fun retainsRecentPagesAndClearsThem() {
        val cache = PageSnapshotCache()
        val pages = (0..32).map(::snapshot)
        pages.take(32).forEach(cache::put)
        assertSame(pages[0], cache[pages[0].url])
        cache.put(pages[32])
        assertNull(cache[pages[1].url])
        assertSame(pages[0], cache[pages[0].url])
        assertSame(pages[32], cache[pages[32].url])

        cache.clear()
        pages.forEach { assertNull(cache[it.url]) }
    }

    @Test fun doesNotStoreBattleRiddleOrIncompleteResponses() {
        val cache = PageSnapshotCache()
        val battle = BattlePage("", emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), "", 0f)
        val riddle = RiddlePage(RiddleImage("", null), emptyList(), null, 1, 3)
        val preview = snapshot(0)
        listOf(RequestState.BattleReady(battle), RequestState.BattleBusy(battle), RequestState.RiddleReady(riddle),
            RequestState.Idle, RequestState.Loading, RequestState.Failed("Failed")).forEach { state ->
            cache.put(preview.copy(state = state))
            assertNull(cache[preview.url])
        }
    }

    @Test fun reusesOnlyAnIdenticalCompleteResponseAtTheSameUrl() {
        val world = HvWorld.Persistent
        val route = HvRoute.Character
        val url = route.url(world)
        val client = HentaiVerseClient(CookieJar.NO_COOKIES)
        val html = javaClass.getResource("/character/persistent.html")!!.readText()
        fun parse(body: String, previous: PageSnapshot?, pageUrl: okhttp3.HttpUrl = url) =
            parsePageResponse(route, world, HvSite.Main, body, pageUrl, previous, client)
        val original = parse(html, null).snapshot
        assertSame(original, parse(html, original).snapshot)
        assertNotSame(original, parse(html, original, url.newBuilder().addQueryParameter("p", "2").build()).snapshot)
        assertNotSame(original, parse(html + "<input type=hidden name=token value=new>", original).snapshot)

        val withDetails = html + "<script src='/dynjs/1/itemc.js'></script>"
        val pending = parse(withDetails, null)
        assertNotNull(pending.source)
        assertNull(pending.snapshot.bodyDigest)
        assertNotNull(parse(withDetails, pending.snapshot).source)

        val battle = javaClass.getResource("/battle-active/encounter.html")!!.readText()
        val updated = parse(battle, original).snapshot
        assertTrue(updated.state is RequestState.BattleReady)
        assertNull(updated.bodyDigest)
    }
}
