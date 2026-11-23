package com.mitahv.mobile.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStream

internal enum class JpxEdition(val directory: String, val label: String) {
    Original("jpx", "JPX 2026.11.11"), Plus("jpx-plus", "JPX-PLUS 2026.11.20.1");

    fun asset(section: String) = "jpx-defaults/$directory/$section.json"
}

internal class JpxDefaults(val edition: JpxEdition, input: InputStream, private val world: String) {
    val full = JpxImport.read(input, edition.label, world)
    private val config = JsonParser.parseString(full.source).asJsonObject
    val ruleCounts = full.modes.associateWith { config.getAsJsonObject(it).getAsJsonArray("rules").size() }

    /** A single-mode export deliberately omits global settings belonging to the whole preset. */
    fun mode(key: String): JpxDraft {
        require(key in ruleCounts)
        val envelope = JsonObject().apply {
            addProperty("schemaVersion", 1); addProperty("kind", "battle-mode"); addProperty("world", "Any")
            addProperty("title", "${edition.label} · $key"); addProperty("mode", key)
            add("config", JsonObject().apply { add("battleVersion", config["battleVersion"]); add(key, config[key]) })
        }
        return envelope.toString().byteInputStream().use { JpxImport.read(it, edition.label, world) }
    }
}
