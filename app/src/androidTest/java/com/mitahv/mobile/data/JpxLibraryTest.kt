package com.mitahv.mobile.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class JpxLibraryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val filename = "jpx-test-${UUID.randomUUID()}.db"
    private val db = JpxLibrary.Database(context, filename)
    private val lib = JpxLibrary(db, "sample", "Isekai")
    private val one = "OneHanded_General"
    private val two = "TwoHanded_General"
    private val blank = "Staff_Tower"
    private val stop = """{"rules":[{"type":"stop"}]}"""
    private val bundle = """{"battleVersion":1,"setting":true,"$one":$stop,"$two":{"supports":[],"attacks":[{"type":"item"}]},"$blank":{}}"""
    private fun parse(text: String) = JpxImport.read(text.byteInputStream(), "battle.json", "Isekai")
    private fun profiles() = lib.entries().associateBy { it.mode }
    private fun saved() = db.readableDatabase.rawQuery("SELECT slot,applied_source FROM profiles WHERE account='sample' AND world='Isekai' AND applied_source IS NOT NULL",null).use { c ->
        buildMap { while(c.moveToNext()) put(c.getString(0), c.getString(1)) }
    }
    @After fun cleanUp() { db.close(); context.deleteDatabase(filename) }

    @Test fun importingEditingExportingAndRemovingTargetOnlyOneMode() {
        assertEquals(3, lib.save(parse(bundle)))
        val entries = profiles(); assertEquals(setOf(one,two,blank), entries.keys)
        assertEquals(0, entries.getValue(blank).rules); assertTrue(saved().isEmpty())
        val a = entries.getValue(one).id; val b = entries.getValue(two).id
        val bBefore = lib.load(b)
        lib.apply(a); lib.apply(b)
        val active = saved(); assertEquals(setOf(one,two),active.keys)
        lib.rename(a,"Shield"); lib.update(a, JpxDocument(lib.load(a)).update(one,"rules",lib.world) { it.clear() })
        assertEquals(active,saved()); assertTrue(profiles().getValue(one).changed); assertFalse(profiles().getValue(two).changed)
        val export = parse(lib.load(a).source)
        assertEquals(listOf(one),export.modes); assertEquals(0,export.rules); assertFalse(JpxDocument(export).config.has("setting"))
        assertEquals(0,lib.save(parse(bundle))); assertEquals(0,lib.load(a).rules); assertEquals("Shield",profiles().getValue(one).nickname)
        lib.apply(a); assertNotEquals(active[one],saved()[one]); assertEquals(active[two],saved()[two]); assertFalse(profiles().getValue(one).changed)
        lib.remove(a); assertEquals(setOf(two),saved().keys); assertEquals(setOf(two,blank),profiles().keys); assertEquals(bBefore,lib.load(b))
        assertEquals(1,lib.save(parse(bundle))); assertEquals(1,profiles().getValue(one).rules); assertFalse(profiles().getValue(one).selected)
    }

    @Test fun alternativesWithSameFilenameAreIndependentWithinTheirAccountAndWorld() {
        lib.save(parse(bundle)); val original = profiles().getValue(one); lib.apply(original.id)
        val alternate = parse("""{"version":1,"$one":{}}"""); assertEquals(1,lib.save(alternate))
        val copy = lib.entries().single { it.mode==one && it.id!=original.id }; assertFalse(copy.selected)
        lib.apply(copy.id); lib.remove(original.id); assertTrue(profiles().getValue(one).selected)
        for (other in listOf(JpxLibrary(db,"other","Isekai"),JpxLibrary(db,"sample","Persistent"))) {
            assertTrue(other.entries().isEmpty()); assertTrue(runCatching { other.remove(copy.id) }.isFailure)
            assertEquals(3,other.save(parse(bundle))); assertFalse(other.entries().any { it.selected })
        }
        assertEquals(lib.load(copy.id).source, saved()[one])
    }

    @Test fun staffGeneralExportCanBeImportedOnItsOwn() {
        val staff = "Staff_General"
        val raw = """{"battleVersion":1,"$staff":$stop,"$two":{},"globalOption":true}"""
        for (file in listOf(raw, """{"schemaVersion":1,"kind":"battle","world":"Isekai","config":$raw}""")) {
            lib.save(parse(file))
            val before = profiles(); val id = before.getValue(staff).id
            lib.update(id, JpxDocument(lib.load(id)).update(staff, "rules", lib.world) { it[0].addProperty("disabled", true) })
            val exported = lib.load(id).source
            val single = JpxImport.read(exported.byteInputStream(), "Staff_General.json", lib.world)
            assertEquals(listOf(staff), single.modes); assertFalse(JpxDocument(single).config.has("globalOption"))
            val sibling = lib.load(before.getValue(two).id)
            lib.remove(id); assertEquals(1, lib.save(single)); assertEquals(sibling, lib.load(before.getValue(two).id))
            val restored = profiles().getValue(staff)
            assertFalse(restored.selected)
            assertTrue(JpxDocument(lib.load(restored.id)).rules(staff, "rules")[0].asJsonObject["disabled"].asBoolean)
            lib.entries().forEach { lib.remove(it.id) }
        }
    }
    @Test fun largePlusProfileRoundTripsWithoutCursorWindowTruncation() {
        val text = """{"schemaVersion":1,"kind":"battle","world":"Isekai","description":"keep","config":{"version":1,"$one":{"rules":[{"type":"custom","code":"${"測".repeat(698900)}"}]},"$blank":{}}}"""
        val src = parse(text); assertEquals(2,lib.save(src))
        val result = lib.load(profiles().getValue(one).id)
        val root = JsonParser.parseString(result.source).asJsonObject; assertEquals("battle-mode",root["kind"].asString); assertEquals("keep",root["description"].asString)
        assertEquals("Isekai",root["world"].asString); assertEquals(listOf(one),parse(result.source).modes)
        assertEquals(JpxDocument(src).config[one],JpxDocument(result).config[one]); lib.apply(profiles().getValue(one).id); assertTrue(profiles().getValue(one).selected)
    }
}
