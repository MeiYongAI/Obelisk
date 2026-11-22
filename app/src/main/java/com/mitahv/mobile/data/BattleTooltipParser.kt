package com.mitahv.mobile.data

/** Decodes only literal arguments of known HV tooltip calls; never evaluates JavaScript. */
internal object BattleTooltipParser {
    fun arguments(source: String, function: String): List<String>? {
        val start = Regex("(?:^|;)\\s*battle\\.${Regex.escape(function)}\\s*\\(").find(source)?.range?.last?.plus(1)
            ?: return null
        val result = mutableListOf<String>()
        var index = start
        while (index < source.length) {
            while (index < source.length && source[index].isWhitespace()) index++
            if (index >= source.length) return null
            val value = StringBuilder()
            val quote = source[index]
            if (quote == '\'' || quote == '"') {
                index++
                var closed = false
                while (index < source.length) {
                    val char = source[index++]
                    if (char == quote) { closed = true; break }
                    if (char != '\\') { value.append(char); continue }
                    if (index >= source.length) return null
                    when (val escaped = source[index++]) {
                        'n' -> value.append('\n')
                        'r' -> value.append('\r')
                        't' -> value.append('\t')
                        '\\', '\'', '"' -> value.append(escaped)
                        else -> return null
                    }
                }
                if (!closed) return null
            } else {
                val begin = index
                while (index < source.length && source[index] != ',' && source[index] != ')') index++
                val literal = source.substring(begin, index).trim()
                if (!literal.matches(Regex("-?\\d+(?:\\.\\d+)?"))) return null
                value.append(literal)
            }
            result += value.toString()
            while (index < source.length && source[index].isWhitespace()) index++
            if (index >= source.length) return null
            when (source[index++]) {
                ')' -> return result
                ',' -> Unit
                else -> return null
            }
        }
        return null
    }
}
