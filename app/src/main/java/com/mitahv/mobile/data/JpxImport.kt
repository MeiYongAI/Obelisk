package com.mitahv.mobile.data

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer

internal data class JpxDraft(val name: String, val modes: List<String>, val rules: Int, val source: String)
internal class WrongConfigWorld : IllegalArgumentException()
internal class OversizedConfig : IllegalArgumentException()

internal object JpxImport {
    const val MAX_BYTES = 2 * 1024 * 1024
    private val modeKey = Regex("(?:OneHanded|1H_Mage|TwoHanded|2H_Mage|DualWielding|DW_Mage|NitenIchiryu|NI_Mage|Staff|Unarmed)_(?:General|Arena|Encounter|Colosseum|Battle1000|Item|Tower)")
    private fun JsonElement?.text() = this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    fun read(input: InputStream, filename: String, world: String): JpxDraft {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (out.size() + n > MAX_BYTES) throw OversizedConfig()
            out.write(buf, 0, n)
        }
        val source = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(out.toByteArray())).toString().removePrefix("\uFEFF")
        return parse(source, filename, world)
    }

    // A PLUS single-mode envelope can be a few bytes larger than its original full export.
    // Bound external streams at import, not a document reconstructed from validated SQLite data.
    fun readStoredMode(source: String, filename: String, world: String) = parse(source, filename, world).also { it.modes.single() }

    private fun parse(source: String, filename: String, world: String): JpxDraft {
        val root = JsonReader(source.reader()).use { reader ->
            reader.strictness = Strictness.STRICT
            JsonParser.parseReader(reader).also { require(it.isJsonObject && reader.peek() == JsonToken.END_DOCUMENT) }.asJsonObject
        }
        val wrapped = root.has("kind") || root.has("schemaVersion") || root.has("config")
        val config = if (wrapped) {
            require(root["schemaVersion"]?.toString() == "1" && root["kind"].text() in setOf("battle", "battle-mode"))
            val target = root["world"].text()
            require(target in setOf("Any", "Persistent", "Isekai"))
            if (target != "Any" && target != world) throw WrongConfigWorld()
            requireNotNull(root["config"]?.takeIf { it.isJsonObject }?.asJsonObject)
        } else root
        val version = config["battleVersion"] ?: config["version"]
        require(version != null && version.isJsonPrimitive && (version.asString.toLongOrNull() ?: 0) > 0)
        val modes = config.keySet().filter { modeKey.matches(it) }.sorted()
        require(modes.isNotEmpty())
        if (wrapped && root["kind"].text() == "battle-mode") require(modes.size == 1 && modes.single() == root["mode"].text())
        val rules = modes.sumOf { key -> countRules(requireNotNull(config[key].takeIf { it.isJsonObject }?.asJsonObject)) }
        val name = (if (wrapped) root["title"].text() else null)?.takeIf { it.isNotBlank() } ?: filename
        return JpxDraft(name.trim().take(120).ifBlank { "JPX" }, modes, rules, source)
    }

    private fun countRules(mode: JsonObject): Int {
        if (mode.size() == 0) return 0 // Explicit empty scene, e.g. General fallback.
        val groups = if (mode.has("rules")) listOf("rules") else listOf("supports", "attacks")
        return (groups + mode.keySet().filter { it.startsWith("kb_") }).sumOf { key ->
            val rules = requireNotNull(mode[key]?.takeIf { it.isJsonArray }?.asJsonArray)
            for (rule in rules) {
                require(rule.isJsonObject && !rule.asJsonObject["type"].text().isNullOrBlank())
                rule.asJsonObject["conditions"]?.let { conditions ->
                    require(conditions.isJsonArray && conditions.asJsonArray.all { it.isJsonObject && !it.asJsonObject["key"].text().isNullOrBlank() })
                }
            }
            rules.size()
        }
    }
}
