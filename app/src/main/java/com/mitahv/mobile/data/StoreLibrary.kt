package com.mitahv.mobile.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest
import java.util.UUID

/** Store envelopes are kept verbatim. Compatible combat modes also get ordinary, unpublished editor drafts. */
internal class StoreLibrary(private val library: JpxLibrary, private val account: String, private val world: String) {
    private val scope = arrayOf(account, world)
    val storageNamespace = digest(account + "\u0000" + world)

    /** Same most recent copy as findSource, without loading profiles or computing draft revisions. */
    fun savedVersions(): JsonObject = library.read { sql ->
        JsonObject().apply {
            sql.rawQuery("SELECT issue, source FROM store_copies WHERE account=? AND world=? ORDER BY id DESC", scope).use { rows ->
                while (rows.moveToNext()) {
                    val issue = rows.getString(0)
                    if (!has(issue)) add(issue, JsonParser.parseString(rows.getString(1)).asJsonObject["sha256"])
                }
            }
        }
    }

    fun list(): JsonArray = library.read { sql ->
        JsonArray().apply {
            sql.rawQuery("SELECT id FROM store_copies WHERE account=? AND world=? ORDER BY id DESC", scope).use { cursor ->
                while (cursor.moveToNext()) add(requireNotNull(storeEntry(sql, cursor.getLong(0), false)))
            }
            // A linked single-mode copy already exposes its latest editable content above.
            val singleModes = sql.rawQuery("""SELECT m.profile FROM store_modes m JOIN store_copies c ON c.id=m.copy
                WHERE c.account=? AND c.world=? AND c.kind='battle-mode'""", scope).use { c ->
                buildSet { while (c.moveToNext()) add(c.getLong(0)) }
            }
            for (entry in library.catalog().entries) if (entry.rules > 0 && entry.id !in singleModes) add(profileEntry(entry, false))
        }
    }

    fun get(id: String): JsonObject? = library.read { sql ->
        val key = id.substringAfter(':').toLongOrNull() ?: return@read null
        when (id.substringBefore(':')) {
            "store" -> storeEntry(sql, key, true)
            "profile" -> library.catalog().entries.find { it.id == key }?.let { profileEntry(it, true) }
            else -> null
        }
    }

    private fun sourceKeys(source: JsonObject): Array<String> {
        val repository = requireNotNull(source["repository"]?.string)
        require(repository.equals("MeiYongAI/JPX-PLUS-Configs", true))
        val issue = requireNotNull(source["id"]?.toString()?.toLongOrNull()).also { require(it > 0) }
        return scope + arrayOf(repository.lowercase(), issue.toString())
    }

    fun findSource(source: JsonObject): JsonObject? = library.read { sql ->
        sourceCopy(sql, sourceKeys(source))?.let { storeEntry(sql, it, false) }
    }

    fun remove(id: String, expectedRevision: String) = library.transaction { sql ->
        require(id.startsWith("store:"))
        val copy = id.removePrefix("store:").toLong()
        val entry = requireNotNull(storeEntry(sql, copy, false))
        check(entry["revision"].asString == expectedRevision) { "Local copy changed" }
        for (profile in linked(sql, copy).values) {
            check(sql.delete("profiles", "account=? AND world=? AND id=?", scope + profile.toString()) == 1)
        }
        check(sql.delete("store_copies", "account=? AND world=? AND id=?", scope + copy.toString()) == 1)
    }

    private fun sourceCopy(sql: SQLiteDatabase, keys: Array<String>): Long? =
        sql.rawQuery("""SELECT id FROM store_copies WHERE account=? AND world=? AND repository=? AND issue=?
            ORDER BY id DESC LIMIT 1""", keys).use { if (it.moveToFirst()) it.getLong(0) else null }

    fun save(envelope: String, source: JsonObject, choice: String = "new", expectedId: String? = null,
             expectedRevision: String? = null): JsonObject {
        val keys = sourceKeys(source)
        require(source["sha256"]?.string?.matches(Regex("[0-9a-f]{64}")) == true)
        // Published store packages have a 60KB limit; this also bounds SQLite row and bridge sizes.
        require(envelope.toByteArray().size <= 64 * 1024)
        val root = JsonParser.parseString(envelope).asJsonObject
        require(root["schemaVersion"]?.toString() == "1")
        val title = requireNotNull(root["title"]?.string).also { require(it.isNotBlank() && it.length <= 100) }
        val targetWorld = requireNotNull(root["world"]?.string)
        require(targetWorld in setOf("Persistent", "Isekai", "Any"))
        val kind = root["kind"]?.string
        val draft = when (kind) {
            "battle-mode", "battle" -> JpxImport.parse(envelope, title, targetWorld)
            "stats" -> {
                val config = root.getAsJsonObject("config")
                require(!root.has("mode") && (config["statsVersion"]?.toString()?.toLongOrNull() ?: 0) >= 20260109)
                require(listOf("statsColumns", "combatRows", "revenueRows").any { config[it]?.isJsonArray == true })
                null
            }
            else -> throw IllegalArgumentException("Unknown store category")
        }
        return library.transaction { sql ->
            val existing = sourceCopy(sql, keys)
            val copyId = when (choice) {
                "new" -> { check(existing == null) { "Source already saved" }; null }
                "copy" -> null
                "update" -> {
                    val id = expectedId?.takeIf { it.startsWith("store:") }?.substringAfter(':')?.toLongOrNull()
                    requireNotNull(id)
                    val entry = requireNotNull(storeEntry(sql, id, false))
                    check(sourceKeys(entry.getAsJsonObject("source")).contentEquals(keys) &&
                        entry["revision"].asString == expectedRevision) { "Local copy changed" }
                    id
                }
                else -> throw IllegalArgumentException("Unknown save choice")
            }
            val data = ContentValues().apply {
                put("account", account); put("world", world); put("repository", keys[2]); put("issue", keys[3])
                put("source", source.toString()); put("envelope", envelope); put("kind", kind)
            }
            val id = if (copyId == null) sql.insertOrThrow("store_copies", null, data) else {
                check(sql.update("store_copies", data, "account=? AND world=? AND id=?", scope + copyId.toString()) == 1)
                copyId
            }
            val compatible = draft?.takeIf { targetWorld == "Any" || targetWorld == world }
            syncDrafts(sql, id, compatible)
            requireNotNull(storeEntry(sql, id, true))
        }
    }

    private fun linked(sql: SQLiteDatabase, id: Long): Map<String, Long> =
        sql.rawQuery("""SELECT p.slot,p.id FROM store_modes m JOIN profiles p ON p.id=m.profile
            WHERE m.copy=? AND p.account=? AND p.world=? ORDER BY p.slot""", arrayOf(id.toString()) + scope).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getLong(1)) }
        }

    private fun syncDrafts(sql: SQLiteDatabase, copy: Long, draft: JpxDraft?) {
        val before = linked(sql, copy)
        val doc = draft?.let(::JpxDocument)
        // Retain an existing profile when its author clears its last rule, so the empty draft can be applied.
        val modes = draft?.modes.orEmpty().filter { it in before || requireNotNull(doc).count(it) > 0 }
        // A removed mode may already be applied. Detach only its store link; its local profile remains usable.
        for ((mode, id) in before) if (mode !in modes)
            sql.delete("store_modes", "copy=? AND profile=?", arrayOf(copy.toString(), id.toString()))
        if (draft == null || doc == null) return
        for (mode in modes) {
            val id = before[mode] ?: run {
                val empty = JsonObject().apply {
                    add("battleVersion", doc.config["battleVersion"] ?: doc.config["version"])
                    add(mode, JsonObject().apply { add("rules", JsonArray()) })
                }
                val profile = sql.insertOrThrow("profiles", null, ContentValues().apply {
                    put("account", account); put("world", world); put("slot", mode); put("origin", draft.name)
                    put("nickname", draft.name.take(80)); put("import_hash", "store-" + UUID.randomUUID())
                    put("rules", 0); put("source", empty.toString())
                })
                sql.execSQL("INSERT INTO store_modes(copy,profile) VALUES(?,?)", arrayOf<Any>(copy, profile))
                profile
            }
            sql.execSQL("INSERT OR REPLACE INTO profile_edits(profile,source,rules) VALUES(?,?,?)",
                arrayOf<Any>(id, doc.export(mode), doc.count(mode)))
            sql.execSQL("DELETE FROM profile_edits WHERE profile=? AND source=(SELECT source FROM profiles WHERE id=?)", arrayOf<Any>(id,id))
        }
    }

    private fun profileEntry(entry: JpxEntry, full: Boolean): JsonObject {
        val draft = library.load(entry.id)
        val raw = JsonParser.parseString(JpxDocument(draft).export(entry.mode)).asJsonObject
        val wrapped = raw.has("config")
        val envelope = (if (wrapped) raw else JsonObject()).apply {
            if (!wrapped) {
                addProperty("schemaVersion", 1); addProperty("world", world); add("config", raw)
            }
            addProperty("kind", "battle-mode"); addProperty("mode", entry.mode)
            addProperty("title", entry.nickname.ifBlank { draft.name }.take(100))
            if (!has("description")) addProperty("description", "")
            if (!has("scriptVersion")) addProperty("scriptVersion", "Obelisk")
        }
        return objectEntry("profile:" + entry.id, digest(envelope.toString()), envelope, null, full)
    }

    private fun storeEntry(sql: SQLiteDatabase, id: Long, full: Boolean): JsonObject? =
        sql.rawQuery("SELECT envelope,source FROM store_copies WHERE account=? AND world=? AND id=?", scope + id.toString()).use { c ->
            if (!c.moveToFirst()) return@use null
            val raw = c.getString(0)
            val source = c.getString(1)
            val envelope = JsonParser.parseString(raw).asJsonObject
            val config = envelope.getAsJsonObject("config")
            val revision = buildString {
                append(digest(raw)); append(digest(source))
                for ((mode, profile) in linked(sql, id)) {
                    // A confirmed store update must not overwrite drafts edited since opening its dialog.
                    val draft = library.load(profile)
                    append(':'); append(profile); append(':'); append(digest(draft.source))
                    if (full) config.add(mode, JpxDocument(draft).config[mode])
                }
            }
            objectEntry("store:$id", digest(revision), envelope,
                JsonParser.parseString(source).asJsonObject, full)
        }

    private fun objectEntry(id: String, revision: String, envelope: JsonObject, source: JsonObject?, full: Boolean): JsonObject =
        JsonObject().apply {
            addProperty("id", id); addProperty("revision", revision)
            if (!full) envelope.remove("config")
            add("envelope", envelope)
            if (source != null) add("source", source)
        }

    companion object {
        private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

        internal fun open(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS store_copies(
                id INTEGER PRIMARY KEY, account TEXT NOT NULL, world TEXT NOT NULL,
                repository TEXT NOT NULL, issue INTEGER NOT NULL, kind TEXT NOT NULL,
                envelope TEXT NOT NULL, source TEXT NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS store_origin ON store_copies(account,world,repository,issue)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS store_modes(
                copy INTEGER NOT NULL REFERENCES store_copies(id) ON DELETE CASCADE,
                profile INTEGER PRIMARY KEY REFERENCES profiles(id) ON DELETE CASCADE)""")
        }
    }
}
