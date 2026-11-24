package com.mitahv.mobile.data

import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvSite
import com.mitahv.mobile.model.HvWorld
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class HvDataScriptRequestsTest {
    @Test fun fetchesOnlyReferencedDataDictionariesAtTheSelectedSite() {
        val requests = HvDataScriptRequests.fromPage("""
            <script src="/z/091c/hvc.js"></script>
            <script src="/dynjs/149140/itemc.js"></script>
            <script src="https://hentaiverse.org/dynjs/149140/itemc.js"></script>
            <script src="/dynjs/1795200978/example/equip-1234.js"></script>
            <script src="https://example.com/dynjs/149140/itemc.js"></script>
            <script src="http://hentaiverse.org/dynjs/149140/itemc.js"></script>
            <script src="/other/itemc.js"></script>
            <script src="/dynjs/1/other.js"></script>
        """, HvWorld.Persistent, HvSite.Alt, HvRoute.Equipment.url(HvWorld.Persistent))

        assertEquals(2, requests.size)
        assertEquals(listOf("/dynjs/149140/itemc.js", "/dynjs/1795200978/example/equip-1234.js"), requests.map { it.url.encodedPath })
        requests.forEach {
            assertEquals("GET", it.method)
            assertEquals("alt.hentaiverse.org", it.url.host)
            assertEquals(HvRoute.Equipment.url(HvWorld.Persistent, HvSite.Alt).toString(), it.header("Referer"))
            HvEndpointPolicy.check(it)
        }
    }

    @Test fun isekaiKeepsItsRefererWhileLoadingTheReferencedRootDictionary() {
        for (site in HvSite.entries) {
            val request = HvDataScriptRequests.fromPage("<script src='/dynjs/149140/itemc.js'></script>",
                HvWorld.Isekai, site, HvRoute.ItemInventory.url(HvWorld.Isekai)).single()
            assertEquals("https://alt.hentaiverse.org/dynjs/149140/itemc.js", request.url.toString())
            assertEquals(HvRoute.ItemInventory.url(HvWorld.Isekai).toString(), request.header("Referer"))
            HvEndpointPolicy.check(request)
            assertThrows(IOException::class.java) {
                HvEndpointPolicy.check(request.newBuilder().url("https://alt.hentaiverse.org/?s=Battle&ss=ar").build())
            }
        }
    }

    @Test fun clientReturnsScriptTextWithoutExecutingIt() = runBlocking {
        val visited = mutableListOf<String>()
        val dictionary = "var dynjs_itemc={\"10005\":{\"n\":\"Health Gem\",\"q\":\"Restores health.\"}};"
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            HvEndpointPolicy.check(chain.request())
            visited += chain.request().url.toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(dictionary.toResponseBody()).build()
        }.build()
        val client = HentaiVerseClient(CookieJar.NO_COOKIES, HvSite.Alt, transport)
        val result = client.fetchSupplementalScripts(HvWorld.Persistent,
            "<script src='/dynjs/149140/itemc.js'></script>", HvRoute.ItemInventory.url(HvWorld.Persistent))
        assertEquals(listOf(dictionary), result.scripts)
        assertTrue(result.failures.isEmpty())
        assertEquals(listOf("https://alt.hentaiverse.org/dynjs/149140/itemc.js"), visited)
    }

    @Test fun clientReusesSuccessfulDictionaryResponsesWithinTheSameAccountSiteClient() = runBlocking {
        var calls = 0
        val dictionary = "var dynjs_itemc={};"
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            HvEndpointPolicy.check(chain.request())
            calls++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(dictionary.toResponseBody()).build()
        }.build()
        val client = HentaiVerseClient(CookieJar.NO_COOKIES, HvSite.Main, transport)
        val requests = HvDataScriptRequests.fromPage(
            "<script src='/dynjs/149140/itemc.js'></script>",
            HvWorld.Persistent,
            HvSite.Main,
            HvRoute.ItemInventory.url(HvWorld.Persistent),
        )

        assertEquals(null, client.cachedSupplementalScripts(requests))
        assertEquals(listOf(dictionary), client.fetchSupplementalScripts(requests).scripts)
        assertEquals(listOf(dictionary), client.cachedSupplementalScripts(requests))
        assertEquals(listOf(dictionary), client.fetchSupplementalScripts(requests).scripts)
        assertEquals(1, calls)
    }

    @Test fun loadsDictionariesConcurrentlyAndKeepsSuccessfulResponses() = runBlocking {
        val bothStarted = CountDownLatch(2)
        val dictionary = "var dynjs_itemc={};"
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            HvEndpointPolicy.check(chain.request())
            bothStarted.countDown()
            if (!bothStarted.await(2, TimeUnit.SECONDS)) throw IOException("Dictionary requests were serialized")
            val succeeds = chain.request().url.encodedPath.endsWith("itemc.js")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (succeeds) 200 else 503).message(if (succeeds) "OK" else "Unavailable")
                .body((if (succeeds) dictionary else "unavailable").toResponseBody()).build()
        }.build()
        try {
            val client = HentaiVerseClient(CookieJar.NO_COOKIES, HvSite.Main, transport)
            val result = client.fetchSupplementalScripts(HvWorld.Persistent, """
                <script src="/dynjs/149140/itemc.js"></script>
                <script src="/dynjs/1795200978/example/equip-1234.js"></script>
            """, HvRoute.ItemInventory.url(HvWorld.Persistent))

            assertEquals(listOf(dictionary), result.scripts)
            assertEquals(1, result.failures.size)
            assertEquals(503, (result.failures.single() as HvHttpException).statusCode)
        } finally {
            transport.dispatcher.executorService.shutdownNow()
            transport.connectionPool.evictAll()
        }
    }
}
