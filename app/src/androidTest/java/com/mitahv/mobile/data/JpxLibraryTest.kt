package com.mitahv.mobile.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mitahv.mobile.model.BattleStyle
import com.mitahv.mobile.model.BattleScene
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
    private fun profiles() = lib.catalog().entries.associateBy { it.mode }
    private fun published(id: Long) = lib.load(id, published = true).source
    @After fun cleanUp() { db.close(); context.deleteDatabase(filename) }

    @Test fun importingEditingExportingAndRemovingTargetOnlyOneMode() {
        assertEquals(3, lib.save(parse(bundle)))
        val entries = profiles(); assertEquals(setOf(one,two,blank), entries.keys)
        assertEquals(0, entries.getValue(blank).rules); assertFalse(lib.catalog().manual)
        val a = entries.getValue(one).id; val b = entries.getValue(two).id
        val bBefore = lib.load(b)
        lib.apply(a); lib.apply(b)
        val active = published(a); assertEquals(b, lib.catalog().manualId)
        lib.rename(a,"Shield"); lib.update(a, JpxDocument(lib.load(a)).update(one,"rules",lib.world) { it.clear() })
        assertEquals(active,published(a)); assertTrue(profiles().getValue(one).changed); assertFalse(profiles().getValue(two).changed)
        val export = parse(lib.load(a).source)
        assertEquals(listOf(one),export.modes); assertEquals(0,export.rules); assertFalse(JpxDocument(export).config.has("setting"))
        assertEquals(0,lib.save(parse(bundle))); assertEquals(0,lib.load(a).rules); assertEquals("Shield",profiles().getValue(one).nickname)
        lib.apply(a); assertNotEquals(active,published(a)); assertEquals(bBefore.source,published(b)); assertFalse(profiles().getValue(one).changed)
        lib.remove(a); assertNull(lib.catalog().manualId); assertEquals(setOf(two,blank),profiles().keys); assertEquals(bBefore,lib.load(b))
        assertEquals(1,lib.save(parse(bundle))); assertEquals(1,profiles().getValue(one).rules); assertNull(lib.catalog().manualId)
    }

    @Test fun alternativesWithSameFilenameAreIndependentWithinTheirAccountAndWorld() {
        lib.save(parse(bundle)); val original = profiles().getValue(one); lib.apply(original.id)
        val alternate = parse("""{"version":1,"$one":{}}"""); assertEquals(1,lib.save(alternate))
        val copy = lib.catalog().entries.single { it.mode==one && it.id!=original.id }; assertEquals(original.id, lib.catalog().manualId)
        lib.apply(copy.id); lib.remove(original.id); assertEquals(copy.id, lib.catalog().manualId)
        for (other in listOf(JpxLibrary(db,"other","Isekai"),JpxLibrary(db,"sample","Persistent"))) {
            assertTrue(other.catalog().entries.isEmpty()); assertTrue(runCatching { other.remove(copy.id) }.isFailure)
            assertEquals(3,other.save(parse(bundle))); assertFalse(other.catalog().manual)
        }
        assertEquals(lib.load(copy.id).source, published(copy.id))
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
            assertNull(lib.catalog().manualId)
            assertTrue(JpxDocument(lib.load(restored.id)).rules(staff, "rules")[0].asJsonObject["disabled"].asBoolean)
            lib.catalog().entries.forEach { lib.remove(it.id) }
        }
    }
    @Test fun routesAndPinsPersistIndependentlyAndNeverBindReusedRowIds() {
        lib.save(parse(bundle))
        val style = BattleStyle.OneHanded
        val arena = BattleScene.Arena
        val id = profiles().getValue(one).id
        val twoId = profiles().getValue(two).id
        lib.save(parse("""{"version":1,"$one":{"rules":[{"type":"item"}]}}"""))
        val alternate = lib.catalog().entries.single { it.id != id && it.mode == one }.id
        assertEquals(id, lib.catalog().resolve(style, arena)?.entry?.id)
        lib.assign(style, arena, alternate)
        assertEquals(MatchReason.Assigned, lib.catalog().resolve(style, arena)?.reason)
        lib.apply(twoId)
        assertEquals(twoId, lib.catalog().resolve(style, arena)?.entry?.id)
        assertTrue(runCatching { lib.assign(style, arena, twoId) }.isFailure)
        assertTrue(runCatching { JpxLibrary(db,"sample","Persistent").assign(style, arena, id) }.isFailure)
        JpxLibrary.Database(context, filename).use { reopened ->
            assertEquals(lib.catalog(), JpxLibrary(reopened, "sample", "Isekai").catalog())
        }
        lib.setAutomatic(true)
        assertEquals(alternate, lib.catalog().resolve(style, arena)?.entry?.id)
        assertEquals(id, lib.catalog().resolve(style, BattleScene.Tower)?.entry?.id)
        val revision = lib.revision.value
        repeat(100) { lib.catalog().resolve(style, arena) }
        assertEquals(revision, lib.revision.value)
        lib.setAutomatic(false, alternate); lib.remove(alternate)
        assertNull(lib.catalog().resolve(style, arena)); assertTrue(lib.catalog().manual)
        lib.save(parse("""{"version":1,"$one":{"rules":[{"type":"item"}]}}"""))
        assertTrue(lib.catalog().routes.isEmpty()); assertNull(lib.catalog().manualId)
        lib.setAutomatic(true)
        assertEquals(id, lib.catalog().resolve(style, arena)?.entry?.id)
    }

    @Test fun inBattleRuntimeReadsOnlyThePublishedRulesOfTheWinningStrategy() {
        lib.save(parse(bundle))
        val id = profiles().getValue(one).id
        val style = BattleStyle.OneHanded
        val scene = BattleScene.Item
        val start = lib.strategy(style, scene, true)
        assertEquals(MatchReason.General, start.match?.reason)
        assertEquals(lib.load(id), start.published)
        assertNull(lib.strategy(style, scene, false).published)
        val draft = JpxDocument(lib.load(id)).update(one, "rules", lib.world) { it[0].addProperty("disabled", true) }
        lib.update(id, draft)
        assertEquals(start.published, lib.strategy(style, scene, true).published)
        lib.apply(id)
        assertEquals(draft.source, lib.strategy(null, BattleScene.Tower, true).published?.source)
        lib.setAutomatic(true)
        assertEquals(draft.source, lib.strategy(style, scene, true).published?.source)
        assertNull(lib.strategy(null, scene, true).published)
    }

    @Test fun largePlusProfileRoundTripsWithoutCursorWindowTruncation() {
        val text = """{"schemaVersion":1,"kind":"battle","world":"Isekai","description":"keep","config":{"version":1,"$one":{"rules":[{"type":"custom","code":"${"測".repeat(698900)}"}]},"$blank":{}}}"""
        val src = parse(text); assertEquals(2,lib.save(src))
        val result = lib.load(profiles().getValue(one).id)
        val root = JsonParser.parseString(result.source).asJsonObject; assertEquals("battle-mode",root["kind"].asString); assertEquals("keep",root["description"].asString)
        assertEquals("Isekai",root["world"].asString); assertEquals(listOf(one),parse(result.source).modes)
        assertEquals(JpxDocument(src).config[one],JpxDocument(result).config[one]); lib.apply(profiles().getValue(one).id); assertEquals(profiles().getValue(one).id, lib.catalog().manualId)
    }
}
