package com.mitahv.mobile.data

import com.mitahv.mobile.model.HvWorld
import java.net.URLDecoder
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterRequestsTest {
    @Test
    fun switchingPersonaPostsCompleteFormToEachWorldRoot() {
        mapOf(
            HvWorld.Persistent to "https://hentaiverse.org/",
            HvWorld.Isekai to "https://alt.hentaiverse.org/isekai/",
        ).forEach { (world, root) ->
            val request = CharacterRequests.persona(world, "2")

            assertEquals(root, request.url.toString())
            assertEquals("POST", request.method)
            assertEquals("application/x-www-form-urlencoded", request.body!!.contentType().toString())
            assertEquals(
                mapOf("persona_set" to "2", "persona_rename" to "", "persona_create" to ""),
                request.formFields(),
            )
            assertTrue(request.body!!.isOneShot())
        }
    }

    @Test
    fun renameEncodesUnicodeAndFormSeparatorsWithoutAddingFields() {
        val name = "人格 + A&B=2"
        val request = CharacterRequests.persona(HvWorld.Persistent, "3", rename = name)

        assertEquals(3, request.formFields().size)
        assertEquals(name, request.formFields()["persona_rename"])
        assertEquals("3", request.formFields()["persona_set"])
    }

    @Test
    fun createsBothPersonaTypesFromSelectedPersona() {
        for (kind in listOf("clone", "blank")) {
            val request = CharacterRequests.persona(HvWorld.Isekai, "1", create = kind)
            assertEquals(
                mapOf("persona_set" to "1", "persona_rename" to "", "persona_create" to kind),
                request.formFields(),
            )
        }
    }

    @Test
    fun attributePostContainsAllSixSignedDeltas() {
        for (world in HvWorld.entries) {
            val request = CharacterRequests.attributes(world, mapOf("str" to 10, "dex" to -2))

            assertEquals(world.baseUrl, request.url)
            assertEquals("POST", request.method)
            assertEquals(
                mapOf(
                    "attr_apply" to "1", "str_delta" to "10", "dex_delta" to "-2",
                    "agi_delta" to "0", "end_delta" to "0", "int_delta" to "0", "wis_delta" to "0",
                ),
                request.formFields(),
            )
            assertTrue(request.body!!.isOneShot())
        }
    }

    @Test
    fun rejectsMissingPersonaAndUnrecognizedOperationsBeforeNetwork() {
        expectInvalid { CharacterRequests.persona(HvWorld.Persistent, "") }
        expectInvalid { CharacterRequests.persona(HvWorld.Persistent, "0") }
        expectInvalid { CharacterRequests.persona(HvWorld.Persistent, "2", rename = " ") }
        expectInvalid { CharacterRequests.persona(HvWorld.Persistent, "2", create = "delete") }
        expectInvalid { CharacterRequests.persona(HvWorld.Persistent, "2", rename = "A", create = "clone") }
        expectInvalid { CharacterRequests.attributes(HvWorld.Isekai, mapOf("persona_create" to 1)) }
    }

    private fun Request.formFields(): Map<String, String> {
        val buffer = Buffer()
        body!!.writeTo(buffer)
        return buffer.readUtf8().split('&').associate { field ->
            val parts = field.split('=', limit = 2)
            URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
        }
    }

    private fun expectInvalid(action: () -> Unit) {
        var rejected = false
        try {
            action()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Invalid operation must be rejected before sending", rejected)
    }
}
