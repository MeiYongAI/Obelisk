package com.mitahv.mobile.data

import com.mitahv.mobile.model.BattleModesChallenge
import com.mitahv.mobile.model.BattleModesEquipment
import com.mitahv.mobile.model.BattleModesFilter
import com.mitahv.mobile.model.BattleModesMaterial
import com.mitahv.mobile.model.BattleModesPage
import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvWorld
import com.mitahv.mobile.model.NativeAction
import okhttp3.HttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal object BattleModesParser {
    fun parse(html: String, route: HvRoute, world: HvWorld, currentUrl: HttpUrl = route.url(world),
        supplementalScripts: List<String> = emptyList()): BattleModesPage =
        parse(Jsoup.parse(html), route, world, currentUrl, supplementalScripts)

    fun parse(document: Document, route: HvRoute, world: HvWorld, currentUrl: HttpUrl = route.url(world),
        supplementalScripts: List<String> = emptyList()): BattleModesPage {
        require(route.isAvailableIn(world)) { "Battle mode is unavailable in this world" }
        require(currentPageUrl(world, route, currentUrl.toString()) != null) { "Battle page URL does not match the route" }
        require(document.selectFirst("#mainpane") != null && document.selectFirst("#battle_main") == null)
        val messages = document.select("#messagebox_inner > p").map { it.hvText() }.filter(String::isNotBlank)
        return when (route) {
            HvRoute.RingOfBlood -> ringOfBlood(document, world, route, currentUrl, messages)
            HvRoute.GrindFest -> grindFest(document, world, route, currentUrl, messages)
            HvRoute.Tower -> tower(document, world, route, currentUrl, messages)
            HvRoute.ItemWorld -> itemWorld(document, world, route, currentUrl, messages, supplementalScripts)
            else -> throw IllegalArgumentException("Not a supported battle entry page")
        }
    }

    private fun ringOfBlood(document: Document, world: HvWorld, route: HvRoute, currentUrl: HttpUrl, messages: List<String>): BattleModesPage {
        val table = requireNotNull(document.selectFirst("#mainpane #arena_list"))
        val form = initForm(document, world, route, currentUrl)
        val challenges = table.select("tbody > tr").mapNotNull { row ->
            val cells = row.children().filter { it.tagName() == "td" }
            if (cells.size < 6 || cells.take(5).any { (it.attr("colspan").toIntOrNull() ?: 1) > 1 }) return@mapNotNull null
            val name = cells[0].hvText().takeIf(String::isNotBlank) ?: return@mapNotNull null
            val combined = cells.getOrNull(5)?.takeIf { (it.attr("colspan").toIntOrNull() ?: 1) > 1 }
            val control = row.select("img[onclick]").firstOrNull { enabled(it) && INIT_BATTLE.matches(it.attr("onclick")) }
            val args = control?.let { INIT_BATTLE.matchEntire(it.attr("onclick")) }
            val cost = if (combined == null) cells.getOrNull(5).hvText() else ""
            val action = if (form != null && args != null) {
                form.action(
                    id = args.groupValues[1], label = "开始挑战",
                    confirmation = listOf(name, cost).filter(String::isNotBlank).joinToString("\n"),
                )
            } else null
            BattleModesChallenge(
                name = name, highestClear = cells[1].hvText(), minimumLevel = cells[2].hvText(),
                rounds = cells[3].hvText(), experienceMultiplier = cells[4].hvText(),
                entryCost = cost, clearBonus = if (combined == null) cells.getOrNull(6).hvText() else "",
                status = combined.hvText().ifBlank { if (action == null) "暂不可挑战" else "" }, start = action,
            )
        }
        return BattleModesPage.RingOfBlood(document.selectFirst("#arena_tokens").hvText(), challenges, messages,
            if (challenges.isEmpty()) arenaListEmptyMessage(table) else "")
    }

    private fun grindFest(document: Document, world: HvWorld, route: HvRoute, currentUrl: HttpUrl, messages: List<String>): BattleModesPage {
        val content = requireNotNull(document.selectFirst("#mainpane #grindfest"))
        val paragraphs = content.select("p").map { it.hvText() }.filter(String::isNotBlank)
        val rounds = paragraphs.firstNotNullOfOrNull { ROUNDS.find(it)?.groupValues?.get(1) }.orEmpty()
        val cost = paragraphs.firstNotNullOfOrNull { STAMINA_COST.find(it)?.groupValues?.get(1) }.orEmpty()
        val action = startAction(content, initForm(document, world, route, currentUrl), "开始 GrindFest",
            listOf("GrindFest", rounds.takeIf(String::isNotBlank)?.let { "$it rounds" },
                cost.takeIf(String::isNotBlank)?.let { "$it Stamina" }).filterNotNull().joinToString("\n"))
        return BattleModesPage.GrindFest(rounds, cost, paragraphs, action, messages)
    }

    private fun tower(document: Document, world: HvWorld, route: HvRoute, currentUrl: HttpUrl, messages: List<String>): BattleModesPage {
        val content = requireNotNull(document.selectFirst("#mainpane #towerstart"))
        val paragraphs = content.select("p").map { it.hvText() }.filter(String::isNotBlank)
        fun value(prefix: String) = paragraphs.firstOrNull { it.startsWith(prefix, ignoreCase = true) }
            ?.substringAfter(':')?.trim().orEmpty()
        val floor = value("Current Floor:")
        val difficulty = paragraphs.firstOrNull { Regex("""Monster Level\s+\d+""", RegexOption.IGNORE_CASE).containsMatchIn(it) }.orEmpty()
        val rules = paragraphs.filterNot {
            it.startsWith("Your Ranking:", true) || it.startsWith("Current Floor:", true) ||
                it.startsWith("Daily Attempts:", true) || it.startsWith("Daily Clears:", true) || it == difficulty
        }
        return BattleModesPage.Tower(
            ranking = value("Your Ranking:"), floor = floor, difficulty = difficulty,
            attempts = value("Daily Attempts:"), clears = value("Daily Clears:"), rules = rules,
            start = startAction(content, initForm(document, world, route, currentUrl), "开始挑战", "The Tower\n$floor\n$difficulty"),
            messages = messages,
        )
    }

    private fun itemWorld(document: Document, world: HvWorld, route: HvRoute, currentUrl: HttpUrl, messages: List<String>,
        supplementalScripts: List<String>): BattleModesPage {
        val form = requireNotNull(document.selectFirst("#mainpane #equipform"))
        require(document.selectFirst("#equiplist") != null)
        val scripts = document.select("script:not([src])").joinToString("\n") { it.data() }
        val eqitems = HvScriptObjectParser.parse(scripts, "eqitems")
        val itemdata = HvScriptObjectParser.parse(scripts, "itemdata")
        val equipdata = HvScriptObjectParser.parse(scripts + "\n" + supplementalScripts.joinToString("\n"), "dynjs_equip")
        val formUrl = formTarget(form, world, route, currentUrl)
        val fields = form.hiddenFields()
        val submit = form.selectFirst("#equipsubmit")
        val dynamicSelect = TITLE_ITEMWORLD.containsMatchIn(scripts) && SINGLE_SELECT.containsMatchIn(scripts)
        val knownSubmit = submit?.attr("type").equals("submit", true) &&
            submit?.attr("onclick")?.contains("confirm_action(") == true
        val canSubmit = formUrl != null && fields.any { it.first == "postoken" && it.second.isNotBlank() } &&
            dynamicSelect && knownSubmit
        val filters = document.select("#filterbar a[href]").mapNotNull { anchor ->
            val target = currentPageUrl(world, route, anchor.attr("href"), currentUrl) ?: return@mapNotNull null
            BattleModesFilter(anchor.hvText(), anchor.selectFirst(".cfbs") != null,
                NativeAction(target, method = "GET", label = anchor.hvText()))
        }
        var group = ""
        val equipment = document.select("#equiplist tr").mapNotNull { row ->
            if (row.hasClass("eqtplabel")) { group = row.hvText(); return@mapNotNull null }
            val input = row.selectFirst("input[name=\"eqids[]\"]") ?: return@mapNotNull null
            val id = input.attr("value").takeIf { it.toLongOrNull()?.let { value -> value > 0 } == true }
                ?: return@mapNotNull null
            val entry = eqitems?.optJSONObject(id)
            val equipmentData = equipdata?.optJSONObject(id)
            val originalName = equipmentData?.optString("n")?.takeIf(String::isNotBlank)
            val details = entry?.optJSONObject("p")
            val name = equipmentData?.optString("t")?.takeIf { originalName != null && it.isNotBlank() }
                ?: entry?.optString("t")?.takeIf(String::isNotBlank) ?: row.selectFirst("label").hvText()
            val costs = entry?.optJSONObject("m")
            val materials = costs?.keys()?.asSequence()?.mapNotNull { key ->
                val required = costs.opt(key)?.toString()?.toLongOrNull() ?: return@mapNotNull null
                val owned = itemdata?.optJSONObject(key)?.opt("c")?.toString()?.toLongOrNull() ?: return@mapNotNull null
                val materialName = itemdata.optJSONObject(key)?.optString("n").orEmpty()
                if (required < 0 || owned < 0 || materialName.isBlank()) return@mapNotNull null
                BattleModesMaterial(materialName, required, owned)
            }?.toList().orEmpty()
            val materialsKnown = costs != null && materials.size == costs.length()
            val enough = materialsKnown && materials.all { it.owned >= it.required }
            val selectable = enabled(input)
            val difficulty = details?.optString("diffname").orEmpty() +
                (details?.optInt("diffboost", 0)?.takeIf { it > 0 }?.let { " +$it%" }.orEmpty())
            val materialSummary = materials.joinToString("\n") { "${it.required} ${it.name}（持有 ${it.owned}）" }
            val detailsComplete = details != null && listOf("iwlvl", "rounds", "monsterlevel").all {
                (details.opt(it)?.toString()?.toIntOrNull() ?: 0) > 0
            } && details.optString("diffname").isNotBlank()
            val start = if (canSubmit && selectable && enough && detailsComplete) {
                NativeAction(requireNotNull(formUrl), fields = fields + ("eqids[]" to id), label = "进入 Item World",
                    confirmation = listOf(name, "World Level ${details?.opt("iwlvl")}",
                        "${details?.opt("rounds")} rounds · $difficulty", materialSummary).filter(String::isNotBlank).joinToString("\n"))
            } else null
            BattleModesEquipment(
                id = id, name = name, group = group, tier = row.select("td").getOrNull(1).hvText(),
                worldLevel = details?.opt("iwlvl")?.toString().orEmpty(), rounds = details?.opt("rounds")?.toString().orEmpty(),
                monsterLevel = details?.opt("monsterlevel")?.toString().orEmpty(), difficulty = difficulty,
                materials = materials, selectable = selectable,
                unavailableReason = when { !selectable -> "此装备不可选择"; materialsKnown && !enough -> "材料不足";
                    start == null -> "挑战数据不完整"; else -> null }, start = start,
                equipmentDetails = equipmentDetails(equipmentData?.optString("d").orEmpty()),
                originalEquipmentName = originalName,
            )
        }
        return BattleModesPage.ItemWorld(filters, equipment, messages)
    }

    private fun equipmentDetails(html: String): String {
        if (html.isBlank()) return ""
        return Jsoup.parseBodyFragment(html).select(".eq > div").flatMap { block ->
            if (block.hasClass("ex") || block.hasClass("ep")) block.children().map { it.hvText() }
            else listOf(block.hvText())
        }.filter(String::isNotBlank).joinToString("\n")
    }

    private data class InitForm(val url: HttpUrl, val fields: List<Pair<String, String>>) {
        fun action(id: String, label: String, confirmation: String) = NativeAction(
            url, fields = fields.map { if (it.first == "initid") "initid" to id else it },
            label = label, confirmation = confirmation,
        )
    }

    private fun initForm(document: Document, world: HvWorld, route: HvRoute, currentUrl: HttpUrl): InitForm? {
        val form = document.selectFirst("#mainpane #initform") ?: return null
        val url = formTarget(form, world, route, currentUrl) ?: return null
        val fields = form.hiddenFields()
        if (fields.count { it.first == "initid" } != 1 || fields.none { it.first == "postoken" && it.second.isNotBlank() }) return null
        return InitForm(url, fields)
    }

    private fun startAction(content: Element, form: InitForm?, label: String, confirmation: String): NativeAction? {
        if (form == null) return null
        val control = content.select("img[onclick]").firstOrNull { enabled(it) && INIT_BATTLE.matches(it.attr("onclick")) } ?: return null
        val id = INIT_BATTLE.matchEntire(control.attr("onclick"))!!.groupValues[1]
        return form.action(id, label, confirmation)
    }

    private fun formTarget(form: Element, world: HvWorld, route: HvRoute, currentUrl: HttpUrl = route.url(world)): HttpUrl? =
        if (form.attr("method").equals("post", true) && enabled(form)) currentPageUrl(world, route, form.attr("action"), currentUrl) else null

    private fun currentPageUrl(world: HvWorld, route: HvRoute, value: String, currentUrl: HttpUrl = route.url(world)): HttpUrl? {
        val url = currentUrl.resolve(value) ?: return null
        return url.takeIf {
            world.containsUrl(it) &&
                it.queryParameterValues("s") == listOf(route.section) && it.queryParameterValues("ss") == listOf(route.subsection) &&
                it.queryParameterNames.all { name -> name in setOf("s", "ss", "screen", "filter") && it.queryParameterValues(name).size == 1 } &&
                (it.queryParameter("screen") == null || route == HvRoute.ItemWorld && it.queryParameter("screen") == "itemworld")
        }
    }

    private fun Element.hiddenFields() = select("input[type=hidden][name]").filter(::enabled)
        .map { it.attr("name") to it.attr("value") }

    private fun enabled(element: Element) = !element.hasAttr("disabled") &&
        !element.attr("aria-disabled").equals("true", true) &&
        !element.attr("src").substringBefore('?').endsWith("_d.png")

    private val INIT_BATTLE = Regex("""\s*init_battle\(\s*(\d+)\s*(?:,\s*(\d+)\s*)?\)\s*;?\s*""")
    private val ROUNDS = Regex("""up to ([\d,]+) rounds""", RegexOption.IGNORE_CASE)
    private val STAMINA_COST = Regex("""consume ([\d,]+) point[s]? of Stamina""", RegexOption.IGNORE_CASE)
    private val TITLE_ITEMWORLD = Regex("""\bconst\s+title\s*=\s*['"]itemworld['"]""")
    private val SINGLE_SELECT = Regex("""\bconst\s+multiselect\s*=\s*false\b""")
}
