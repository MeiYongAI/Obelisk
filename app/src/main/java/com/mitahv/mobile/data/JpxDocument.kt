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

    /** JPX native single-mode export. A PLUS envelope keeps its world restriction and metadata. */
    fun export(mode: String? = null): String {
        if (mode == null) return draft.source
        require(mode in draft.modes)
        val slice = JsonObject().apply {
            add("battleVersion", config["battleVersion"] ?: config["version"])
            add(mode, config[mode])
        }
        return if (config === root) slice.toString() else root.deepCopy().apply {
            addProperty("kind", "battle-mode"); addProperty("mode", mode); add("config", slice)
        }.toString()
    }

    /** Each key is an independent applied slot. Single-mode files never overwrite global settings. */
    fun application(mode: String?): Map<String, String> {
        val single = mode ?: root.get("mode")?.takeIf { root.get("kind")?.asString == "battle-mode" }?.asString
        if (single != null) require(single in draft.modes)
        return config.entrySet().filter { (key, _) -> (single == null || key == single) && key !in setOf("battleVersion", "version") }
            .associate { it.key to it.value.toString() }
    }
}
