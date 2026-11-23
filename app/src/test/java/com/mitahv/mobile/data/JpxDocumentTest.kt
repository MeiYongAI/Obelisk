package com.mitahv.mobile.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class JpxDocumentTest {
    private val single = "Staff_General"
    private val other = "OneHanded_Tower"
    private val bare = """{"battleVersion":20251216,"customOption":{"future":true},"$single":{"rules":[{"type":"item","name":"Spirit Gem","conditions":[{"key":"pSP","value":[0,0.9],"extension":true}]},{"type":"custom","code":"opaque"}],"kb_x":[{"type":"stop"}]},"$other":{}}"""
    private fun doc(text: String) = JpxDocument(JpxImport.read(text.byteInputStream(),"file.txt","Isekai"))
    private fun tree(text: String) = JsonParser.parseString(text).asJsonObject
    private fun wrap(cfg: String, kind: String="battle") = """{"schemaVersion":1,"kind":"$kind","world":"Isekai","title":"Mixed","description":"test","extra":7,${if(kind=="battle-mode") "\"mode\":\"$single\"," else ""}"config":$cfg}"""

    @Test fun fullExportIsUnmodifiedUntilEdited() {
        for (text in listOf(bare,wrap(bare))) assertEquals(text,doc(text).export())
    }
    @Test fun editingRetainsOrderUnknownKeysAndOtherModes() {
        val original=doc(wrap(bare)); val edit=original.update(single,"rules","Isekai") { rules ->
            rules[0].getAsJsonArray("conditions")[0].asJsonObject.getAsJsonArray("value").set(1,com.google.gson.JsonPrimitive(0.7))
            rules.add(0,rules.removeAt(1)); rules.last().addProperty("disabled",true)
        }; val updated=doc(edit.source)
        assertEquals(original.config[other],updated.config[other]);assertEquals(original.config["customOption"],updated.config["customOption"])
        assertEquals(7,tree(edit.source)["extra"].asInt);assertEquals("opaque",updated.rules(single,"rules")[0].asJsonObject["code"].asString)
        val c=updated.rules(single,"rules")[1].asJsonObject.getAsJsonArray("conditions")[0].asJsonObject
        assertTrue(c["extension"].asBoolean);assertEquals(0.7,c.getAsJsonArray("value")[1].asDouble,0.0)
        assertEquals(3,updated.count(single));assertEquals(wrap(bare),original.export())
    }
    @Test fun modeExportPreservesEnvelopeAndSkipsGlobals() {
        val out=doc(wrap(bare)).export(single);val root=tree(out)
        assertEquals("battle-mode",root["kind"].asString);assertEquals("Isekai",root["world"].asString);assertEquals(7,root["extra"].asInt)
        assertEquals(setOf(single,"battleVersion"),root.getAsJsonObject("config").keySet());assertEquals(listOf(single),doc(out).draft.modes)
        assertThrows(WrongConfigWorld::class.java) { JpxImport.read(out.byteInputStream(),"out.json","Persistent") }
        assertEquals(setOf(single,"battleVersion"),tree(doc(bare).export(single)).keySet())
    }
    @Test fun applySingleModePatchesOnlyItsSlotAndUsesAnIndependentCopy() {
        val active=doc(bare).application(null).toMutableMap();val initial=active.toMap()
        assertEquals(setOf(single,other,"customOption"),active.keys)
        val edited=JpxDocument(doc(bare).update(single,"rules","Isekai") { it.clear() })
        assertEquals(initial,active);active.putAll(edited.application(single));assertNotEquals(initial[single],active[single]);assertEquals(initial[other],active[other]);assertEquals(initial["customOption"],active["customOption"])
        val narrowed=bare.replace(",\"$other\":{}", "")
        assertEquals(setOf(single),doc(wrap(narrowed,"battle-mode")).application(null).keys)
    }
    @Test fun emptyAndLegacyGroupsAreEditableWithoutNormalizingThemAway() {
        val draft=doc(bare).update(other,"rules","Isekai") { it.add(JsonObject().apply { addProperty("type","normalAttack") }) };assertEquals(1,doc(draft.source).count(other))
        val legacy=doc("""{"version":20251216,"$single":{"supports":[],"attacks":[],"kb_a":[]}}""");assertEquals(listOf("supports","attacks","kb_a"),legacy.groups(single))
        val modified=JpxDocument(legacy.update(single,"kb_a","Isekai") { it.add(JsonObject().apply{addProperty("type","stop")}) });assertEquals(1,modified.count(single));assertFalse(modified.config.getAsJsonObject(single).has("rules"));assertEquals(20251216,tree(modified.export(single))["battleVersion"].asInt)
        assertThrows(IllegalArgumentException::class.java) { legacy.update(single,"hidden","Isekai"){} }
    }
}
