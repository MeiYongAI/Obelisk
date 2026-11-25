package com.mitahv.mobile.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mitahv.mobile.model.BattleScene
import com.mitahv.mobile.model.BattleStyle
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StoreLibraryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val filename = "store-" + UUID.randomUUID() + ".db"
    private val db = JpxLibrary.Database(context, filename)
    private val library = JpxLibrary(db, "account", "Persistent")
    private val store = library.store
    private val mode = "Staff_General"
    private val source = JsonObject().apply {
        addProperty("repository", "MeiYongAI/JPX-PLUS-Configs"); addProperty("id", 17)
        addProperty("author", "tester"); addProperty("sha256", "a".repeat(64))
    }
    private fun envelope(action: String = "stop", world: String = "Persistent") =
        """{"schemaVersion":1,"kind":"battle-mode","title":"Config","description":"about","world":"$world","mode":"$mode","scriptVersion":"1","config":{"battleVersion":20251216,"$mode":{"rules":[{"type":"$action"}]}}}"""
    private fun rules(envelope: JsonObject) = envelope.getAsJsonObject("config").getAsJsonObject(mode).getAsJsonArray("rules")
    @After fun close() { db.close(); context.deleteDatabase(filename) }

    @Test fun downloadedModeAndUpdatesStayDraftUntilExplicitApplyAndDetectLocalEdits() {
        val first = store.save(envelope(), source)
        val key = first["id"].asString
        val id = library.catalog().entries.single().id
        assertEquals(0, library.load(id, published = true).rules)
        assertNull(library.strategy(BattleStyle.Staff, BattleScene.General, true).match)
        library.apply(id)
        val edited = JpxDocument(library.load(id)).update(mode, "rules", library.world) {
            it.add(JsonParser.parseString("""{"type":"target"}""").asJsonObject)
        }
        library.update(id, edited)
        val local = store.get(key)!!
        assertEquals(2, rules(local.getAsJsonObject("envelope")).size())
        assertEquals(1, store.list().size())
        val newer = source.deepCopy().apply { addProperty("sha256", "b".repeat(64)) }
        assertThrows(IllegalStateException::class.java) {
            store.save(envelope("normalAttack"), newer, "update", key, first["revision"].asString)
        }
        assertEquals(edited, library.load(id))
        val updated = store.save(envelope("normalAttack"), newer, "update", key, local["revision"].asString)
        assertEquals(key, updated["id"].asString)
        assertEquals("stop", JpxDocument(library.load(id, published = true)).rules(mode, "rules")[0].asJsonObject["type"].asString)
        assertEquals("normalAttack", JpxDocument(library.load(id)).rules(mode, "rules")[0].asJsonObject["type"].asString)
        assertThrows(IllegalStateException::class.java) { store.save(envelope(), source) }
        val copy = store.save(envelope(), source, "copy")
        assertNotEquals(key, copy["id"].asString)
        assertEquals(2, library.catalog().entries.size)
        assertThrows(IllegalStateException::class.java) { store.remove(key, first["revision"].asString) }
        store.remove(key, updated["revision"].asString)
        assertNull(store.get(key))
        assertEquals(1, library.catalog().entries.size)
        assertEquals(copy["id"], store.findSource(source)!!["id"])
    }

    @Test fun foreignWorldAndStatisticsAreStoredExactlyAndScopedByAccountAndWorld() {
        val foreign = envelope(world = "Isekai")
        val saved = store.save(foreign, source)
        assertEquals(JsonParser.parseString(foreign), saved["envelope"])
        assertTrue(library.catalog().entries.isEmpty())
        val stats = """{"schemaVersion":1,"kind":"stats","title":"Stats","description":"","world":"Any","scriptVersion":"1","config":{"statsVersion":20260109,"statsColumns":[{"id":"round","customName":"Rounds"}]}}"""
        val otherSource = source.deepCopy().apply { addProperty("id", 19) }
        val entry = store.save(stats, otherSource)
        assertEquals(2, store.list().size())
        assertEquals(JsonParser.parseString(stats), store.get(entry["id"].asString)!!["envelope"])
        assertEquals(saved["id"], store.findSource(source)!!["id"])
        for (other in listOf(JpxLibrary(db, "account", "Isekai"), JpxLibrary(db, "another", "Persistent"))) {
            assertEquals(0, other.store.list().size())
            assertNull(other.store.findSource(source))
            assertNull(other.store.get(saved["id"].asString))
        }
    }

    @Test fun multiModeEnvelopeKeepsGlobalOptionsAndEachApplicableModeBecomesAnIndependentDraft() {
        val multi = JsonParser.parseString(envelope()).asJsonObject.apply {
            addProperty("kind", "battle"); remove("mode"); addProperty("world", "Any")
            getAsJsonObject("config").apply {
                addProperty("showCooldowns", true)
                add("TwoHanded_Arena", get(mode).deepCopy())
            }
        }
        val entry = store.save(multi.toString(), source)
        assertEquals(multi, entry["envelope"])
        assertEquals(2, library.catalog().entries.size)
        assertTrue(library.catalog().entries.all { it.publishedRules == 0 && it.rules == 1 })
        val local = library.create("My draft", BattleStyle.Staff, BattleScene.Item)
        library.update(local, JpxDocument(library.load(local)).update("Staff_Item","rules",library.world) {
            it.add(JsonParser.parseString("""{"type":"stop"}""").asJsonObject)
        })
        val share = store.get("profile:$local")!!.getAsJsonObject("envelope")
        assertEquals("battle-mode", share["kind"].asString)
        assertEquals("Staff_Item", share["mode"].asString)
        assertEquals("My draft", share["title"].asString)
    }
}
