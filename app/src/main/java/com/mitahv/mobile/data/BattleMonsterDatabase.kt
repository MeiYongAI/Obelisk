package com.mitahv.mobile.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.mitahv.mobile.model.BattlePage
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.io.Reader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

internal data class MonsterRecord(val monsterId: Long, val monsterName: String, val monsterClass: String,
    val attack: String, val plvl: Long, val lastUpdate: String) {
    fun daysSince(today: LocalDate = LocalDate.now(ZoneOffset.UTC)): Long =
        // JPX daysSince appends midnight UTC to the supplied date. Its null result for an
        // ISO timestamp (e.g. a local Scan) coerces to zero in the range comparison.
        runCatching { today.toEpochDay() - LocalDate.parse(lastUpdate).toEpochDay() }.getOrDefault(0L)
}

internal data class MonsterTarget(val slot: Int, val name: String, val id: Long?)

internal class BattleMonsterLookup(private val db: BattleMonsterDatabase, private val world: String, private val scope: CoroutineScope) {
    private var targets = emptyList<MonsterTarget>()
    private var revision = -1L
    private var nextLookupAt = 0L
    private var result: StateFlow<Map<Int, MonsterRecord>?> = MutableStateFlow(null)
    @Synchronized fun start(page: BattlePage): StateFlow<Map<Int, MonsterRecord>?> {
        val current = page.enemies.map { MonsterTarget(it.target, it.name, page.round.monsterIds[it.name]) }
        val change = db.scanRevision.get()
        val now = System.currentTimeMillis()
        if (current != targets || revision != change || now >= nextLookupAt) {
            targets = current
            revision = change
            // Expiring only this small in-memory result permits retry after an offline first look.
            nextLookupAt = now + 5 * 60_000
            result = db.records(world, current).catch { emit(emptyMap()) }.stateIn(scope, SharingStarted.Eagerly, null)
        }
        return result
    }
}

/** World snapshots from the public Sukka Monster Database used by JPX-PLUS. */
internal class BattleMonsterDatabase(context: Context, name: String = "battle-monsters.db",
    private val fetch: (String) -> List<MonsterRecord> = ::download) : SQLiteOpenHelper(context, name, null, 1) {
    init { setWriteAheadLoggingEnabled(true) }
    val scanRevision = java.util.concurrent.atomic.AtomicLong(0)
    private val refreshLocks = mapOf("Persistent" to Mutex(), "Isekai" to Mutex())

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE monsters(world TEXT, id INTEGER, scan INTEGER, name TEXT, class TEXT, attack TEXT,
            power INTEGER, updated TEXT, PRIMARY KEY(world,id,scan))""")
        db.execSQL("CREATE INDEX monster_names ON monsters(world,name,id)")
        db.execSQL("CREATE TABLE refresh(world TEXT PRIMARY KEY, day TEXT, retry INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unknown monster cache schema")

    fun records(world: String, targets: List<MonsterTarget>) = flow {
        require(world in setOf("Persistent", "Isekai"))
        val cached = read(world, targets)
        if (cached.isNotEmpty()) emit(cached)
        refreshLocks.getValue(world).withLock {
            if (claimRefresh(world)) {
                val rows = try {
                    fetch(world.lowercase())
                } catch (_: IOException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                } catch (_: com.google.gson.JsonParseException) {
                    null
                }
                if (rows != null) replaceSnapshot(world, rows)
            }
        }
        emit(read(world, targets))
    }.flowOn(Dispatchers.IO)

    private fun replaceSnapshot(world: String, rows: List<MonsterRecord>) {
        val sql = writableDatabase
        sql.beginTransactionNonExclusive()
        try {
            sql.delete("monsters", "world=? AND scan=0", arrayOf(world))
            sql.compileStatement("INSERT INTO monsters VALUES(?,?,0,?,?,?,?,?)").use { insert ->
                for (r in rows) {
                    insert.bindString(1, world)
                    insert.bindLong(2, r.monsterId)
                    insert.bindString(3, r.monsterName)
                    insert.bindString(4, r.monsterClass)
                    insert.bindString(5, r.attack)
                    insert.bindLong(6, r.plvl)
                    insert.bindString(7, r.lastUpdate)
                    insert.executeInsert()
                }
            }
            sql.execSQL("UPDATE refresh SET day=?,retry=0 WHERE world=?", arrayOf(LocalDate.now(ZoneOffset.UTC).toString(), world))
            sql.setTransactionSuccessful()
        } finally { sql.endTransaction() }
    }

    private fun claimRefresh(world: String): Boolean {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransactionNonExclusive()
        try {
            val claimed = db.rawQuery("SELECT day,retry FROM refresh WHERE world=?", arrayOf(world)).use {
                !it.moveToFirst() || (it.getString(0) != LocalDate.now(ZoneOffset.UTC).toString() && it.getLong(1) <= now)
            }
            if (claimed) db.execSQL("INSERT OR REPLACE INTO refresh(world,retry) VALUES(?,?)", arrayOf<Any>(world, now + 300000))
            db.setTransactionSuccessful()
            return claimed
        } finally { db.endTransaction() }
    }

    fun read(world: String, targets: List<MonsterTarget>): Map<Int, MonsterRecord> = buildMap {
        for (target in targets) {
            val field = if (target.id != null) "id" else "name"
            val value = target.id?.toString() ?: target.name
            // Each upstream name index returns the lowest ID. A newer local Scan wins.
            val candidates = (0..1).mapNotNull { scan ->
                readableDatabase.rawQuery("SELECT id,name,class,attack,power,updated FROM monsters WHERE world=? AND $field=? AND scan=? ORDER BY id LIMIT 1",
                    arrayOf(world, value, scan.toString())).use {
                    if (it.moveToFirst()) MonsterRecord(it.getLong(0), it.getString(1), it.getString(2), it.getString(3), it.getLong(4), it.getString(5)) else null
                }
            }
            candidates.asReversed().maxByOrNull { timestamp(it.lastUpdate) }?.let { put(target.slot, it) }
        }
    }

    fun saveScan(world: String, row: MonsterRecord) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO monsters VALUES(?,?,1,?,?,?,?,?)",
            arrayOf<Any>(world, row.monsterId, row.monsterName, row.monsterClass, row.attack, row.plvl, row.lastUpdate))
        scanRevision.incrementAndGet()
    }

    companion object {
        private val classes = "Arthropod Avion Beast Celestial Daimon Dragonkin Elemental Giant Humanoid Mechanoid Reptilian Sprite Undead Common Rare Legendary Ultimate".split(" ").toSet()
        private val attacks = "Piercing Crushing Slashing Fire Cold Wind Elec Holy Dark Void".split(" ").toSet()
        private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
        @Volatile private var instance: BattleMonsterDatabase? = null
        fun shared(context: Context): BattleMonsterDatabase = instance ?: synchronized(this) {
            instance ?: BattleMonsterDatabase(context.applicationContext).also { instance = it }
        }
        private fun timestamp(value: String): Instant = if (value.length == 10)
            LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant() else Instant.parse(value)

        private fun download(world: String): List<MonsterRecord> {
            val req = Request.Builder().url("https://hv-monsterdb-data.skk.moe/$world.json").build()
            client.newCall(req).execute().use { response ->
                if (response.code != 200) throw IOException("Monster database HTTP ${response.code}")
                return parse(requireNotNull(response.body).charStream())
            }
        }

        internal fun parse(input: Reader): List<MonsterRecord> {
            val rows = ArrayList<MonsterRecord>()
            val ids = HashSet<Long>()
            var count = 0
            JsonReader(input).use { reader ->
                require(reader.peek() == JsonToken.BEGIN_ARRAY)
                reader.beginArray()
                while (reader.hasNext()) {
                    require(++count <= 200000)
                    val obj = JsonParser.parseReader(reader) as? JsonObject ?: continue
                    fun integer(key: String) = obj[key]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asString?.toLongOrNull()
                    val id = integer("monsterId")?.takeIf { it > 0 } ?: continue
                    val name = obj["monsterName"].string?.takeIf { it.isNotBlank() && it.length <= 256 } ?: continue
                    val cls = obj["monsterClass"].string?.takeIf(classes::contains) ?: continue
                    val atk = obj["attack"].string?.takeIf(attacks::contains) ?: continue
                    val power = integer("plvl")?.takeIf { it >= 0 } ?: continue
                    val updated = obj["lastUpdate"].string?.takeIf { runCatching { timestamp(it) }.isSuccess } ?: continue
                    require(ids.add(id))
                    rows += MonsterRecord(id, name, cls, atk, power, updated)
                }
                reader.endArray()
                require(reader.peek() == JsonToken.END_DOCUMENT && count > 0 && rows.size >= count * .95)
            }
            return rows
        }

        private val scanName = Regex("""Scanning\s+(.+?)\.\.\.""")
        private val scanClass = Regex("""([A-Za-z]+)(?:,\s*Power Level (\d+))?""")
        internal fun scan(html: String, ids: Map<String,Long>): MonsterRecord? {
            if ("Scanning" !in html || ids.isEmpty()) return null
            val doc = Jsoup.parseBodyFragment(html)
            val name = scanName.find(doc.text())?.groupValues?.get(1)?.trim() ?: return null
            val id = ids[name] ?: return null
            val fields = doc.select("td").associate { it.text().trimEnd(':') to it.nextElementSibling()?.text() }
            val cls = scanClass.matchEntire(fields["Monster Class"].orEmpty()) ?: return null
            val atk = (fields["Melee Attack"] ?: fields["Attack Type"] ?: fields["Attack"])?.takeWhile(Char::isLetter) ?: return null
            if (cls.groupValues[1] !in classes || atk !in attacks) return null
            return MonsterRecord(id, name, cls.groupValues[1], atk, cls.groupValues[2].ifEmpty { "0" }.toLongOrNull() ?: return null, Instant.now().toString())
        }
    }
}
