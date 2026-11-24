package com.mitahv.mobile.data

import com.mitahv.mobile.RequestState
import com.mitahv.mobile.parseLoadedPage
import com.mitahv.mobile.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

/** Exercises client request preparation through an in-memory transport; never contacts HV. */
class HentaiVerseClientEndpointTest {
    @Test fun compressedBattleRepliesUseTheSameTurnParser() = runBlocking {
        val world = HvWorld.Persistent
        val url = HvRoute.Battle.url(world)
        val json = """{"textlog":[{"t":"You hit the monster."}]}"""
        val gzip = java.io.ByteArrayOutputStream().also { bytes ->
            java.util.zip.GZIPOutputStream(bytes).use { it.write(json.toByteArray()) }
        }.toByteArray()
        val brotli = java.util.Base64.getDecoder().decode("ixSAeyJ0ZXh0bG9nIjpbeyJ0IjoiWW91IGhpdCB0aGUgbW9uc3Rlci4ifV19Aw==")
        for ((encoding, bytes) in listOf("br" to brotli, "gzip" to gzip, "identity" to json.toByteArray())) {
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                assertEquals(setOf("br", "gzip"), request.header("Accept-Encoding")!!.split(",").toSet())
                Response.Builder().request(request).protocol(Protocol.HTTP_2).code(200).message("OK")
                    .header("Content-Encoding", encoding).body(bytes.toResponseBody()).build()
            }.build()
            val response = HentaiVerseClient(CookieJar.NO_COOKIES, httpClient = http)
                .submitBattleAction(world, url, "fixture", BattleAction("attack", 1))
            assertEquals("You hit the monster.", response.getJSONArray("textlog").getJSONObject(0).getString("t"))
        }
    }

    @Test fun battleRefreshReadsTheCurrentUrlAndDisplaysTheReturnedLobby() = runBlocking {
        val world = HvWorld.Isekai
        val url = HvRoute.Battle.url(world)
        val responses = listOf("/battle-active/ring-of-blood.html", "/battle-modes/ar-low-level.html")
        val requests = mutableListOf<Request>()
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            val html = requireNotNull(javaClass.getResource(responses[requests.size])).readText()
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(html.toResponseBody()).build()
        }.build()
        val client = HentaiVerseClient(CookieJar.NO_COOKIES, httpClient = transport)
        val initial = client.fetchPage(world, url)
        assertTrue(parseLoadedPage(HvRoute.Battle, world, initial.html, false, initial.url, emptyList()) is RequestState.BattleReady)
        val refreshed = client.fetchPage(world, url, forceNetwork = true)
        assertTrue(parseLoadedPage(HvRoute.Battle, world, refreshed.html, false, refreshed.url, emptyList()) is RequestState.ArenaReady)
        val refresh = requests.last()
        assertEquals(url, refresh.url)
        assertEquals("GET", refresh.method)
        assertNull(refresh.body)
        assertTrue(refresh.cacheControl.noCache)
    }

    @Test fun everyClientOperationUsesSelectedSiteAndPreservesPayload() = runBlocking {
        for (world in HvWorld.entries) for (site in HvSite.entries) {
            val requests = mutableListOf<Request>()
            val transport = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                requests += request
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body((if (request.url.encodedPath.endsWith("/json")) "{}" else "<html></html>").toResponseBody()).build()
            }.build()
            val client = HentaiVerseClient(CookieJar.NO_COOKIES, site, transport)
            val current = HvRoute.Equipment.url(world).newBuilder().addQueryParameter("filter", "1h").build()
            client.fetchHtml(HvRoute.Character, world)
            client.fetchPage(world, current)
            client.executeNativeAction(world, NativeAction(current, fields = listOf("eqids[]" to "123", "eqids[]" to "456"), label = "Equip"))
            client.restoreStamina(world, HvRoute.Equipment, HvStaminaRecovery(current, listOf("recover" to "stamina")), current)
            client.startArenaChallenge(HvRoute.Battle, world, 1, "synthetic-token")
            client.submitPersonaForm(world, "2", rename = "A & B")
            client.submitAttributeAllocation(world, mapOf("str" to 1))
            client.submitBattleAction(world, HvRoute.Battle.url(world), "synthetic-token", BattleAction("attack", 1))

            assertEquals(8, requests.size)
            assertTrue(requests.all { it.url.host == world.baseUrl(site).host })
            assertTrue(requests.all { world.containsUrl(it.url, allowJson = true) })
            requests.forEach { request ->
                HvEndpointPolicy.check(request)
                request.header("Referer")?.let { assertTrue(it.startsWith(world.baseUrl(site).toString())) }
                if (request.method == "POST") assertTrue(request.body!!.isOneShot())
            }
            assertEquals("1h", requests[1].url.queryParameter("filter"))
            assertEquals("eqids%5B%5D=123&eqids%5B%5D=456", requests[2].bodyText())
            assertEquals("recover=stamina", requests[3].bodyText())
            assertEquals("initid=1&postoken=synthetic-token", requests[4].bodyText())
            assertTrue(requests[5].bodyText().contains("persona_rename=A%20%26%20B"))
            assertTrue(requests[6].bodyText().contains("str_delta=1"))
            assertEquals("${world.baseUrl.encodedPath}json", requests[7].url.encodedPath)
            assertEquals(HvRoute.Battle.url(world, site).toString(), requests[7].header("Referer"))
        }
    }

    @Test fun actionForOtherWorldIsRejectedBeforeTransport() = runBlocking {
        var requests = 0
        val transport = OkHttpClient.Builder().addInterceptor { requests++; error("Must not reach transport") }.build()
        val client = HentaiVerseClient(CookieJar.NO_COOKIES, HvSite.Alt, transport)
        var rejected = false
        try {
            client.executeNativeAction(HvWorld.Persistent, NativeAction(HvRoute.Bazaar.url(HvWorld.Isekai), label = "Buy"))
        } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertEquals(0, requests)
    }

    private fun Request.bodyText(): String = Buffer().also { body!!.writeTo(it) }.readUtf8()
}
