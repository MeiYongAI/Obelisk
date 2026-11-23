package com.mitahv.mobile.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Edits only the addressed rule array. Envelope metadata, global options and unknown fields round-trip. */
internal class JpxDocument(val draft: JpxDraft) {
    private val root = JsonParser.parseString(draft.source).asJsonObject
    val config: JsonObject = root.getAsJsonObject("config") ?: root
    fun groups(mode: String): List<String> {
        val obj = config.getAsJsonObject(mode)
        return when {
            obj.has("rules") || obj.size() == 0 -> listOf("rules")
            else -> listOf("supports", "attacks")
        } + obj.keySet().filter { it.startsWith("kb_") }
    }
    fun rules(mode: String, group: String) = config.getAsJsonObject(mode).getAsJsonArray(group) ?: JsonArray()
    fun count(mode: String) = groups(mode).sumOf { rules(mode, it).size() }

    fun update(mode: String, group: String, world: String, edit: (MutableList<JsonObject>) -> Unit): JpxDraft {
        require(mode in draft.modes && group in groups(mode))
        val copy = root.deepCopy()
        val slot = (copy.getAsJsonObject("config") ?: copy).getAsJsonObject(mode)
        val values = slot.getAsJsonArray(group)?.map { it.asJsonObject }?.toMutableList() ?: mutableListOf()
        edit(values)
        slot.add(group, JsonArray().apply { values.forEach { add(it) } })
        return copy.toString().byteInputStream().use { JpxImport.read(it, draft.name, world) }
    }

    /** Export only this mode. PLUS metadata (including the target world) stays with it. */
    fun export(mode: String): String {
        require(mode in draft.modes)
        val slice = JsonObject().apply {
            add("battleVersion", config["battleVersion"] ?: config["version"])
            add(mode, config[mode])
        }
        if (root === config) return slice.toString()

        val envelope = JsonObject().apply {
            root.entrySet().filter { it.key != "config" }.forEach { add(it.key, it.value) }
            add("config", slice)
        }
        if (root["kind"].asString == "battle-mode") return envelope.toString()
        envelope.addProperty("kind", "battle-mode")
        envelope.addProperty("mode", mode)
        val exported = envelope.toString()
        if (exported.toByteArray().size <= JpxImport.MAX_BYTES) return exported
        // A full-size PLUS file may have no room for the extra mode tag.
        envelope.addProperty("kind", "battle")
        envelope.remove("mode")
        return envelope.toString()
    }
}
