package com.mitahv.mobile.data

import com.mitahv.mobile.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Actions from the supplied upgrade page and its official hveqc.js confirmation controls. */
internal object ArmoryUpgradeParser {
    fun parse(document: Document, basePostAction: NativeAction): BazaarSectionsGroup? {
        val root = document.selectFirst("#equpgrade") ?: return null
        val facts = root.select("p").map { "" to it.hvText() }
            .filter { it.second.isNotBlank() && it.second != "Next Tier Materials:" } +
            root.select("#upgrmats tr").mapNotNull { row ->
                val cells = row.select("td").map { it.hvText() }
                when {
                    cells.size >= 3 -> cells[1] to "${cells[0]} · 持有 ${cells[2].trim('(', ')')}"
                    cells.size == 1 && cells[0].isNotBlank() -> "" to cells[0]
                    else -> null
                }
            }
        val entries = root.select("button[disabled]").mapIndexed { index, button ->
            BazaarSectionsEntry("upgrade-disabled-$index", button.hvText(),
                details = listOfNotNull(button.attr("title").takeIf(String::isNotBlank)?.let { "" to it }), enabled = false)
        }.toMutableList()
        root.select("button[onclick]:not([disabled])").firstOrNull {
            Regex("""\s*pop_upgrade\(\s*\)\s*;\s*return false\s*;?\s*""").matches(it.attr("onclick"))
        }?.let { button ->
            val title = document.selectFirst("#equipinfo .showequip > div").hvText()
            // pop_upgrade creates this named submit control; targetlevel is already a hidden field.
            val action = basePostAction.copy(method = "POST", label = button.hvText(),
                fields = basePostAction.fields.filterNot { it.first == "action" } + ("action" to "upgrade"),
                confirmation = (listOf(title, "升级将消耗所列材料和 Credits；Credits 与 Cores 不可退还。") +
                    facts.map { (label, value) -> if (label.isBlank()) value else "$label：$value" }).joinToString("\n"))
            entries += BazaarSectionsEntry("upgrade-equipment", button.hvText(), action = action)
        }
        return BazaarSectionsGroup("Upgrade", entries, facts)
    }

    fun itemWorldAction(document: Document, world: HvWorld, basePostAction: NativeAction): NativeAction? {
        val button = document.select("#equpgrade button[onclick]:not([disabled])").firstOrNull {
            Regex("""\s*armory_itemworld\(this\)\s*;\s*return false\s*;?\s*""").matches(it.attr("onclick"))
        } ?: return null
        val script = document.select("script").joinToString("\n") { it.data() }
        val body = Regex("""function\s+armory_itemworld\(elem\)\s*\{([\s\S]*?)\}""")
            .find(script)?.groupValues?.get(1) ?: return null
        val confirm = Regex("""confirm_action\(elem,\s*`([^`]*)`,\s*`([^`]*)`,\s*false,\s*`([^`]*)`\)""")
            .find(body) ?: return null
        val url = basePostAction.url.resolve(confirm.groupValues[3])?.takeIf {
            world.containsUrl(it) && it.queryParameterValues("s") == listOf("Battle") &&
                it.queryParameterValues("ss") == listOf("iw")
        } ?: return null
        val info = HvScriptObjectParser.parse(script, "info_itemworld") ?: return null
        val details = listOf("World Level" to info.optString("iwlvl"), "Battle Rounds" to info.optString("rounds"),
            "Monster LVL" to info.optString("monsterlevel"), "Difficulty" to (info.optString("diffname") +
                if (info.optInt("diffboost") > 0) " +${info.optInt("diffboost")}%" else ""),
            "World Seeds" to "${info.optString("req_worldseeds")} · 持有 ${info.optString("has_worldseeds")}")
        val equipment = document.selectFirst("#equipinfo .showequip > div").hvText()
        // confirm_action uses formaction on an unnamed submit input: preserve hidden fields, add no action value.
        return basePostAction.copy(url = url, method = "POST", label = button.hvText(),
            confirmation = (listOf(Jsoup.parseBodyFragment(confirm.groupValues[2]).hvText(), equipment) +
                details.map { (label, value) -> "$label：$value" }).joinToString("\n"))
    }
}
