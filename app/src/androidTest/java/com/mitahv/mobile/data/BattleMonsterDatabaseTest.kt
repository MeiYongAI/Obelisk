package com.mitahv.mobile.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BattleMonsterDatabaseTest {
    @Test fun dailySnapshotsAndScansAreWorldScopedAndKnownIdsNeverFallBackToNames() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = "monsters-${UUID.randomUUID()}.db"
        val fetched = mutableListOf<String>()
        var offline = false
        val old = MonsterRecord(17, "Twin", "Beast", "Cold", 1300, "2026-11-01")
        val db = BattleMonsterDatabase(context, file) { world ->
            fetched += world
            if (offline) throw IOException("Offline fixture")
            listOf(old.copy(plvl = if (world == "isekai") 800 else 1300), old.copy(monsterId = 30, monsterClass = "Dragonkin"))
        }
        val targets = listOf(MonsterTarget(1,"Twin",30), MonsterTarget(2,"Twin",null), MonsterTarget(3,"Twin",31))
        try {
            suspend fun snapshot(world: String) = db.records(world, targets).toList().last()
            val first = snapshot("Persistent")
            assertEquals(setOf(1,2), first.keys)
            assertEquals("Dragonkin", first[1]?.monsterClass)
            assertEquals(old, first[2])
            assertEquals(first,snapshot("Persistent"))
            assertEquals(800L,snapshot("Isekai")[2]?.plvl)
            assertEquals(listOf("persistent","isekai"),fetched)
            val scan = requireNotNull(BattleMonsterDatabase.scan("Scanning Twin...<table><tr><td>Monster Class:</td><td>Beast, Power Level 1500</td></tr><tr><td>Melee Attack:</td><td>Cold</td></tr></table>",mapOf("Twin" to 17L)))
            db.saveScan("Persistent", scan)
            assertEquals(scan, snapshot("Persistent")[2])
            assertEquals(800L,snapshot("Isekai")[2]?.plvl)
            db.writableDatabase.execSQL("UPDATE refresh SET day=NULL WHERE world='Persistent'")
            offline = true
            assertEquals(scan,snapshot("Persistent")[2])
            assertEquals("Dragonkin",snapshot("Persistent")[1]?.monsterClass)
            assertEquals(listOf("persistent","isekai","persistent"),fetched)
        } finally { db.close(); context.deleteDatabase(file) }
    }
}
