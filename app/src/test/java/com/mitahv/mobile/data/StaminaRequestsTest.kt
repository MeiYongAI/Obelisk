package com.mitahv.mobile.data

import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvStaminaRecovery
import com.mitahv.mobile.model.HvWorld
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaminaRequestsTest {
    @Test
    fun postsTheParsedFieldsToCurrentPageExactlyOnce() {
        val recovery = HvStaminaRecovery(HvRoute.Battle.url(HvWorld.Persistent), listOf("recover" to "stamina", "synthetic_field" to "a+b&c"))
        val request = StaminaRequests.build(HvWorld.Persistent, HvRoute.Battle, recovery)
        val buffer = Buffer()
        request.body!!.writeTo(buffer)

        assertEquals("POST", request.method)
        assertEquals(recovery.actionUrl, request.url)
        assertEquals(HvRoute.Battle.url(HvWorld.Persistent).toString(), request.header("Referer"))
        assertEquals("recover=stamina&synthetic_field=a%2Bb%26c", buffer.readUtf8())
        assertTrue(request.body!!.isOneShot())
    }

    @Test
    fun rejectsOldWorldOrPageRecoveryBeforeSending() {
        val valid = HvStaminaRecovery(HvWorld.Persistent.baseUrl, listOf("recover" to "stamina"))
        expectInvalid { StaminaRequests.build(HvWorld.Isekai, HvRoute.Character, valid) }
        expectInvalid { StaminaRequests.build(HvWorld.Persistent, HvRoute.Battle, valid) }
        expectInvalid { StaminaRequests.build(HvWorld.Persistent, HvRoute.Character,
            valid.copy(actionUrl = "https://hentaiverse.org/?s=Character&ss=ch&ss=eq".toHttpUrl())) }
        expectInvalid { StaminaRequests.build(HvWorld.Persistent, HvRoute.Character,
            valid.copy(actionUrl = "https://hentaiverse.org/?s=Character&ss=ch#another-form".toHttpUrl())) }
    }

    @Test
    fun subpageRecoveryPreservesActualQueryAndReferrerInEachWorld() {
        HvWorld.entries.forEach { world ->
            val current = world.baseUrl.newBuilder()
                .addQueryParameter("s", "Bazaar").addQueryParameter("ss", "am")
                .addQueryParameter("screen", "modify").addQueryParameter("filter", "equipped")
                .addQueryParameter("eqids[]", "1001").addQueryParameter("eqids[]", "1002")
                .build()
            val request = StaminaRequests.build(world, HvRoute.Armory,
                HvStaminaRecovery(current, listOf("recover" to "stamina")), current)

            assertEquals(current, request.url)
            assertEquals(current.toString(), request.header("Referer"))
            assertEquals(listOf("1001", "1002"), request.url.queryParameterValues("eqids[]"))
            assertTrue(request.body!!.isOneShot())
        }
    }

    @Test
    fun subpageRecoveryStillRejectsDuplicateRouteParametersAndWrongCurrentPage() {
        val current = "https://hentaiverse.org/?s=Character&ss=ab&tree=onehanded".toHttpUrl()
        val recovery = HvStaminaRecovery(current, listOf("recover" to "stamina"))
        listOf("s" to "Character", "s" to "Battle", "ss" to "ab", "ss" to "ch").forEach { (name, value) ->
            val duplicate = current.newBuilder().addQueryParameter(name, value).build()
            expectInvalid { StaminaRequests.build(HvWorld.Persistent, HvRoute.Abilities,
                recovery.copy(actionUrl = duplicate), current) }
            expectInvalid { StaminaRequests.build(HvWorld.Persistent, HvRoute.Abilities, recovery, duplicate) }
        }
        expectInvalid { StaminaRequests.build(HvWorld.Persistent, HvRoute.Abilities, recovery,
            HvRoute.Abilities.url(HvWorld.Isekai)) }
        expectInvalid { StaminaRequests.build(HvWorld.Persistent, HvRoute.Abilities, recovery,
            HvRoute.Equipment.url(HvWorld.Persistent)) }
        expectInvalid { StaminaRequests.build(HvWorld.Isekai, HvRoute.Abilities, recovery) }
    }

    @Test
    fun requiresSingleStaminaRecoveryField() {
        val target = HvWorld.Persistent.baseUrl
        listOf(emptyList(), listOf("recover" to "health"), listOf("recover" to "stamina", "recover" to "stamina"))
            .forEach { fields -> expectInvalid {
                StaminaRequests.build(HvWorld.Persistent, HvRoute.Character, HvStaminaRecovery(target, fields))
            } }
        assertTrue(StaminaRequests.build(HvWorld.Persistent, HvRoute.Character,
            HvStaminaRecovery(target, listOf("recover" to "stamina"))).body!!.isOneShot())
    }

    private fun expectInvalid(action: () -> Unit) {
        var rejected = false
        try { action() } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue("Invalid recovery must be rejected before sending", rejected)
    }
}
