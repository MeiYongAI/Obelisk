package com.mitahv.mobile.data

import com.mitahv.mobile.model.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class HvEndpointPolicyTest {
    @Test fun siteSelectionChangesPersistentOriginWithoutChangingWorld() {
        assertEquals("https://hentaiverse.org/?s=Character&ss=ch", HvRoute.Character.url(HvWorld.Persistent).toString())
        assertEquals("https://alt.hentaiverse.org/?s=Character&ss=ch", HvRoute.Character.url(HvWorld.Persistent, HvSite.Alt).toString())
        for (site in HvSite.entries) {
            assertEquals("https://alt.hentaiverse.org/isekai/?s=Character&ss=ch", HvRoute.Character.url(HvWorld.Isekai, site).toString())
        }
        assertEquals(HvRoute.Character, HvRoute.fromUrl("https://alt.hentaiverse.org/?s=Character&ss=ch", HvWorld.Persistent))
        assertNull(HvRoute.fromUrl("https://alt.hentaiverse.org/isekai/?s=Character&ss=ch", HvWorld.Persistent))
        assertNull(HvRoute.fromUrl("https://alt.hentaiverse.org/?s=Character&ss=ch", HvWorld.Isekai))
    }

    @Test fun mappingPreservesSubpageQueriesAndRepeatedFormSelection() {
        val source = "https://hentaiverse.org/?s=Character&ss=eq&filter=1h&eqids%5B%5D=123&eqids%5B%5D=456".toHttpUrl()
        val mapped = HvWorld.Persistent.urlAtSite(source, HvSite.Alt)
        assertEquals("alt.hentaiverse.org", mapped.host)
        assertEquals(source.encodedQuery, mapped.encodedQuery)
        assertEquals(listOf("123", "456"), mapped.queryParameterValues("eqids[]"))
        assertEquals(source, HvWorld.Persistent.urlAtSite(mapped, HvSite.Main))
    }

    @Test fun rejectsWrongWorldUntrustedOriginsAndUnexpectedPaths() {
        val invalid = listOf("https://alt.hentaiverse.org/isekai/", "https://hentaiverse.org/isekai/",
            "https://hentaiverse.org.evil.test/", "https://hentaiverse.org:444/", "http://alt.hentaiverse.org/",
            "https://user@alt.hentaiverse.org/", "https://alt.hentaiverse.org/#form", "https://alt.hentaiverse.org/other")
        for (value in invalid) for (site in HvSite.entries) {
            assertThrows(value, IllegalArgumentException::class.java) {
                HvWorld.Persistent.urlAtSite(value.toHttpUrl(), site)
            }
        }
        assertFalse(HvWorld.Persistent.containsUrl("https://alt.hentaiverse.org/json".toHttpUrl()))
        assertTrue(HvWorld.Persistent.containsUrl("https://alt.hentaiverse.org/json".toHttpUrl(), allowJson = true))
        assertFalse(HvWorld.Isekai.containsUrl("https://alt.hentaiverse.org/json".toHttpUrl(), allowJson = true))
    }

    @Test fun battleJsonAndRefererUseSelectedSiteAndStayInTheSameWorld() {
        for (world in HvWorld.entries) for (site in HvSite.entries) {
            val request = Request.Builder().url(requireNotNull(world.baseUrl.resolve("json")))
                .header("Referer", HvRoute.Battle.url(world).toString()).build()
            val prepared = HvEndpointPolicy.prepare(request, world, site)
            assertEquals(world.baseUrl(site).host, prepared.url.host)
            assertEquals("${world.baseUrl.encodedPath}json", prepared.url.encodedPath)
            assertEquals(HvRoute.Battle.url(world, site).toString(), prepared.header("Referer"))
            HvEndpointPolicy.check(prepared)
            val crossWorld = prepared.newBuilder().url(if (world == HvWorld.Persistent) HvWorld.Isekai.baseUrl else HvWorld.Persistent.baseUrl).build()
            assertThrows(IOException::class.java) { HvEndpointPolicy.check(crossWorld) }
        }
        val wrongReferer = Request.Builder().url("https://alt.hentaiverse.org/json")
            .header("Referer", HvRoute.Battle.url(HvWorld.Isekai).toString()).build()
        assertThrows(IllegalArgumentException::class.java) { HvEndpointPolicy.prepare(wrongReferer, HvWorld.Persistent, HvSite.Alt) }
    }

    @Test fun redirectsCannotSilentlyChangeTheSelectedSite() {
        val request = HvEndpointPolicy.prepare(Request.Builder().url(HvWorld.Persistent.baseUrl).build(), HvWorld.Persistent, HvSite.Alt)
        assertThrows(IOException::class.java) {
            HvEndpointPolicy.check(request.newBuilder().url(HvWorld.Persistent.baseUrl).build())
        }
    }
}
