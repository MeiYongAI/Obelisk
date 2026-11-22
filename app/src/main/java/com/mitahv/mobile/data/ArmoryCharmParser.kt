package com.mitahv.mobile.data

import com.mitahv.mobile.model.ArmoryCharmConfig
import com.mitahv.mobile.model.ArmoryCharmOption
import com.mitahv.mobile.model.BazaarSectionsField
import com.mitahv.mobile.model.BazaarSectionsForm
import com.mitahv.mobile.model.NativeAction
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal object ArmoryCharmParser {
    /** Preserve the official struck-through pouch state when rendering the slot as native text. */
    fun slotDetails(node: Element): List<Pair<String, String>> {
        val table = node.selectFirst("table") ?: return listOfNotNull(
            node.selectFirst(".eqce").hvText().takeIf(String::isNotBlank)?.let { "" to it })
        val name = table.selectFirst("th").hvText()
        val cells = table.select("tr").lastOrNull()?.select("td").orEmpty()
        val condition = cells.getOrNull(0).hvText()
        val pouch = cells.getOrNull(1)
        val pouchText = pouch?.let { cell ->
            cell.hvText().takeIf(String::isNotBlank)?.plus(if (cell.hasClass("chdmg")) "（损毁）" else "")
        }
        return listOfNotNull(
            listOf(name, condition).filter(String::isNotBlank).joinToString(" · ").takeIf(String::isNotBlank)?.let { "" to it },
            pouchText?.let { "护符袋" to it },
        )
    }

    fun parse(document: Document, action: NativeAction): BazaarSectionsForm? {
        if (document.selectFirst("#eqselcharm") == null) return null
        val button = document.selectFirst("#setcharm") ?: return null
        if (!Regex("""\s*pop_setcharm\(\s*\)\s*;?\s*(?:return false\s*;?)?\s*""").matches(button.attr("onclick"))) return null
        val script = document.select("script").joinToString("\n") { it.data() }
        fun integer(name: String) = Regex("\\b${Regex.escape(name)}\\s*=\\s*(\\d+)")
            .find(script)?.groupValues?.get(1)?.toIntOrNull()
        fun boolean(name: String) = Regex("\\b${Regex.escape(name)}\\s*=\\s*(true|false)\\b")
            .find(script)?.groupValues?.get(1)?.toBooleanStrictOrNull()
        val charmMaterials = HvScriptObjectParser.parse(script, "charm_mats") ?: return null
        val pouchMaterials = HvScriptObjectParser.parse(script, "pouch_mats") ?: return null
        val previews = HvScriptObjectParser.parse(script, "charm_stats")
        fun options(name: String, materials: JSONObject): List<ArmoryCharmOption> =
            document.select("#equipform input[name=$name][type=radio]").mapNotNull { input ->
                val id = input.attr("value").takeIf { it.toIntOrNull() != null } ?: return@mapNotNull null
                val points = input.attr("data-cp").toIntOrNull() ?: return@mapNotNull null
                val itemMaterials = materials.optJSONObject(id)?.let { items ->
                    items.keys().asSequence().mapNotNull material@{ key ->
                        val item = items.optJSONObject(key) ?: return@material null
                        val count = item.opt("c")?.toString()?.toLongOrNull() ?: return@material null
                        item.optString("n").takeIf(String::isNotBlank)?.let { it to count }
                    }.toList()
                } ?: if (id == "0") emptyList() else null
                val preview = if (name == "charmtype") previews?.optString(id).orEmpty() else ""
                val details = Jsoup.parseBodyFragment(preview).select(".eq > div").flatMap { row ->
                    if (row.hasClass("ex") || row.hasClass("ep")) row.children().map { "" to it.hvText() }
                    else listOf("" to row.hvText())
                }.filter { it.second.isNotBlank() }
                ArmoryCharmOption(id, input.closest("label").hvText().ifBlank { input.attr("data-name") }, points,
                    input.attr("data-disabled").takeIf(String::isNotBlank)
                        ?: input.closest("tr")?.takeIf { it.attr("data-disabled") == "1" }?.attr("title")?.takeIf(String::isNotBlank),
                    itemMaterials, details)
            }
        val config = ArmoryCharmConfig(
            slot = integer("charmslot") ?: return null,
            maximumPoints = integer("max_cp") ?: return null,
            otherPoints = integer("exl_cp") ?: return null,
            currentCharm = integer("cur_charmtype")?.toString() ?: return null,
            currentPouch = integer("cur_pouchtype")?.toString() ?: return null,
            charmWorn = boolean("cur_charm_worn") ?: return null,
            charmBroken = boolean("cur_charm_broken") ?: return null,
            pouchBroken = boolean("cur_pouch_broken") ?: return null,
            charms = options("charmtype", charmMaterials),
            pouches = options("pouchtype", pouchMaterials),
        )
        if (config.charms.isEmpty() || config.pouches.isEmpty()) return null
        fun field(name: String, title: String, options: List<ArmoryCharmOption>, current: String) = BazaarSectionsField(
            name, title, document.selectFirst("#equipform input[name=$name][checked]")?.attr("value") ?: current,
            "select", options.map { it.id to "${it.name} · ${it.points} CP" }, required = true)
        return BazaarSectionsForm("armory-charm", "Charm Slot ${config.slot}", listOf(
            field("charmtype", "护符", config.charms, config.currentCharm),
            field("pouchtype", "护符袋", config.pouches, config.currentPouch)), listOf(action), armoryCharm = config)
    }
}
