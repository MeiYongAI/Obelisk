package com.mitahv.mobile.data

import com.mitahv.mobile.model.ArenaChallenge
import com.mitahv.mobile.model.ArenaPage
import com.mitahv.mobile.model.BattleActionOption
import com.mitahv.mobile.model.BattleEnemy
import com.mitahv.mobile.model.BattlePage
import com.mitahv.mobile.model.BattleVital
import com.mitahv.mobile.model.BattleEffect
import com.mitahv.mobile.model.BattleControl
import com.mitahv.mobile.model.BattleContinuation
import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvWorld
import com.mitahv.mobile.model.NativeAction
import okhttp3.HttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONObject

internal object BattlePageParser {
    fun isBattlePage(html: String): Boolean = isBattlePage(Jsoup.parse(html))

    fun isBattlePage(document: Document): Boolean = document.selectFirst("#battle_main") != null

    fun parseArena(html: String): ArenaPage = parseArena(Jsoup.parse(html))

    fun parseArena(document: Document): ArenaPage {
        val table = requireNotNull(document.selectFirst("#arena_list")) {
            "Arena challenge list was not found"
        }
        val postToken = requireNotNull(
            document.selectFirst("#initform input[name=postoken]")?.attr("value")?.takeIf(String::isNotBlank),
        ) { "Arena form token was not found" }

        val rows = table.select("tbody > tr").mapNotNull { row ->
            val cells = row.children().filter { it.tagName() == "td" }
            if (cells.size < 6 || cells.take(5).any { (it.attr("colspan").toIntOrNull() ?: 1) > 1 }) return@mapNotNull null
            val name = cells[0].hvText().takeIf(String::isNotBlank) ?: return@mapNotNull null
            val startControl = row.select("img[onclick]").firstOrNull {
                !it.hasAttr("disabled") && !it.attr("aria-disabled").equals("true", true) &&
                    !it.attr("src").substringBefore('?').endsWith("_d.png") &&
                    INIT_BATTLE_ID.matches(it.attr("onclick"))
            }
            val id = startControl?.attr("onclick")?.let(INIT_BATTLE_ID::matchEntire)?.groupValues?.get(1)?.toIntOrNull()
            val combinedStatus = cells.getOrNull(5)?.takeIf {
                (it.attr("colspan").toIntOrNull() ?: 1) > 1
            }
            ArenaChallenge(
                id = id,
                name = name,
                highestClear = cells[1].hvText(),
                minimumLevel = cells[2].hvText(),
                rounds = cells[3].hvText(),
                experienceMultiplier = cells[4].hvText(),
                entryCost = if (combinedStatus == null) cells.getOrNull(5).hvText() else "",
                clearBonus = if (combinedStatus == null) cells.getOrNull(6).hvText() else "",
                availabilityInfo = combinedStatus.hvText(),
                available = id != null,
            )
        }
        return ArenaPage(postToken, rows, if (rows.isEmpty()) arenaListEmptyMessage(table) else "")
    }

    fun parseBattle(
        html: String,
        supplementalScripts: List<String> = emptyList(),
        world: HvWorld = HvWorld.Persistent,
        currentUrl: HttpUrl = HvRoute.Battle.url(world),
    ): BattlePage = parseBattle(Jsoup.parse(html), html, supplementalScripts, world, currentUrl)

    fun parseBattle(document: Document, snapshotHtml: String,
        supplementalScripts: List<String> = emptyList(), world: HvWorld = HvWorld.Persistent,
        currentUrl: HttpUrl = HvRoute.Battle.url(world)): BattlePage {
        require(document.selectFirst("#battle_main") != null) { "Active battle page was not found" }
        val scripts = document.select("script").joinToString("\n") { it.data() + it.html() }
        val itemCatalog = HvScriptObjectParser.parse(scripts + "\n" + supplementalScripts.joinToString("\n"), "dynjs_itemc")
        val token = requireNotNull(BATTLE_TOKEN.find(scripts)?.groupValues?.get(1)) {
            "Active battle token was not found"
        }

        val vitalPane = document.selectFirst("#pane_vitals")
        val utilitarianVitals = vitalPane?.selectFirst("#dvbh") != null
        val vitalPrefix = if (utilitarianVitals) "d" else ""
        // hvb.css defines both Standard and Utilitarian bars. Their IDs and
        // full widths differ; the health readout can use either hb or hd.
        val vitals = listOfNotNull(
            vitalPane?.selectFirst("#${vitalPrefix}vrhb, #${vitalPrefix}vrhd")?.let { value ->
                BattleVital("Health", value.hvText(), document.selectFirst("#${vitalPrefix}vbh img")
                    ?.let { barFraction(it, if (utilitarianVitals) 414f else 496f) } ?: 0f)
            },
            vitalPane?.selectFirst("#${vitalPrefix}vrm")?.let { value ->
                BattleVital("Mana", value.hvText(), document.selectFirst("#${vitalPrefix}vbm img")
                    ?.let { barFraction(it, if (utilitarianVitals) 414f else 207f) } ?: 0f)
            },
            vitalPane?.selectFirst("#${vitalPrefix}vrs")?.let { value ->
                BattleVital("Spirit", value.hvText(), document.selectFirst("#${vitalPrefix}vbs img")
                    ?.let { barFraction(it, if (utilitarianVitals) 414f else 207f) } ?: 0f)
            },
            vitalPane?.selectFirst("#dvrc")?.let { value ->
                BattleVital("Overcharge", value.hvText(), document.selectFirst("#dvbc img")
                    ?.let { barFraction(it, 414f) } ?: 0f)
            } ?: if (!utilitarianVitals) standardOvercharge(vitalPane) else null,
        )
        val completion = document.selectFirst("#pane_completion")

        return BattlePage(
            token = token,
            playerVitals = vitals,
            enemies = document.select("#pane_monster > [id^=mkey_]").mapNotNull { enemy ->
                val target = enemy.id().substringAfter("mkey_").toIntOrNull() ?: return@mapNotNull null
                val level = enemy.selectFirst(".btm2").hvText()
                BattleEnemy(
                    target = target,
                    name = enemy.selectFirst(".btm3").hvText(),
                    level = level,
                    healthFraction = enemy.selectFirst(".chbd img[alt=health]")
                        ?.let { barFraction(it, 120f) } ?: 0f,
                    magicFraction = enemy.selectFirst(".chbd img[alt=magic]")?.let { barFraction(it, 120f) },
                    spiritFraction = enemy.selectFirst(".chbd img[alt=spirit]")?.let { barFraction(it, 120f) },
                    effects = effects(enemy.selectFirst(".btm6")),
                    targetable = enabled(enemy) && COMMIT_TARGET.find(enemy.attr("onclick"))?.groupValues?.get(1)?.toIntOrNull() == target,
                )
            },
            quickActions = document.select("#quickbar > div").mapNotNull(::actionOption),
            skills = groupedActions(document.selectFirst("#table_skills")).ifEmpty { document.select("#pane_skill .btsd").mapNotNull(::actionOption) },
            spells = groupedActions(document.selectFirst("#table_magic")).ifEmpty { document.select("#pane_magic .btsd").mapNotNull(::actionOption) },
            items = document.select("#pane_item .bti3 > div, #pane_item [id^=ikey_]").mapNotNull { item ->
                val itemId = BattleTooltipParser.arguments(item.attr("onmouseover"), "set_infopane_item")?.firstOrNull()
                val metadata = itemId?.let { itemCatalog?.optJSONObject(it) }
                actionOption(item)?.let { option -> option.copy(
                    name = metadata?.optString("n")?.takeIf(String::isNotBlank) ?: option.name,
                    description = metadata?.optString("q")?.let { Jsoup.parseBodyFragment(it).text() }.orEmpty(),
                    group = item.closest(".bti1")?.selectFirst(".bti2").hvText().let {
                    when { it.startsWith("S") -> "Scrolls"; it.startsWith("N") -> "Infusions"; else -> "Items" }
                }) }
            },
            log = document.select("#textlog tr").map { it.hvText() }.filter(String::isNotBlank),
            completion = completion.hvText(),
            experienceBarWidth = document.selectFirst("#expbar")?.let(::barWidth) ?: 0f,
            snapshotHtml = snapshotHtml,
            playerEffects = effects(document.selectFirst("#pane_effects")),
            controls = document.select("#pane_action [id^=ckey_]").mapNotNull { control ->
                val mode = control.id().substringAfter("ckey_")
                if (mode !in setOf("attack", "skill", "items", "spirit", "defend", "focus")) return@mapNotNull null
                BattleControl(mode, enabled(control) && CONTROL_ACTION.find(control.attr("onclick"))?.groupValues?.get(1) == mode,
                    control.attr("src").substringBefore('?').endsWith("_s.png"))
            },
            isComplete = completion?.let { it.attr("data-obelisk-complete") == "true" || it.html().isNotBlank() } == true,
            continuation = completion?.let { continuation(it, world, currentUrl) },
            completionLines = completion?.select("#btcp > div")?.map { it.hvText() }
                .orEmpty().filter(String::isNotBlank).ifEmpty { listOfNotNull(completion.hvText().takeIf(String::isNotBlank)) },
            supplementalScripts = supplementalScripts,
        )
    }

    private fun continuation(container: Element, world: HvWorld, currentUrl: HttpUrl): BattleContinuation? {
        val control = container.select("#btcp[onclick]").singleOrNull()?.takeIf(::enabled) ?: return null
        val handler = control.attr("onclick")
        if (CONTINUE_BATTLE.matches(handler)) return BattleContinuation(label = "下一轮")
        val target = if (GRINDFEST_RETURN.matches(handler)) {
            // hvc.js: common.goto_grindfest() assigns MAIN_URL + "?s=Battle&ss=gr".
            "?s=Battle&ss=gr"
        } else {
            val location = FINISH_BATTLE.matchEntire(handler) ?: return null
            location.groupValues[2].also {
                if (location.groupValues[1].single() in it || '\\' in it || "\${" in it) return null
            }
        }
        val url = currentUrl.resolve(target)?.takeIf { world.containsUrl(it) } ?: return null
        if (url.queryParameterValues("s").size != 1 || url.queryParameterValues("ss").size != 1) return null
        val route = HvRoute.entries.firstOrNull {
            it.section == url.queryParameter("s") && it.subsection == url.queryParameter("ss") && it.isAvailableIn(world)
        } ?: return null
        return BattleContinuation(
            label = "结束战斗",
            action = NativeAction(url = url, method = "GET", label = "结束战斗"),
            destination = route,
        )
    }

    fun mergeActionResponse(currentHtml: String, response: JSONObject): String {
        val document = Jsoup.parse(currentHtml)
        applyActionResponse(document, response)
        return document.outerHtml()
    }

    /**
     * Applies the server's battle JSON to the already loaded battle document and
     * parses that same document. This avoids the generic page pipeline (navigation,
     * page classification and supplemental-script discovery) for every battle turn.
     * A null result means the response did not contain an update pane we understand.
     */
    fun parseActionResponse(
        current: BattlePage,
        response: JSONObject,
        world: HvWorld,
        currentUrl: HttpUrl,
    ): BattlePage? {
        if (!hasActionUpdate(response)) return null
        val document = Jsoup.parse(current.snapshotHtml, currentUrl.toString())
        applyActionResponse(document, response)
        val updatedHtml = document.outerHtml()
        return parseBattle(document, updatedHtml, current.supplementalScripts, world, currentUrl)
    }

    fun hasActionUpdate(response: JSONObject): Boolean =
        ACTION_UPDATE_IDS.any(response::has) || response.has("textlog") || response.has("exp")

    private fun applyActionResponse(document: Document, response: JSONObject) {
        val refreshIds = listOf(
            "pane_completion", "pane_effects", "pane_action", "pane_vitals", "pane_quickbar",
            "table_skills", "table_magic", "pane_item", "pane_monster",
        )
        refreshIds.forEach { id ->
            if (response.has(id)) document.selectFirst("#$id")?.html(response.getString(id))
        }
        document.selectFirst("#textlog tbody")?.let { logBody ->
            val newEntries = response.optJSONArray("textlog")
            if (newEntries != null) {
                for (index in 0 until newEntries.length()) {
                    val entry = newEntries.optJSONObject(index) ?: continue
                    val row = document.createElement("tr")
                    val cell = document.createElement("td")
                    cell.addClass("tl${entry.optString("c")}")
                    cell.html(entry.optString("t"))
                    row.appendChild(cell)
                    logBody.prependChild(row)
                }
                while (logBody.childrenSize() > 100) logBody.children().last()?.remove()
            }
        }
        if (!response.has("pane_completion")) document.selectFirst("#pane_completion")?.empty()
        document.selectFirst("#pane_completion")?.attr("data-obelisk-complete", response.has("pane_completion").toString())
        if (response.has("exp")) {
            val expBar = document.selectFirst("#expbar")
            val width = response.optDouble("exp", Double.NaN)
            if (expBar != null && width.isFinite()) expBar.attr("style", "width:${width}px")
        }
    }

    private fun actionOption(element: Element): BattleActionOption? {
        val onclick = element.attr("onclick")
        val lock = LOCK_ACTION.find(onclick)
        val skillCall = SKILL_CALL.find(onclick)
        val skill = skillCall?.groupValues?.get(2)?.toIntOrNull()
        val tooltip = BattleTooltipParser.arguments(element.attr("onmouseover"), "set_infopane_spell")
        val name = tooltip?.firstOrNull()?.takeIf(String::isNotBlank) ?: element.hvText()
        if (name.isBlank()) return null
        val mode = lock?.groupValues?.get(1).orEmpty()
        return BattleActionOption(
            id = element.id().ifBlank { "disabled-$name" },
            name = name.trim(),
            mode = mode,
            skill = skill ?: 0,
            targetsEnemy = skillCall?.groupValues?.get(1) == "hostile",
            enabled = enabled(element) && skill != null && mode in setOf("magic", "items"),
            description = tooltip?.getOrNull(1)?.let { Jsoup.parseBodyFragment(it).text() }.orEmpty(),
            manaCost = tooltip?.getOrNull(3)?.toIntOrNull(),
            chargeCost = tooltip?.getOrNull(4)?.toIntOrNull(),
            cooldown = tooltip?.getOrNull(5)?.toIntOrNull(),
        )
    }

    private fun groupedActions(table: Element?): List<BattleActionOption> {
        if (table == null) return emptyList()
        var group = ""
        return table.select("tr").flatMap { row ->
            row.selectFirst("td[colspan] > img[alt]")?.attr("alt")?.let { header ->
                group = when (header) {
                    "skills_innate" -> "Innate"; "skills_weapon" -> "Weapon"
                    "magic_damage" -> "Damage"; "magic_debuff" -> "Deprecating"
                    "magic_curative" -> "Curative"; "magic_support" -> "Supportive"
                    else -> ""
                }
            }
            row.select(".btsd").mapNotNull { actionOption(it)?.copy(group = group) }
        }.ifEmpty { table.select(".btsd").mapNotNull(::actionOption) }
    }

    private fun effects(container: Element?): List<BattleEffect> = container?.select("[onmouseover]")?.mapNotNull {
        val arguments = BattleTooltipParser.arguments(it.attr("onmouseover"), "set_infopane_effect") ?: return@mapNotNull null
        if (arguments.size != 3) return@mapNotNull null
        BattleEffect(arguments[0], Jsoup.parseBodyFragment(arguments[1]).text(), arguments[2])
    }.orEmpty()

    private fun enabled(element: Element) = !element.hasAttr("disabled") &&
        !element.attr("aria-disabled").equals("true", true) &&
        !element.attr("src").substringBefore('?').endsWith("_d.png")

    private fun barFraction(element: Element, maximumWidth: Float): Float =
        (barWidth(element) / maximumWidth).coerceIn(0f, 1f)

    private fun standardOvercharge(vitalPane: Element?): BattleVital? {
        val dots = vitalPane?.selectFirst("#vcp") ?: return null
        val charge = (dots.select("#vcp > div > div").size -
            if (dots.html().contains("vcr")) 0.5f else 0f).coerceIn(0f, 10f)
        val value = if (charge % 1f == 0f) charge.toInt().toString() else charge.toString()
        return BattleVital("Overcharge", value, charge / 10f)
    }

    private fun barWidth(element: Element): Float = WIDTH.find(element.attr("style"))
        ?.groupValues?.get(1)?.toFloatOrNull() ?: 0f

    private val INIT_BATTLE_ID = Regex("""\s*init_battle\(\s*(\d+)\s*(?:,\s*\d+\s*)?\)\s*;?\s*""")
    private val ACTION_UPDATE_IDS = listOf(
        "pane_completion", "pane_effects", "pane_action", "pane_vitals", "pane_quickbar",
        "table_skills", "table_magic", "pane_item", "pane_monster",
    )
    private val BATTLE_TOKEN = Regex("battle_token\\s*=\\s*['\"]([^'\"]+)['\"]")
    private val LOCK_ACTION = Regex("lock_action\\(this,1,'([^']+)',")
    private val SKILL_CALL = Regex("set_(friendly|hostile)_skill\\(\\s*['\"]?([^)'\"]+)")
    private val WIDTH = Regex("width\\s*:\\s*([0-9.]+)px")
    private val CONTROL_ACTION = Regex("""battle\.lock_action\(\s*this\s*,\s*0\s*,\s*['"]([^'"]+)['"]\s*\)""")
    private val COMMIT_TARGET = Regex("""battle\.commit_target\(\s*(\d+)\s*\)""")
    private val CONTINUE_BATTLE = Regex("""\s*battle\.battle_continue\(\s*\)\s*;?\s*""")
    private val GRINDFEST_RETURN = Regex("""\s*common\.goto_grindfest\(\s*\)\s*;?\s*""")
    private val FINISH_BATTLE = Regex("""\s*document\.location\s*=\s*(['"`])([\s\S]*?)\1\s*;?\s*""")
}

internal fun arenaListEmptyMessage(table: Element): String = table.select("tbody > tr")
    .mapNotNull { row ->
        val cells = row.children().filter { it.tagName() == "td" }
        cells.singleOrNull()?.takeIf { (it.attr("colspan").toIntOrNull() ?: 1) > 1 }
            ?.hvText()?.takeIf(String::isNotBlank)
    }.distinct().joinToString("\n")
