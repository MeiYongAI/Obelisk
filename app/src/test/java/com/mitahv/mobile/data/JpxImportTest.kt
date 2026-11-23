package com.mitahv.mobile.data

import com.google.gson.JsonParseException
import org.junit.Assert.*
import org.junit.Test

class JpxImportTest {
    private val legacy = """{"battleVersion":20251216,"1H_Mage_Item":{"supports":[{"type":"item","conditions":[{"key":"pMP","value":[0,0.3]}]}],"attacks":[{"type":"custom","code":"opaque text"}]},"Staff_General":{}}"""
    private val modern = """{"battleVersion":20261101,"DW_Mage_Arena":{"rules":[{"type":"target"}],"kb_a":[{"type":"item"}]}}"""
    private fun envelope(world: String = "Any", mode: String = "DW_Mage_Arena") = """{"schemaVersion":1,"kind":"battle-mode","title":"My build","world":"$world","mode":"$mode","config":$modern}"""
    private fun read(s: String) = JpxImport.read(s.byteInputStream(), "sample.txt", "Isekai")

    @Test fun plainTextBomAndCustomCodeRoundTripAsData() {
        val draft = read("\uFEFF$legacy")
        assertEquals(legacy, draft.source); assertEquals("sample.txt", draft.name)
        assertEquals(listOf("1H_Mage_Item"), draft.modes); assertEquals(2, draft.rules)
    }
    @Test fun wrappedModeAllowsAnyOrMatchingWorld() {
        assertEquals("My build", read(envelope()).name); assertEquals(2, read(envelope("Isekai")).rules)
        assertThrows(WrongConfigWorld::class.java) { read(envelope("Persistent")) }
    }
    @Test fun wholeBattleEnvelopeAllowsMultipleModes() {
        assertEquals(1, read("""{"schemaVersion":1,"world":"Any","kind":"battle","config":$legacy}""").modes.size)
    }
    @Test fun rejectsStatsWrongModeAndUnknownVersion() {
        for (s in listOf(envelope().replace("battle-mode", "stats"), envelope(mode = "Staff_General"),
            envelope().replace("schemaVersion\":1", "schemaVersion\":2"), "{}", "[]")) {
            assertThrows(IllegalArgumentException::class.java) { read(s) }
        }
    }
    @Test fun rejectsBrokenRuleListsAndMalformedConditions() {
        for (s in listOf(legacy.replace("\"attacks\"", "\"other\""), modern.replace("\"type\":\"target\"", "\"type\":3"),
            legacy.replace("\"key\":\"pMP\"", "\"key\":\"\""))) {
            assertThrows(IllegalArgumentException::class.java) { read(s) }
        }
    }
    @Test fun emptyGroupsAreNotImportsButKeybindsAndDisabledRulesAreContent() {
        assertThrows(EmptyConfig::class.java) { read("""{"version":1,"Staff_General":{"rules":[],"kb_a":[]},"OneHanded_Item":{}}""") }
        assertEquals(listOf("Staff_General"), read("""{"version":1,"Staff_General":{"rules":[],"kb_a":[{"type":"stop","disabled":true}]}}""").modes)
    }
    @Test fun rejectsOversizedFileAndInvalidJson() {
        assertThrows(OversizedConfig::class.java) { read(" ".repeat(JpxImport.MAX_BYTES + 1)) }
        assertThrows(JsonParseException::class.java) { read("{\"battleVersion\":") }
        assertTrue(runCatching { read("$modern null") }.isFailure)
    }
}
