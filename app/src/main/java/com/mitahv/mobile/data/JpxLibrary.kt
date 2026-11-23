package com.mitahv.mobile.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.mitahv.mobile.model.BattleScene
import com.mitahv.mobile.model.BattleStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

internal class JpxLibrary(private val db: Database, private val account: String, val world: String) {
    class Database(context: Context, name: String = "battle-profiles.db") : SQLiteOpenHelper(context.applicationContext, name, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE profiles(
                id INTEGER PRIMARY KEY, account TEXT NOT NULL, world TEXT NOT NULL,
                import_hash TEXT NOT NULL, origin TEXT NOT NULL, slot TEXT NOT NULL,
                nickname TEXT NOT NULL DEFAULT '', rules INTEGER NOT NULL,
                source TEXT NOT NULL,
                UNIQUE(account, world, import_hash, slot))""")
        }

        override fun onConfigure(db: SQLiteDatabase) = db.setForeignKeyConstraintsEnabled(true)

        // Routing and unpublished edits are separate from the imported profile format.
        override fun onOpen(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS profile_edits(
                profile INTEGER PRIMARY KEY REFERENCES profiles(id) ON DELETE CASCADE,
                source TEXT NOT NULL, rules INTEGER NOT NULL)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS strategy_routes(
                account TEXT NOT NULL, world TEXT NOT NULL, target TEXT NOT NULL,
                profile INTEGER REFERENCES profiles(id) ON DELETE SET NULL,
                PRIMARY KEY(account,world,target))""")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
            error("Unsupported battle profile schema: $oldVersion → $newVersion")
    }

    private val scope = arrayOf(account, world)
    private val owned = "account=? AND world=?"
    private val row = "$owned AND id=?"

    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()

    fun catalog(): JpxCatalog = read(::readCatalog)

    /** Read the chosen rules and routing metadata in the same transaction, outside the turn loop. */
    fun strategy(style: BattleStyle?, scene: BattleScene?, inBattle: Boolean): BattleStrategy = read { sql ->
        val catalog = readCatalog(sql)
        val match = catalog.resolve(style, scene)
        val published = if (inBattle && match != null) load(match.entry.id, published = true) else null
        BattleStrategy(catalog, style, scene, inBattle, match, published)
    }

    private fun <T> read(action: (SQLiteDatabase) -> T): T {
        val sql = db.readableDatabase
        sql.beginTransactionNonExclusive()
        try { return action(sql).also { sql.setTransactionSuccessful() } } finally { sql.endTransaction() }
    }

    private fun readCatalog(sql: SQLiteDatabase): JpxCatalog {
        val entries = sql.rawQuery("""SELECT id, slot, origin, nickname, COALESCE(e.rules,p.rules), p.rules,
            e.profile IS NOT NULL FROM profiles p LEFT JOIN profile_edits e ON e.profile=p.id WHERE $owned
            ORDER BY (COALESCE(e.rules,p.rules)=0), (slot NOT GLOB '*_General'), id DESC""", scope).use { c ->
            buildList {
                while (c.moveToNext()) add(JpxEntry(c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                    c.getInt(4), c.getInt(5), c.getInt(6) != 0))
            }
        }
        var manual = false
        var manualId: Long? = null
        val routes = buildMap {
            sql.rawQuery("SELECT target,profile FROM strategy_routes WHERE $owned", scope).use { c ->
                while (c.moveToNext()) {
                    val target = c.getString(0)
                    val id = if (c.isNull(1)) null else c.getLong(1)
                    if (target == MANUAL) { manual = true; manualId = id }
                    else if (id != null) put(target, id)
                }
            }
        }
        return JpxCatalog(entries, manual, manualId, routes)
    }

    fun load(id: Long, published: Boolean = false): JpxDraft {
        val sql = db.readableDatabase
        val source = if (published) "p.source" else "COALESCE(e.source,p.source)"
        val count = if (published) "p.rules" else "COALESCE(e.rules,p.rules)"
        val from = "FROM profiles p LEFT JOIN profile_edits e ON e.profile=p.id WHERE $row"
        val args = scope + id.toString()
        return sql.rawQuery("SELECT origin, slot, $count, length(CAST($source AS BLOB)) $from", args).use { c ->
            check(c.moveToFirst())
            // A valid import can exceed one Android CursorWindow row. Read UTF-8 in byte chunks.
            val length = c.getInt(3)
            val out = ByteArrayOutputStream(length)
            while (out.size() < length) {
                sql.rawQuery("SELECT substr(CAST($source AS BLOB),?,262144) $from",
                    arrayOf((out.size() + 1).toString()) + args).use { chunk ->
                    check(chunk.moveToFirst())
                    val bytes = chunk.getBlob(0)
                    check(bytes.isNotEmpty())
                    out.write(bytes)
                }
            }
            JpxDraft(c.getString(0), listOf(c.getString(1)), c.getInt(2), out.toString("UTF-8"))
        }
    }

    /** Re-import restores missing modes but leaves edited profiles alone. */
    fun save(draft: JpxDraft): Int = transaction { sql ->
        val hash = MessageDigest.getInstance("SHA-256").digest(draft.source.toByteArray()).joinToString("") { "%02x".format(it) }
        val doc = JpxDocument(draft)
        val existing = sql.rawQuery("SELECT slot FROM profiles WHERE $owned AND import_hash=?", scope + hash).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }
        val slots = doc.config.keySet().filter { it in draft.modes }
        // Only the exact original file proves that a row was imported empty (not emptied by its owner).
        for (slot in existing - slots.toSet()) {
            if (doc.config.has(slot) && doc.count(slot) == 0) sql.delete("profiles", """$owned AND import_hash=? AND slot=?
                AND rules=0 AND nickname='' AND id NOT IN (SELECT profile FROM profile_edits)
                AND id NOT IN (SELECT profile FROM strategy_routes WHERE profile IS NOT NULL)""", scope + arrayOf(hash, slot))
        }
        val missing = slots.filter { it !in existing && doc.count(it) > 0 }
        // The library shows new imports first; keep the file's mode order within each import.
        for (mode in missing.asReversed()) sql.insertOrThrow("profiles", null, ContentValues().apply {
            put("account", account); put("world", world); put("import_hash", hash); put("origin", draft.name)
            put("slot", mode); put("rules", doc.count(mode)); put("source", doc.export(mode))
        })
        missing.size
    }

    fun update(id: Long, draft: JpxDraft) = transaction { sql ->
        val args = scope + id.toString()
        require(sql.rawQuery("SELECT 1 FROM profiles WHERE $row AND slot=?", args + draft.modes.single()).use { it.moveToFirst() })
        sql.execSQL("INSERT OR REPLACE INTO profile_edits(profile,source,rules) VALUES (?,?,?)", arrayOf<Any>(id, draft.source, draft.rules))
        sql.execSQL("DELETE FROM profile_edits WHERE profile=? AND source=(SELECT source FROM profiles WHERE id=?)", arrayOf(id,id))
    }

    fun rename(id: Long, nickname: String) = transaction { sql ->
        val values = ContentValues().apply { put("nickname", nickname.trim().take(80)) }
        check(sql.update("profiles", values, row, scope + id.toString()) == 1)
    }

    /** Publish only this draft, and pin it independently of detected style and scene. */
    fun apply(id: Long) = transaction { sql ->
        val args = scope + id.toString()
        require(sql.rawQuery("SELECT 1 FROM profiles WHERE $row", args).use { it.moveToFirst() })
        sql.execSQL("""UPDATE profiles SET source=(SELECT source FROM profile_edits WHERE profile=id),
            rules=(SELECT rules FROM profile_edits WHERE profile=id) WHERE $row AND id IN (SELECT profile FROM profile_edits)""", args)
        sql.delete("profile_edits", "profile=?", arrayOf(id.toString()))
        // Publishing an empty draft makes it ineligible for automatic assignments.
        sql.execSQL("DELETE FROM strategy_routes WHERE $owned AND target<>? AND profile=? AND (SELECT rules FROM profiles WHERE id=?)=0",
            scope + arrayOf(MANUAL, id.toString(), id.toString()))
        setRoute(sql, MANUAL, id)
    }

    fun setAutomatic(automatic: Boolean, current: Long? = null) = transaction { sql ->
        if (automatic) sql.delete("strategy_routes", "$owned AND target=?", scope + MANUAL)
        else if (current != null) {
            require(sql.rawQuery("SELECT 1 FROM profiles WHERE $row", scope + current.toString()).use { it.moveToFirst() })
            setRoute(sql, MANUAL, current)
        } else sql.execSQL("INSERT OR IGNORE INTO strategy_routes(account,world,target) VALUES (?,?,?)", scope + MANUAL)
        Unit
    }

    fun assign(style: BattleStyle, scene: BattleScene, id: Long?) = transaction { sql ->
        val target = style.slot(scene)
        if (id == null) sql.delete("strategy_routes", "$owned AND target=?", scope + target)
        else {
            val mode = sql.rawQuery("SELECT slot FROM profiles WHERE $row AND rules>0", scope + id.toString()).use { c ->
                require(c.moveToFirst()); c.getString(0)
            }
            require(mode.substringBeforeLast('_') == style.key)
            setRoute(sql, target, id)
        }
        Unit
    }

    private fun setRoute(sql: SQLiteDatabase, target: String, id: Long) = sql.execSQL(
        "INSERT OR REPLACE INTO strategy_routes(account,world,target,profile) VALUES (?,?,?,?)", arrayOf<Any>(account, world, target, id))

    fun remove(id: Long) = transaction { sql -> check(sql.delete("profiles", row, scope + id.toString()) == 1) }

    private fun <T> transaction(action: (SQLiteDatabase) -> T): T {
        val sql = db.writableDatabase
        sql.beginTransaction()
        val result = try { action(sql).also { sql.setTransactionSuccessful() } } finally { sql.endTransaction() }
        changes.update { it + 1 }
        return result
    }

    private companion object { const val MANUAL = "@manual" }
}
