package com.mitahv.mobile.i18n

import java.net.URI
import java.util.Locale
import org.json.JSONObject

/** Display-only translations. Editable names and user-authored text must stay raw. */
class HvTextTranslator(json: String) {
    private data class Rule(
        val value: String,
        val text: String?,
        val pattern: Regex?,
        val translatedGroups: Set<Int>,
        val actorGroups: Set<Int>,
        val compoundGroups: Set<Int>,
    )
    private data class Group(
        val priority: Int,
        val conditions: List<Regex>,
        val routes: List<String>,
        val battleOnly: Boolean,
        val rules: List<Rule>,
    )
    private data class Span(val text: String, val translated: Boolean = false)
    private data class Translations(val text: (String) -> String, val battleLog: (String) -> String)

    private val groups: List<Group> = JSONObject(json).getJSONArray("groups").let { data ->
        (0 until data.length()).map { index ->
            val group = data.getJSONObject(index)
            val conditions = group.optJSONArray("conditions")
            val routes = group.optJSONArray("routes")
            val rules = group.getJSONArray("rules")
            Group(
                priority = group.optInt("priority", 1),
                conditions = if (conditions == null) emptyList() else (0 until conditions.length()).map {
                    val condition = conditions.getJSONObject(it)
                    regex(condition.getString("pattern"), condition.optString("flags"))
                },
                routes = if (routes == null) emptyList() else (0 until routes.length()).map { routes.getString(it) },
                battleOnly = group.optBoolean("battleOnly"),
                rules = (0 until rules.length()).map {
                    val rule = rules.getJSONObject(it)
                    val captures = rule.optJSONArray("translateGroups")
                    val actors = rule.optJSONArray("actorGroups")
                    val compounds = rule.optJSONArray("compoundGroups")
                    Rule(
                        rule.getString("value"),
                        rule.optString("text").takeIf(String::isNotEmpty),
                        rule.optString("pattern").takeIf(String::isNotEmpty)?.let { pattern ->
                            regex(pattern, rule.optString("flags"))
                        },
                        if (captures == null) emptySet() else (0 until captures.length()).map { captures.getInt(it) }.toSet(),
                        if (actors == null) emptySet() else (0 until actors.length()).map { actors.getInt(it) }.toSet(),
                        if (compounds == null) emptySet() else (0 until compounds.length()).map { compounds.getInt(it) }.toSet(),
                    )
                },
            )
        }.sortedByDescending(Group::priority) // Stable: source order breaks equal-priority ties.
    }
    private val pageTranslations = mutableMapOf<List<Int>, Translations>()
    private val defaultTranslation by lazy { translatorFor() }

    fun translate(text: String): String = defaultTranslation(text)

    /** Build once when the rendered page changes; never compile the dictionary per label. */
    fun translatorFor(pageUrl: String? = null, battle: Boolean = false): (String) -> String = translationsFor(pageUrl, battle).text

    fun battleLogTranslatorFor(pageUrl: String? = null): (String) -> String = translationsFor(pageUrl, true).battleLog

    private fun translationsFor(pageUrl: String?, battle: Boolean): Translations {
        val url = pageUrl ?: "https://hentaiverse.org/"
        val query = URI(url).rawQuery.orEmpty().split('&').associate {
            it.substringBefore('=') to it.substringAfter('=', "")
        }
        val route = "${query["s"].orEmpty()}/${query["ss"].orEmpty()}"
        val matching = groups.indices.filter { index ->
            val group = groups[index]
            (!group.battleOnly || battle) && when {
                group.routes.isNotEmpty() -> group.routes.any { it == route || it.endsWith("/*") && route.startsWith(it.dropLast(1)) }
                group.conditions.isNotEmpty() -> group.conditions.any { it.containsMatchIn(url) }
                else -> true
            }
        }
        return pageTranslations.getOrPut(matching) {
            val scoped = ScopedTranslator(matching.flatMap { groups[it].rules }, battle)
            Translations(scoped::translate, scoped::translateBattleLog)
        }
    }

    private class ScopedTranslator(rules: List<Rule>, private val battle: Boolean) {
        private val words: Map<String, String>
        private val singleLetterLabels: Map<String, String>
        private val wordPattern: Regex?
        private val sentences = rules.filter { it.pattern != null }
        private val wholeSentences = sentences.filter { it.pattern!!.pattern.startsWith('^') && it.pattern.pattern.endsWith('$') }

        init {
            val byText = linkedMapOf<String, Pair<String, String>>()
            val byLetter = linkedMapOf<String, String>()
            rules.forEach { rule ->
                rule.text?.let { text ->
                    // Attribute initials are complete, case-sensitive field labels.
                    // They must not rewrite English a/I, units, or the A in N/A.
                    if (text.length == 1 && text[0] in 'A'..'Z') byLetter.putIfAbsent(text, rule.value)
                    else byText.putIfAbsent(key(text), text to rule.value)
                }
            }
            singleLetterLabels = byLetter
            words = byText.mapValues { it.value.second }
            wordPattern = byText.values.takeIf { it.isNotEmpty() }?.sortedByDescending { it.first.length }
                ?.joinToString("|") { (text, _) ->
                    val start = if (text.first().isAsciiWord()) "(?<![A-Za-z0-9_-])" else ""
                    val end = if (text.last().isAsciiWord()) "(?![A-Za-z0-9_-])" else ""
                    start + text.split(WHITESPACE).joinToString("\\s+") { Regex.escape(it) } + end
                }?.let { Regex(it, RegexOption.IGNORE_CASE) }
        }

        fun translate(text: String): String {
            if (text.isBlank()) return text
            val source = text.replace('\u00a0', ' ')
            if (battle) RESISTED_SUFFIX.matchEntire(source)?.let { suffix ->
                val line = suffix.groupValues[1]
                val translated = wholeSentences.firstNotNullOfOrNull { rule ->
                    rule.pattern!!.matchEntire(line)?.let { render(it, rule) }
                }
                if (translated != null) return translated.trimEnd('。', '.') + "（抵抗 ${suffix.groupValues[2]}%）。"
            }
            // Explicit contextual rules, such as the monster stat "Power", take
            // precedence over the generic equipment-affix dictionary.
            wholeSentences.firstNotNullOfOrNull { rule ->
                rule.pattern!!.matchEntire(source)?.let { render(it, rule) }
            }?.let { return it }
            singleLetterLabels[source.trim()]?.let { return it }
            words[key(source)]?.let { return it }
            if ('\n' in source) return source.split('\n').joinToString("\n") { translate(it) }
            var spans = listOf(Span(source))
            for (rule in sentences) {
                spans = spans.flatMap { span ->
                    if (span.translated) listOf(span) else replaceSentence(span.text, rule)
                }
                if (spans.all { it.translated }) break
            }
            return spans.joinToString("") { if (it.translated) it.text else translateWords(it.text) }
        }

        /** Unknown log sentences may contain player names; never do fragment translation. */
        fun translateBattleLog(text: String): String {
            val source = text.replace('\u00a0', ' ').trim()
            val suffix = RESISTED_SUFFIX.matchEntire(source)
            val line = suffix?.groupValues?.get(1) ?: source
            val translated = wholeSentences.firstNotNullOfOrNull { rule ->
                rule.pattern!!.matchEntire(line)?.let { render(it, rule) }
            } ?: words[key(line)] ?: return text
            return if (suffix == null) translated
                else translated.trimEnd('。', '.') + "（抵抗 ${suffix.groupValues[2]}%）。"
        }

        private fun render(match: MatchResult, rule: Rule): String = CAPTURE.replace(rule.value) { reference ->
            val index = reference.groupValues[1].toInt()
            val captured = match.groups[index]?.value.orEmpty()
            // Name captures remain sealed; only explicitly typed game terms recurse.
            when {
                index in rule.actorGroups -> when (captured.lowercase(Locale.ROOT)) {
                    "you" -> "你"
                    "your" -> "你的"
                    "your offhand attack" -> "你的副手攻击"
                    else -> captured
                }
                index in rule.compoundGroups -> translateWords(captured)
                index in rule.translatedGroups -> words[key(captured)]?.trim() ?: captured
                else -> captured
            }
        }

        private fun replaceSentence(text: String, rule: Rule): List<Span> {
            val matches = rule.pattern!!.findAll(text).iterator()
            if (!matches.hasNext()) return listOf(Span(text))
            val spans = mutableListOf<Span>()
            var offset = 0
            while (matches.hasNext()) {
                val match = matches.next()
                if (match.range.first > offset) spans += Span(text.substring(offset, match.range.first))
                spans += Span(render(match, rule), translated = true)
                offset = match.range.last + 1
            }
            if (offset < text.length) spans += Span(text.substring(offset))
            return spans
        }

        private fun translateWords(text: String): String = words[key(text)] ?: wordPattern?.replace(text) {
            words[key(it.value)] ?: it.value
        } ?: text
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val CAPTURE = Regex("\\$(\\d+)")
        val RESISTED_SUFFIX = Regex("^(.*?) \\(([\\d.]+)% resisted\\)[.!]?$")
        fun key(text: String) = text.trim().replace(WHITESPACE, " ").lowercase(Locale.ROOT)
        fun Char.isAsciiWord() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '_'
        fun regex(pattern: String, flags: String) = Regex(pattern, buildSet {
            if ('i' in flags) add(RegexOption.IGNORE_CASE)
            if ('m' in flags) add(RegexOption.MULTILINE)
            if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
        })
    }
}
