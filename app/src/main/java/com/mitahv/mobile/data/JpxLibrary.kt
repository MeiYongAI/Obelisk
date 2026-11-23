package com.mitahv.mobile.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

internal data class JpxEntry(val id: Long, val name: String, val modes: String, val rules: Int, val selected: Boolean, val changed: Boolean)

internal class JpxLibrary(private val db: Database, private val account: String, val world: String) {
    class Database(context: Context) : SQLiteOpenHelper(context.applicationContext, "battle-configs.db", null, 2) {
        private fun createApplied(db: SQLiteDatabase) = db.execSQL("""CREATE TABLE applied(account TEXT NOT NULL, world TEXT NOT NULL, slot TEXT NOT NULL,
            entry INTEGER NOT NULL, digest TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(account,world,slot))""")
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE configs (id INTEGER PRIMARY KEY, account TEXT NOT NULL, world TEXT NOT NULL,
                digest TEXT NOT NULL, name TEXT NOT NULL, modes TEXT NOT NULL, rules INTEGER NOT NULL,
                source TEXT NOT NULL, selected INTEGER NOT NULL DEFAULT 0, UNIQUE(account, world, digest))""")
            createApplied(db)
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            check(oldVersion == 1 && newVersion == 2)
            createApplied(db)
            db.rawQuery("SELECT account,world,id FROM configs WHERE selected=1", null).use { cursor ->
                while (cursor.moveToNext()) writeSnapshot(db, cursor.getString(0), cursor.getString(1), cursor.getLong(2), null)
            }
        }
    }
    private val scope = arrayOf(account, world)
    private val where = "account=? AND world=?"
    fun entries(): List<JpxEntry> = db.readableDatabase.rawQuery("""SELECT c.id,c.name,c.modes,c.rules,COUNT(a.slot),MAX(a.digest<>c.digest)
        FROM configs c LEFT JOIN applied a ON a.entry=c.id AND a.account=c.account AND a.world=c.world
        WHERE c.account=? AND c.world=? GROUP BY c.id ORDER BY c.id DESC""", scope
    ).use { c -> buildList { while (c.moveToNext()) add(JpxEntry(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4)>0, c.getInt(5)>0)) } }
    fun load(id: Long): JpxDraft = readDraft(db.readableDatabase, account, world, id)
    private fun values(draft: JpxDraft) = ContentValues().apply {
        put("digest", digest(draft.source)); put("name", draft.name); put("modes", draft.modes.joinToString(", ")); put("rules", draft.rules); put("source", draft.source)
    }
    fun save(draft: JpxDraft): Long = transaction { sql ->
        val values = values(draft)
        sql.rawQuery("SELECT id FROM configs WHERE $where AND digest=?", scope + values.getAsString("digest")).use { if (it.moveToFirst()) it.getLong(0) else null }
            ?: sql.insertOrThrow("configs", null, values.apply { put("account", account); put("world", world) })
    }
    fun update(id: Long, draft: JpxDraft) { check(db.writableDatabase.update("configs", values(draft), "$where AND id=?", scope + id.toString()) == 1) }
    fun apply(id: Long, mode: String? = null) = transaction { writeSnapshot(it, account, world, id, mode) }
    fun remove(id: Long) = transaction { sql ->
        sql.delete("applied", "$where AND entry=?", scope + id.toString()); sql.delete("configs", "$where AND id=?", scope + id.toString()); Unit
    }
    private fun <T> transaction(action: (SQLiteDatabase) -> T): T {
        val sql = db.writableDatabase
        sql.beginTransaction()
        try { return action(sql).also { sql.setTransactionSuccessful() } } finally { sql.endTransaction() }
    }
    private companion object {
        fun digest(source: String) = MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString("") { "%02x".format(it) }
        fun readDraft(sql: SQLiteDatabase, account: String, world: String, id: Long): JpxDraft {
            val args = arrayOf(account, world, id.toString())
            val where = " FROM configs WHERE account=? AND world=? AND id=?"
            val (name, length) = sql.rawQuery("SELECT name,length(CAST(source AS BLOB))$where", args).use {
                check(it.moveToFirst()); it.getString(0) to it.getInt(1)
            }
            // A legal 2 MiB export can exceed a single Android CursorWindow row.
            require(length in 1..JpxImport.MAX_BYTES)
            val out = ByteArrayOutputStream(length)
            while (out.size() < length) sql.rawQuery("SELECT substr(CAST(source AS BLOB),?,262144)$where", arrayOf((out.size()+1).toString()) + args).use {
                check(it.moveToFirst()); val bytes = it.getBlob(0); check(bytes.isNotEmpty()); out.write(bytes)
            }
            return out.toByteArray().inputStream().use { JpxImport.read(it, name, world) }
        }
        fun writeSnapshot(sql: SQLiteDatabase, account: String, world: String, id: Long, mode: String?) {
            val draft = readDraft(sql, account, world, id)
            val revision = digest(draft.source)
            for ((slot, payload) in JpxDocument(draft).application(mode)) sql.insertWithOnConflict("applied", null, ContentValues().apply {
                put("account", account); put("world", world); put("slot", slot); put("entry", id); put("digest", revision); put("payload", payload)
            }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
        }
    }
}
