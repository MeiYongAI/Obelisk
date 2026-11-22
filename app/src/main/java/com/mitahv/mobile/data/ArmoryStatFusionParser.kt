package com.mitahv.mobile.data

import com.mitahv.mobile.model.*
import okhttp3.HttpUrl
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** The real statfuse form posts a single donor equipment id to its current URL. */
internal object ArmoryStatFusionParser {
    fun parse(document: Document, world: HvWorld, currentUrl: HttpUrl, supplementalScripts: List<String> = emptyList()): BazaarSectionsPage {
        require(world.containsUrl(currentUrl) && currentUrl.queryParameter("s") == "Bazaar" &&
            currentUrl.queryParameter("ss") == "am" && currentUrl.queryParameter("screen") == "statfuse")
        val form = requireNotNull(document.selectFirst("#equipform"))
        require(form.attr("method").equals("post", true))
        val target = if (form.attr("action").isBlank()) currentUrl else requireNotNull(currentUrl.resolve(form.attr("action")))
        require(target == currentUrl) { "未取得当前 Stat Fusion 表单" }
        val script = document.select("script").joinToString("\n") { it.data() }
        val items = requireNotNull(HvScriptObjectParser.parse(script, "eqitems"))
        val materials = requireNotNull(HvScriptObjectParser.parse(script, "itemdata"))
        val sources = supplementalScripts + script
        val equipment = JSONObject().apply {
            sources.forEach { source -> HvScriptObjectParser.parse(source, "dynjs_equip")?.let { data ->
                data.keys().forEach { key -> put(key, data.get(key)) }
            } }
        }
        val balance = Regex("""\bcredit_balance\s*=\s*(\d+)""").find(script)?.groupValues?.get(1)?.toLongOrNull()
        val targetLink = document.selectFirst("#eqback a")
        val backUrl = targetLink?.attr("href")?.let(currentUrl::resolve)?.takeIf {
            world.containsUrl(it) && it.queryParameter("s") == "Bazaar" && it.queryParameter("ss") == "am"
        }
        val targetName = document.selectFirst("#itemlist a").hvText().ifBlank {
            Regex("""\bfusename\s*=\s*"([^"]+)"""").find(script)?.groupValues?.get(1).orEmpty()
        }
        val fields = mutableListOf<BazaarSectionsField>()
        val candidates = mutableListOf<ArmoryStatFusionCandidate>()
        form.select("#equiplist input[type=checkbox][name='eqids[]']").forEach { input ->
            val id = input.attr("value")
            val row = input.closest("tr") ?: return@forEach
            val protection = Regex("""confirm_unprotect\(this,\s*(\d+),\s*`([^`]*)`,\s*`([^`]*)`\)""").find(row.attr("onclick"))
                ?.takeIf { it.groupValues[1] == id && row.attr("data-eqprotect") == "1" }
            if (input.hasAttr("disabled") && protection == null) return@forEach
            val data = items.optJSONObject(id) ?: return@forEach
            val costs = data.optJSONObject("m") ?: return@forEach
            val stats = data.optJSONObject("s") ?: return@forEach
            val credits = data.opt("c")?.toString()?.toLongOrNull() ?: return@forEach
            val statRows = stats.keys().asSequence().mapNotNull { key ->
                val stat = stats.optJSONObject(key) ?: return@mapNotNull null
                val label = stat.optString("label").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val value = stat.opt("value")?.toString()?.toIntOrNull() ?: return@mapNotNull null
                val boost = stat.opt("boost")?.toString()?.toIntOrNull() ?: return@mapNotNull null
                val binding = stat.opt("bndid")?.toString()?.takeIf { it.toLongOrNull() != null } ?: return@mapNotNull null
                ArmoryStatFusionStat(label, value, boost, binding)
            }.toList()
            val materialRows = costs.keys().asSequence().mapNotNull { key ->
                val material = materials.optJSONObject(key) ?: return@mapNotNull null
                val name = material.optString("n").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val needed = costs.opt(key)?.toString()?.toLongOrNull() ?: return@mapNotNull null
                val owned = material.opt("c")?.toString()?.toLongOrNull() ?: return@mapNotNull null
                ArmoryStatFusionMaterial(key, name, needed, owned)
            }.toList()
            if (statRows.size != stats.length() || statRows.isEmpty() || materialRows.size != costs.length()) return@forEach
            val name = data.optString("t").ifBlank { input.closest("label").hvText() }
            val details = equipment.optJSONObject(id)?.optString("d")?.let { html ->
                Jsoup.parseBodyFragment(html).select(".eq > div").flatMap { row ->
                    if (row.hasClass("ex") || row.hasClass("ep")) row.children().map { "" to it.hvText() }
                    else listOf("" to row.hvText())
                }.filter { it.second.isNotBlank() }
            }.orEmpty()
            candidates += ArmoryStatFusionCandidate(id, name, credits, statRows, materialRows)
            fields += BazaarSectionsField("eqids[]", name, id, "checkbox", key = "eqids[]:$id", details = details,
                selectionConfirmation = protection?.let {
                    listOf("将受保护装备设为本次可选：", it.groupValues[2], "获得时间：${it.groupValues[3]}").joinToString("\n")
                })
        }
        val config = ArmoryStatFusionConfig(targetName, balance, candidates)
        val button = requireNotNull(form.selectFirst("#equipsubmit[type=submit]"))
        val action = NativeAction(target, "POST", form.select("input[type=hidden][name]:not([disabled])")
            .map { it.attr("name") to it.attr("value") }, button.attr("value"), "Stat Fusion",
            confirmationChecks = listOf("确认销毁所选装备", "确认此操作无法撤销"))
        val links = document.select("#armory_left > a").mapNotNull { link ->
            val url = currentUrl.resolve(link.attr("href"))?.takeIf {
                world.containsUrl(it) && it.queryParameter("s") == "Bazaar" && it.queryParameter("ss") == "am"
            } ?: return@mapNotNull null
            BazaarSectionsLink(link.hvText(), NativeAction(url, "GET", label = link.hvText()))
        }
        val emptyText = if (candidates.isEmpty()) document.selectFirst("#equiplist").hvText() else ""
        val fusionForm = BazaarSectionsForm("armory-stat-fusion", "Stat Fusion", fields, listOf(action),
            facts = listOf("融合至" to targetName), singleEquipment = true, armoryStatFusion = config)
        return BazaarSectionsPage(HvRoute.Armory, links = links,
            sections = if (emptyText.isBlank()) emptyList() else listOf(BazaarSectionsGroup("Stat Fusion", emptyText = emptyText)),
            forms = if (fields.isEmpty()) emptyList() else listOf(fusionForm),
            actions = listOfNotNull(backUrl?.let { NativeAction(it, "GET", label = "返回装备改装") }))
    }
}
