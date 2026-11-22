package com.mitahv.mobile.data

import com.mitahv.mobile.model.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class HvImageRequestsTest {
    @Test fun imageUsesSelectedHostWithoutRewritingItsPathOrQuery() {
        val image = "https://hentaiverse.org/riddlemaster?fixture=1".toHttpUrl()
        val request = HvImageRequests.build(HvWorld.Persistent, HvSite.Alt, HvRoute.Battle.url(HvWorld.Persistent), image)
        assertEquals("alt.hentaiverse.org", request.url.host)
        assertEquals(image.encodedPath, request.url.encodedPath)
        assertEquals("1", request.url.queryParameter("fixture"))
        assertTrue(request.header("Referer")!!.startsWith("https://alt.hentaiverse.org/?"))
        HvEndpointPolicy.check(request)
        assertFalse(request.tag(HvRequestScope::class.java)!!.accepts("https://alt.hentaiverse.org/other".toHttpUrl()))
    }

    @Test fun sharedReferenceImageRetainsIsekaiReferer() {
        val request = HvImageRequests.build(HvWorld.Isekai, HvSite.Main, HvRoute.Battle.url(HvWorld.Isekai),
            "https://alt.hentaiverse.org/y/ponychart.jpg".toHttpUrl())
        assertEquals("/y/ponychart.jpg", request.url.encodedPath)
        assertTrue(request.header("Referer")!!.contains("/isekai/"))
        HvEndpointPolicy.check(request)
    }

    @Test fun unrelatedImageHostsAreNotSentTheGameSession() {
        assertThrows(IllegalArgumentException::class.java) {
            HvImageRequests.build(HvWorld.Persistent, HvSite.Main, HvRoute.Battle.url(HvWorld.Persistent),
                "https://unrelated.example/riddlemaster".toHttpUrl())
        }
    }
}
