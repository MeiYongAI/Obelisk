package com.mitahv.mobile.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

internal data class JpxEntry(val id: Long, val mode: String, val origin: String, val nickname: String,
    val rules: Int, val selected: Boolean, val changed: Boolean)

internal class JpxLibrary(private val db: Database, private val account: String, val world: String) {
    class Database(context: Context, name: String = "battle-configs.db") : SQLiteOpenHelper(context.applicationContext, name, null, 3) {
        private fun createProfiles(sql: SQLiteDatabase) {
            sql.execSQL("""CREATE TABLE sources(id INTEGER PRIMARY KEY, account TEXT NOT NULL, world TEXT NOT NULL,
                digest TEXT NOT NULL, name TEXT NOT NULL, header TEXT NOT NULL, UNIQUE(account,world,digest))""")
            sql.execSQL("""CREATE TABLE profiles(id INTEGER PRIMARY KEY, origin INTEGER NOT NULL, slot TEXT NOT NULL,
                rules INTEGER NOT NULL, payload TEXT NOT NULL, nickname TEXT NOT NULL DEFAULT '', UNIQUE(origin,slot))""")
        }
        private fun createApplied(sql: SQLiteDatabase) = sql.execSQL("""CREATE TABLE applied(account TEXT NOT NULL, world TEXT NOT NULL, slot TEXT NOT NULL,
            entry INTEGER NOT NULL, digest TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(account,world,slot))""")
        override fun onCreate(db: SQLiteDatabase) { createProfiles(db); createApplied(db) }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            check(oldVersion in 1..2 && newVersion == 3)
            createProfiles(db)
            if (oldVersion == 1) createApplied(db)
            // Keep the original source ids: v2 applied payloads (including globals) may differ from today's drafts.
            db.rawQuery("SELECT id,account,world,name,digest,selected FROM configs ORDER BY id", null).use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0); val account = c.getString(1); val world = c.getString(2)
                    val text = readText(db, "source", "FROM configs WHERE id=?", arrayOf(id.toString()))
                    val doc = JpxDocument(JpxImport.read(text.byteInputStream(), c.getString(3), world))
                    insertSource(db, account, world, doc, c.getString(4), id)
                    insertProfiles(db, id, doc)
                    if (oldVersion == 1 && c.getInt(5) != 0) {
                        for ((slot, payload) in doc.application(null)) snapshot(db, account, world, id, slot, payload)
                    }
                }
            }
            db.execSQL("DROP TABLE configs")
        }
    }

    private val scope = arrayOf(account, world)
    private val from = "FROM profiles p JOIN sources s ON s.id=p.origin"
    private val owned = "s.account=? AND s.world=?"
    private val row = "$from WHERE $owned AND p.id=?"
    fun entries(): List<JpxEntry> = db.readableDatabase.rawQuery("""SELECT p.id,p.slot,s.name,p.nickname,p.rules,
        a.entry IS NOT NULL, a.payload<>p.payload $from
        LEFT JOIN applied a ON a.account=s.account AND a.world=s.world AND a.slot=p.slot AND a.entry=p.origin
        WHERE $owned ORDER BY (p.rules=0), s.id DESC, (p.slot NOT GLOB '*_General'), p.id""", scope
    ).use { c -> buildList { while (c.moveToNext()) add(JpxEntry(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4), c.getInt(5)!=0, c.getInt(6)!=0)) } }

    private data class Row(val origin: Long, val slot: String)
    private fun identity(sql: SQLiteDatabase, args: Array<String>) = sql.rawQuery("SELECT p.origin,p.slot $row", args).use {
        check(it.moveToFirst()); Row(it.getLong(0), it.getString(1))
    }

    fun load(id: Long): JpxDraft {
        val sql = db.readableDatabase; val args = scope + id.toString()
        val (name, mode) = sql.rawQuery("SELECT s.name,p.slot $row", args).use { check(it.moveToFirst()); it.getString(0) to it.getString(1) }
        val text = JpxDocument.modeSource(readText(sql,"s.header",row,args), mode, readText(sql,"p.payload",row,args))
        return JpxImport.readStoredMode(text, name, world)
    }

    /** Re-import fills missing slots from this source, never resetting edits or applying anything. */
    fun save(draft: JpxDraft): Int = transaction { sql ->
        val hash = digest(draft.source); val doc = JpxDocument(draft)
        val origin = sql.rawQuery("SELECT id FROM sources WHERE account=? AND world=? AND digest=?", scope + hash).use {
            if (it.moveToFirst()) it.getLong(0) else null
        } ?: insertSource(sql, account, world, doc, hash)
        insertProfiles(sql, origin, doc)
    }
    private val ownedIds = "id IN (SELECT p.id $row)"
    fun update(id: Long, draft: JpxDraft) {
        val mode = draft.modes.single()
        val values = ContentValues().apply { put("payload", JpxDocument(draft).config[mode].toString()); put("rules", draft.rules) }
        check(db.writableDatabase.update("profiles", values, "$ownedIds AND slot=?", scope + id.toString() + mode) == 1)
    }
    fun rename(id: Long, nickname: String) {
        val values = ContentValues().apply { put("nickname", nickname.trim().take(80)) }
        check(db.writableDatabase.update("profiles", values, ownedIds, scope + id.toString()) == 1)
    }
    fun apply(id: Long) = transaction { sql ->
        val args = scope + id.toString()
        val (origin, slot) = identity(sql, args)
        snapshot(sql, account, world, origin, slot, readText(sql, "p.payload", row, args))
    }
    fun remove(id: Long) = transaction { sql ->
        val args = scope + id.toString()
        val (origin, slot) = identity(sql, args)
        sql.delete("applied", "account=? AND world=? AND entry=? AND slot=?", scope + origin.toString() + slot)
        sql.delete("profiles", ownedIds, args)
        sql.delete("sources", "id=? AND NOT EXISTS (SELECT 1 FROM profiles WHERE origin=sources.id) AND NOT EXISTS (SELECT 1 FROM applied WHERE entry=sources.id)", arrayOf(origin.toString()))
        Unit
    }
    private fun <T> transaction(action: (SQLiteDatabase) -> T): T {
        val sql = db.writableDatabase
        sql.beginTransaction()
        try { return action(sql).also { sql.setTransactionSuccessful() } } finally { sql.endTransaction() }
    }
    private companion object {
        fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        fun insertSource(sql: SQLiteDatabase, account: String, world: String, doc: JpxDocument, hash: String, id: Long? = null) =
            sql.insertOrThrow("sources", null, ContentValues().apply {
                if (id != null) put("id", id)
                put("account", account); put("world", world); put("digest", hash); put("name", doc.draft.name); put("header", doc.header())
            })
        fun insertProfiles(sql: SQLiteDatabase, origin: Long, doc: JpxDocument): Int {
            val existing = sql.rawQuery("SELECT slot FROM profiles WHERE origin=?", arrayOf(origin.toString())).use { c -> buildSet { while(c.moveToNext()) add(c.getString(0)) } }
            val missing = doc.config.keySet().filter { it in doc.draft.modes && it !in existing }
            for (slot in missing) sql.insertOrThrow("profiles", null, ContentValues().apply {
                put("origin", origin); put("slot", slot); put("rules", doc.count(slot)); put("payload", doc.config[slot].toString())
            })
            return missing.size
        }
        fun snapshot(sql: SQLiteDatabase, account: String, world: String, origin: Long, slot: String, payload: String) {
            sql.insertWithOnConflict("applied", null, ContentValues().apply {
                put("account", account); put("world", world); put("slot", slot); put("entry", origin); put("digest", digest(payload)); put("payload", payload)
            }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
        }
        // Legal imports can exceed one Android CursorWindow row. Chunk bytes, not UTF-16 characters.
        fun readText(sql: SQLiteDatabase, column: String, from: String, args: Array<String>): String {
            val blob = "CAST($column AS BLOB)"
            val length = sql.rawQuery("SELECT length($blob) $from", args).use { check(it.moveToFirst()); it.getInt(0) }
            val out = ByteArrayOutputStream(length)
            while (out.size() < length) sql.rawQuery("SELECT substr($blob,?,262144) $from", arrayOf((out.size()+1).toString()) + args).use {
                check(it.moveToFirst()); val bytes = it.getBlob(0); check(bytes.isNotEmpty()); out.write(bytes)
            }
            return out.toString("UTF-8")
        }
    }
}
