package com.mitahv.mobile.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

internal data class JpxEntry(
    val id: Long, val mode: String, val origin: String, val nickname: String,
    val rules: Int, val selected: Boolean, val changed: Boolean,
)

internal class JpxLibrary(private val db: Database, private val account: String, val world: String) {
    class Database(context: Context, name: String = "battle-profiles.db") : SQLiteOpenHelper(context.applicationContext, name, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE profiles(
                id INTEGER PRIMARY KEY, account TEXT NOT NULL, world TEXT NOT NULL,
                import_hash TEXT NOT NULL, origin TEXT NOT NULL, slot TEXT NOT NULL,
                nickname TEXT NOT NULL DEFAULT '', rules INTEGER NOT NULL,
                source TEXT NOT NULL, applied_source TEXT,
                UNIQUE(account, world, import_hash, slot))""")
            db.execSQL("""CREATE UNIQUE INDEX active_mode ON profiles(account, world, slot)
                WHERE applied_source IS NOT NULL""")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
            error("Unsupported battle profile schema: $oldVersion → $newVersion")
    }

    private val scope = arrayOf(account, world)
    private val owned = "account=? AND world=?"
    private val row = "$owned AND id=?"

    fun entries(): List<JpxEntry> = db.readableDatabase.rawQuery("""SELECT id, slot, origin, nickname, rules,
        applied_source IS NOT NULL, applied_source <> source FROM profiles WHERE $owned
        ORDER BY (rules=0), (slot NOT GLOB '*_General'), id DESC""", scope).use { c ->
        buildList {
            while (c.moveToNext()) add(JpxEntry(c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                c.getInt(4), c.getInt(5) != 0, c.getInt(6) != 0))
        }
    }

    fun load(id: Long): JpxDraft {
        val sql = db.readableDatabase
        val args = scope + id.toString()
        return sql.rawQuery("SELECT origin, slot, rules, length(CAST(source AS BLOB)) FROM profiles WHERE $row", args).use { c ->
            check(c.moveToFirst())
            // A valid import can exceed one Android CursorWindow row. Read UTF-8 in byte chunks.
            val length = c.getInt(3)
            val out = ByteArrayOutputStream(length)
            while (out.size() < length) {
                sql.rawQuery("SELECT substr(CAST(source AS BLOB),?,262144) FROM profiles WHERE $row",
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
        val existing = sql.rawQuery("SELECT slot FROM profiles WHERE $owned AND import_hash=?", scope + hash).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }
        val doc = JpxDocument(draft)
        val missing = doc.config.keySet().filter { it in draft.modes && it !in existing }
        // The library shows new imports first; keep the file's mode order within each import.
        for (mode in missing.asReversed()) sql.insertOrThrow("profiles", null, ContentValues().apply {
            put("account", account); put("world", world); put("import_hash", hash); put("origin", draft.name)
            put("slot", mode); put("rules", doc.count(mode)); put("source", doc.export(mode))
        })
        missing.size
    }

    fun update(id: Long, draft: JpxDraft) {
        val values = ContentValues().apply { put("source", draft.source); put("rules", draft.rules) }
        check(db.writableDatabase.update("profiles", values, "$row AND slot=?", scope + id.toString() + draft.modes.single()) == 1)
    }

    fun rename(id: Long, nickname: String) {
        val values = ContentValues().apply { put("nickname", nickname.trim().take(80)) }
        check(db.writableDatabase.update("profiles", values, row, scope + id.toString()) == 1)
    }

    fun apply(id: Long) = transaction { sql ->
        val args = scope + id.toString()
        val slot = sql.rawQuery("SELECT slot FROM profiles WHERE $row", args).use { c ->
            check(c.moveToFirst()); c.getString(0)
        }
        sql.execSQL("UPDATE profiles SET applied_source=NULL WHERE $owned AND slot=? AND applied_source IS NOT NULL", scope + slot)
        sql.execSQL("UPDATE profiles SET applied_source=source WHERE $row", args)
    }

    fun remove(id: Long) { check(db.writableDatabase.delete("profiles", row, scope + id.toString()) == 1) }

    private fun <T> transaction(action: (SQLiteDatabase) -> T): T {
        val sql = db.writableDatabase
        sql.beginTransaction()
        try { return action(sql).also { sql.setTransactionSuccessful() } } finally { sql.endTransaction() }
    }
}
