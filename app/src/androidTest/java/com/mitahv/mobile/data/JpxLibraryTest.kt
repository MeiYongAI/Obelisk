package com.mitahv.mobile.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
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
    private fun saved() = db.readableDatabase.rawQuery("SELECT slot,payload FROM applied WHERE account='sample' AND world='Isekai'",null).use { c ->
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
        assertEquals("{}",saved()[one])
    }

    private fun seedLegacy(version: Int) {
        context.openOrCreateDatabase(filename, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("""CREATE TABLE configs(id INTEGER PRIMARY KEY,account TEXT,world TEXT,digest TEXT,name TEXT,modes TEXT,rules INTEGER,source TEXT,selected INTEGER)""")
            val hash = MessageDigest.getInstance("SHA-256").digest(bundle.toByteArray()).joinToString("") { "%02x".format(it) }
            old.execSQL("INSERT INTO configs VALUES(7,'sample','Isekai',?,'battle.json',?,2,?,1)", arrayOf(hash,"$one, $two, $blank",bundle))
            if(version==2) {
                old.execSQL("CREATE TABLE applied(account TEXT,world TEXT,slot TEXT,entry INTEGER,digest TEXT,payload TEXT,PRIMARY KEY(account,world,slot))")
                for ((slot,payload) in mapOf(one to "{}","setting" to "false")) old.execSQL("INSERT INTO applied VALUES('sample','Isekai',?,7,'prior-revision',?)",arrayOf(slot,payload))
            }
            old.version = version
        }
    }
    @Test fun v2KeepsUnappliedEditsAndHistoricalActivePayloadsSeparate() {
        seedLegacy(2); val rows = profiles(); assertEquals(3,rows.size); assertEquals(1,lib.load(rows.getValue(one).id).rules)
        assertTrue(rows.getValue(one).changed); assertFalse(rows.getValue(two).selected)
        assertEquals(mapOf(one to "{}","setting" to "false"),saved()); assertEquals(0,lib.save(parse(bundle)))
        lib.remove(rows.getValue(two).id); assertEquals(mapOf(one to "{}","setting" to "false"),saved())
    }
    @Test fun v1SelectedPackageMigratesToAppliedIndividualModes() {
        seedLegacy(1); assertEquals(3,lib.entries().size); assertTrue(lib.entries().all { it.selected && !it.changed })
        assertEquals(setOf(one,two,blank,"setting"),saved().keys); assertEquals(stop,saved()[one])
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
