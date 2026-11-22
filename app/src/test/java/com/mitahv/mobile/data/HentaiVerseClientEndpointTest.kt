package com.mitahv.mobile.data

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
