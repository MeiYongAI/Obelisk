package com.mitahv.mobile.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

internal data class JpxEntry(val id: Long, val name: String, val modes: String, val rules: Int, val selected: Boolean)

/** Only metadata is loaded when listing configurations. The payload is inert local data. */
internal class JpxLibrary(private val db: Database, private val account: String, val world: String) {
    class Database(context: Context) : SQLiteOpenHelper(context.applicationContext, "battle-configs.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE configs (id INTEGER PRIMARY KEY, account TEXT NOT NULL, world TEXT NOT NULL,
                digest TEXT NOT NULL, name TEXT NOT NULL, modes TEXT NOT NULL, rules INTEGER NOT NULL,
                source TEXT NOT NULL, selected INTEGER NOT NULL DEFAULT 0, UNIQUE(account, world, digest))""")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unsupported schema")
    }
    private val scope = arrayOf(account, world)
    private val where = "account=? AND world=?"
    fun entries(): List<JpxEntry> = db.readableDatabase.rawQuery(
        "SELECT id,name,modes,rules,selected FROM configs WHERE $where ORDER BY selected DESC,id DESC", scope
    ).use { c -> buildList { while (c.moveToNext()) add(JpxEntry(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4) != 0)) } }

    fun save(draft: JpxDraft) = transaction { sql ->
        val digest = MessageDigest.getInstance("SHA-256").digest(draft.source.toByteArray()).joinToString("") { "%02x".format(it) }
        val id = sql.rawQuery("SELECT id FROM configs WHERE $where AND digest=?", scope + digest).use { if (it.moveToFirst()) it.getLong(0) else null }
            ?: sql.insertOrThrow("configs", null, ContentValues().apply {
                put("account", account); put("world", world); put("digest", digest); put("name", draft.name)
                put("modes", draft.modes.joinToString(", ")); put("rules", draft.rules); put("source", draft.source)
            })
        select(sql, id)
    }
    fun select(id: Long) = transaction { select(it, id) }
    private fun select(sql: SQLiteDatabase, id: Long) {
        check(sql.update("configs", ContentValues().apply { put("selected", 1) }, "$where AND id=?", scope + id.toString()) == 1)
        sql.execSQL("UPDATE configs SET selected=0 WHERE $where AND id<>? AND selected=1", arrayOf<Any>(account, world, id))
    }
    fun remove(id: Long) { db.writableDatabase.delete("configs", "$where AND id=?", scope + id.toString()) }
    private fun transaction(action: (SQLiteDatabase) -> Unit) {
        val sql = db.writableDatabase
        sql.beginTransaction()
        try { action(sql); sql.setTransactionSuccessful() } finally { sql.endTransaction() }
    }
}
