package com.mitahv.mobile.data

import org.json.JSONObject

/** Reads the JSON objects emitted by official HV scripts without evaluating script code. */
internal object HvScriptObjectParser {
    fun parse(source: String, name: String): JSONObject? {
        val start = Regex("\\b${Regex.escape(name)}\\s*=\\s*(?=\\{)")
            .find(source)?.range?.last?.plus(1) ?: return null
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in start until source.length) {
            val char = source[index]
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> if (--depth == 0) return runCatching { JSONObject(source.substring(start, index + 1)) }.getOrNull()
            }
        }
        return null
    }
}
