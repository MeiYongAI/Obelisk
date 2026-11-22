package com.mitahv.mobile.data

import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvWorld
import com.mitahv.mobile.model.NativeAction
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class NativeRequestsTest {
    @Test fun preservesCurrentSubmodeAndEncodesFormExactlyOnce() {
        val url = "https://hentaiverse.org/?s=Bazaar&ss=mm&filter=new".toHttpUrl()
        val request = NativeRequests.build(HvWorld.Persistent, NativeAction(url,
            fields = listOf("text" to "a&b + 中文", "eqids[]" to "123", "eqids[]" to "456"), label = "Send"))
        assertEquals(url, request.url)
        assertEquals("POST", request.method)
        assertTrue(request.body!!.isOneShot())
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        assertEquals("text=a%26b%20%2B%20%E4%B8%AD%E6%96%87&eqids%5B%5D=123&eqids%5B%5D=456", buffer.readUtf8())
    }

    @Test fun getNavigationUsesDistinctWorldPaths() {
        for (world in HvWorld.entries) {
            val url = HvRoute.Equipment.url(world).newBuilder().addQueryParameter("filter", "1h").build()
            val request = NativeRequests.build(world, NativeAction(url, "GET", label = "One-handed"))
            assertEquals(url, request.url)
            assertNull(request.body)
        }
    }

    @Test fun rejectsCrossWorldOrOffsiteFormTargets() {
        for (url in listOf("https://alt.hentaiverse.org/isekai/", "https://example.com/",
            "http://hentaiverse.org/", "https://hentaiverse.org/json")) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeRequests.build(HvWorld.Persistent, NativeAction(url.toHttpUrl(), label = "Submit"))
            }
        }
    }
}
