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

    /** Stored once per import, so envelope metadata and legacy global options survive migration. */
    fun header(): String {
        val options = JsonObject().apply {
            config.entrySet().filter { it.key !in draft.modes }.forEach { add(it.key, it.value) }
        }
        if (root === config) return options.toString()
        return JsonObject().apply {
            root.entrySet().filter { it.key != "config" }.forEach { add(it.key, it.value) }
            add("config", options)
        }.toString()
    }

    /** JPX native single-mode export. A PLUS envelope keeps its world restriction and metadata. */
    fun export(mode: String? = null): String {
        if (mode == null) return draft.source
        require(mode in draft.modes)
        return modeSource(header(), mode, config[mode].toString())
    }

    companion object {
        fun modeSource(header: String, mode: String, payload: String): String {
            val root = JsonParser.parseString(header).asJsonObject
            val options = root.getAsJsonObject("config") ?: root
            val slice = JsonObject().apply {
                add("battleVersion", options["battleVersion"] ?: options["version"])
                add(mode, JsonParser.parseString(payload))
            }
            if (root === options) return slice.toString()
            root.add("config", slice)
            val originalKind = root.get("kind").asString
            val originalMode = root.get("mode")
            root.addProperty("kind", "battle-mode"); root.addProperty("mode", mode)
            val result = root.toString()
            if (result.toByteArray().size <= JpxImport.MAX_BYTES) return result
            // At the exact import limit the extra PLUS mode tag can make the export too big
            // to re-import. Its original battle envelope with just this slot is also valid JPX.
            root.addProperty("kind", originalKind)
            if (originalMode == null) root.remove("mode") else root.add("mode", originalMode)
            return root.toString()
        }
    }

    /** Each key is an independent applied slot. Single-mode files never overwrite global settings. */
    fun application(mode: String?): Map<String, String> {
        val single = mode ?: root.get("mode")?.takeIf { root.get("kind")?.asString == "battle-mode" }?.asString
        if (single != null) require(single in draft.modes)
        return config.entrySet().filter { (key, _) -> (single == null || key == single) && key !in setOf("battleVersion", "version") }
            .associate { it.key to it.value.toString() }
    }
}
