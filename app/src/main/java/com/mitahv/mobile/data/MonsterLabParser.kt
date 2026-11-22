package com.mitahv.mobile.data

import com.mitahv.mobile.model.BazaarSectionsEntry
import com.mitahv.mobile.model.BazaarSectionsField
import com.mitahv.mobile.model.BazaarSectionsForm
import com.mitahv.mobile.model.BazaarSectionsGroup
import com.mitahv.mobile.model.BazaarSectionsLink
import com.mitahv.mobile.model.BazaarSectionsPage
import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvWorld
import com.mitahv.mobile.model.NativeAction
import java.util.Locale
import okhttp3.HttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Forms and handlers from the supplied Monster Lab list, creation, stats and skills pages. */
object MonsterLabParser {
    fun parse(
        html: String,
        world: HvWorld,
        currentUrl: HttpUrl = HvRoute.MonsterLab.url(world),
        supplementalScripts: List<String> = emptyList(),
    ): BazaarSectionsPage = parse(Jsoup.parse(html, currentUrl.toString()), world, currentUrl, supplementalScripts)

    fun parse(document: Document, world: HvWorld, currentUrl: HttpUrl = HvRoute.MonsterLab.url(world),
        supplementalScripts: List<String> = emptyList()): BazaarSectionsPage {
        require(HvRoute.MonsterLab.isAvailableIn(world))
        require(world.containsUrl(currentUrl) && currentUrl.queryParameterValues("s") == listOf("Bazaar") &&
            currentUrl.queryParameterValues("ss") == listOf("ml"))
        require(document.selectFirst("#monster_outer") != null) { "未取得 Monster Lab 页面，请重新加载" }
        val page = Page(document, world, currentUrl, supplementalScripts).parse()
        return page.copy(messages = document.select("#messagebox_inner > p, #monsterstats_rename")
            .map { it.hvText() }.filter(String::isNotBlank).distinct())
    }

    private class Page(val doc: Document, val world: HvWorld, val current: HttpUrl, supplementalScripts: List<String>) {
        val scripts = (doc.select("script").map { it.data() } + supplementalScripts).joinToString("\n")

        fun parse(): BazaarSectionsPage = when {
            doc.selectFirst("#monstercreate_left") != null -> creation()
            doc.selectFirst("#skillform #monsterskills") != null -> skills()
            doc.selectFirst("#monsterstats_left") != null -> statistics()
            else -> overview()
        }

        private fun url(value: String): HttpUrl? = (if (value.isBlank()) current else current.resolve(value))?.takeIf {
            world.containsUrl(it) && it.queryParameterValues("s") == listOf("Bazaar") && it.queryParameterValues("ss") == listOf("ml")
        }

        private fun destination(node: Element): HttpUrl? {
            if (!enabled(node)) return null
            if (node.hasAttr("href")) return url(node.attr("href"))
            val match = LOCATION.matchEntire(node.attr("onclick")) ?: return null
            val target = match.groupValues[2]
            if (match.groupValues[1].single() in target || '\\' in target || "\${" in target) return null
            return url(target)
        }

        private fun get(node: Element, label: String): NativeAction? =
            destination(node)?.let { NativeAction(it, method = "GET", label = label) }

        private fun hidden(form: Element): List<Pair<String, String>> = form.select("input[type=hidden][name]")
            .filterNot { it.hasAttr("disabled") }.map { it.attr("name") to it.attr("value") }

        private fun post(form: Element?, label: String, fields: List<Pair<String, String>> = emptyList(), confirmation: String? = null): NativeAction? {
            if (form == null || !form.attr("method").equals("post", true)) return null
            val target = url(form.attr("action")) ?: return null
            val names = fields.map { it.first }.toSet()
            return NativeAction(target, method = "POST", fields = hidden(form).filterNot { it.first in names } + fields,
                label = label, confirmation = confirmation)
        }

        private fun overview(): BazaarSectionsPage {
            val entries = doc.select("#slot_pane > div[onclick]").mapIndexedNotNull { index, node ->
                val cells = node.children().map { it.hvText() }
                val name = cells.getOrNull(1).orEmpty().ifBlank { "Slot ${cells.firstOrNull().orEmpty()}" }
                val action = get(node, name) ?: return@mapIndexedNotNull null
                BazaarSectionsEntry("monster-$index", name, slotFacts(node), action)
            }
            val actions = doc.select("#monster_actions img[onclick]").filter(::enabled).mapNotNull { node ->
                val click = handler(node)
                val info = node.parent()?.parent().hvText()
                when (click) {
                    "do_feed_all('food')" -> post(doc.selectFirst("#feed_form"), "Feed All", listOf("feed_all" to "food"), "Feed All\n$info")
                    "do_feed_all('drugs')" -> post(doc.selectFirst("#feed_form"), "Give Happy Pills", listOf("feed_all" to "drugs"), "Give Happy Pills\n$info")
                    "do_unlock_slot()" -> post(doc.selectFirst("#unlock_form"), "Unlock Slot", listOf("unlock_slot" to "1"), "Unlock Slot\n$info")
                    else -> get(node, "Create Monster")
                }
            }
            val costs = doc.select("#monster_actions > div").map { "" to it.hvText() }.filter { it.second.isNotBlank() }
            return BazaarSectionsPage(HvRoute.MonsterLab,
                sections = listOf(BazaarSectionsGroup("Monster Lab", entries, costs,
                    if (entries.isEmpty() && doc.selectFirst("#slot_pane") == null) "未取得怪物页面内容，请重新加载"
                    else if (entries.isEmpty()) "没有已解锁的怪物栏位" else "")), actions = actions)
        }

        private fun creation(): BazaarSectionsPage {
            val form = doc.selectFirst("#create_form")
            val selectedClass = form?.selectFirst("input[name=selected_class]")?.attr("value").orEmpty()
            var selectedName = ""
            val links = doc.select("#monstercreate_left img").mapNotNull { image ->
                val active = imageKey(image).endsWith("_a") && selectedClass != "0"
                val id = SELECT_CLASS.matchEntire(image.attr("onclick"))?.groupValues?.get(1)
                    ?: selectedClass.takeIf { active } ?: return@mapNotNull null
                if (!enabled(image)) return@mapNotNull null
                val name = imageLabel(image)
                if (active) selectedName = name
                val action = post(form, name, listOf("selected_class" to id)) ?: return@mapNotNull null
                BazaarSectionsLink(name, action, active)
            }
            val groups = mutableListOf<BazaarSectionsGroup>()
            if (selectedName.isNotBlank()) {
                val description = doc.selectFirst("#monstercreate_info").hvText()
                groups += BazaarSectionsGroup("", entries = listOf(BazaarSectionsEntry("class-description", selectedName,
                    moreDetails = listOf("" to description).filter { it.second.isNotBlank() })))
            }
            doc.select("#monstercreate_stats > div").forEach { section ->
                groups += BazaarSectionsGroup(section.children().firstOrNull().hvText(), facts = section.select("table tr").mapNotNull(::pair))
            }
            val attacks = doc.select("#monstercreate_patk img").mapNotNull { image ->
                val type = CREATE_MONSTER.matchEntire(image.attr("onclick"))?.groupValues?.get(1) ?: return@mapNotNull null
                val name = imageLabel(image)
                val action = if (enabled(image) && selectedClass.toIntOrNull()?.let { it > 0 } == true)
                    post(form, "创建", listOf("selected_patk" to type), "创建 $selectedName\n伤害类型：$name") else null
                BazaarSectionsEntry("create-$type", name, action = action, enabled = action != null)
            }
            if (attacks.isNotEmpty()) groups += BazaarSectionsGroup("创建怪物", entries = attacks)
            return BazaarSectionsPage(HvRoute.MonsterLab, links, groups)
        }

        private fun navigation(): List<BazaarSectionsLink> {
            val links = mutableListOf<BazaarSectionsLink>()
            doc.selectFirst("#monster_head > [onclick]")?.let { node ->
                get(node, "怪物列表")?.let { links += BazaarSectionsLink(it.label, it) }
            }
            doc.select("#monster_nav a[href], #monster_nav img[onclick]").forEach { node ->
                val image = if (node.tagName() == "img") node else node.selectFirst("img") ?: return@forEach
                val label = when (imageKey(image).removeSuffix("_a")) {
                    "prev" -> "上一只"; "next" -> "下一只"; "ml_monstats" -> "属性"; "ml_skilledit" -> "技能"
                    else -> return@forEach
                }
                val action = get(node, label) ?: return@forEach
                val selected = label in setOf("属性", "技能") && action.url.queryParameter("slot") == current.queryParameter("slot") &&
                    action.url.queryParameter("pane") == current.queryParameter("pane")
                links += BazaarSectionsLink(label, action, selected)
            }
            return links
        }

        private fun summary(): BazaarSectionsGroup? = doc.selectFirst("#monster_head > .msl")?.let { row ->
            BazaarSectionsGroup(row.children().getOrNull(1).hvText(), facts = slotFacts(row))
        }

        private fun slotFacts(row: Element): List<Pair<String, String>> {
            val cells = row.children().map { it.hvText() }
            return listOf("Slot" to cells.getOrNull(0).orEmpty(), "Level" to cells.getOrNull(2).orEmpty().removePrefix("Lvl "),
                "Species" to cells.getOrNull(3).orEmpty()).filter { it.second.isNotBlank() } +
                listOfNotNull(monsterBar(row, 4, "Hunger"), monsterBar(row, 5, "Morale"))
        }

        private fun monsterBar(row: Element, index: Int, label: String): Pair<String, String>? {
            val cell = row.children().getOrNull(index)?.takeIf { it.hasClass("msn") } ?: return null
            val image = cell.selectFirst("div > img") ?: return null
            val width = Regex("(?:^|;)\\s*width\\s*:\\s*([\\d.]+)px(?:\\s*;|\\s*$)")
                .find(image.attr("style"))?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
            // hvo.css positions the fill at x=64; hungerbar/moralebar PNGs have
            // their 120-pixel interior at x=64..183 (the frame starts at x=62).
            if (width !in 0.0..120.0) return null
            return label to String.format(Locale.ROOT, "%.1f%%", width * 100.0 / 120.0)
        }

        private fun statistics(): BazaarSectionsPage {
            val groups = mutableListOf<BazaarSectionsGroup>()
            summary()?.let(groups::add)
            val feed = doc.select("#monsterstats_top > div:first-child > div").mapNotNull { block ->
                val image = block.selectFirst("img") ?: return@mapNotNull null
                val key = imageKey(image).removeSuffix("_d")
                val label = when (key) { "feedmonster" -> "喂食"; "drugmonster" -> "快乐药丸"; else -> return@mapNotNull null }
                val mode = FEED.matchEntire(image.attr("onclick"))?.groupValues?.get(1)
                val details = block.children().drop(1).map { "" to it.hvText() }.filter { it.second.isNotBlank() }
                val action = if (mode != null && enabled(image)) post(doc.selectFirst("#food_form"), label,
                    listOf("food_action" to mode), (listOf(label) + details.map { it.second }).joinToString("\n")) else null
                BazaarSectionsEntry("feed-$key", label, details, action, enabled = action != null)
            }
            if (feed.isNotEmpty()) groups += BazaarSectionsGroup("", entries = feed)
            doc.select("#monsterstats_top > .mcr").forEachIndexed { index, group ->
                val rows = group.select("table tr").mapIndexedNotNull { rowIndex, row ->
                    val cells = row.children().filter { it.tagName() == "td" }
                    val image = cells.firstOrNull()?.selectFirst("img") ?: return@mapIndexedNotNull null
                    val name = imageLabel(image)
                    val help = tooltip(image)
                    val material = CRYSTAL_COST.matchEntire(help)
                    val details = listOfNotNull(
                        cells.getOrNull(2)?.hvText()?.let { "属性" to "$it (${cells.getOrNull(1).hvText()})" },
                        material?.let { "晶石" to it.groupValues[1] },
                        material?.let { "下次消耗 / 库存" to "${it.groupValues[2]} / ${it.groupValues[3]}" },
                    )
                    val upgrade = CRYSTAL_UPGRADE.matchEntire(image.attr("onclick"))?.groupValues?.get(1)
                    val choices = if (enabled(image) && upgrade != null) upgrades("crystal", upgrade, name, help) else emptyList()
                    BazaarSectionsEntry("crystal-$index-$rowIndex", name, details, choices = choices, enabled = choices.isNotEmpty(),
                        moreDetails = if (material == null && help.isNotBlank()) listOf("" to help) else emptyList())
                }
                groups += BazaarSectionsGroup(group.children().firstOrNull().hvText(), entries = rows)
            }
            val chaos = doc.select("#chaosupg tr").mapIndexedNotNull { index, row ->
                val cells = row.children().filter { it.tagName() == "td" }
                val name = cells.firstOrNull().hvText().takeIf(String::isNotBlank) ?: return@mapIndexedNotNull null
                val image = cells.getOrNull(2)?.selectFirst("img") ?: return@mapIndexedNotNull null
                val help = tooltip(image)
                val cost = CHAOS_COST.matchEntire(help)
                val level = cells.getOrNull(1)?.select(".mcu2")?.size ?: 0
                val total = cells.getOrNull(1)?.select(".mcu0, .mcu1, .mcu2")?.size ?: 0
                val upgrade = CHAOS_UPGRADE.matchEntire(image.attr("onclick"))?.groupValues?.get(1)
                val choices = if (enabled(image) && upgrade != null) upgrades("chaos", upgrade, name, help) else emptyList()
                BazaarSectionsEntry("chaos-$index", name,
                    details = listOfNotNull(("等级" to "$level / $total").takeIf { total > 0 },
                        cost?.let { "下次消耗 / 库存" to "${it.groupValues[1]} / ${it.groupValues[2]} Chaos Tokens" }),
                    choices = choices, enabled = choices.isNotEmpty(),
                    moreDetails = listOfNotNull(cells.getOrNull(3)?.hvText()?.takeIf(String::isNotBlank)?.let { "" to it },
                        help.takeIf { cost == null && it.isNotBlank() }?.let { "" to it }))
            }
            if (chaos.isNotEmpty()) groups += BazaarSectionsGroup("Chaos", entries = chaos)
            doc.selectFirst("#monsterstats_right")?.let { right ->
                val facts = right.select(".mst").mapNotNull(::pair).map { (label, value) ->
                    // The percentage stat differs from the same-named Chaos upgrade.
                    (if (label == "Accuracy") "命中率" else label) to value
                }
                groups += BazaarSectionsGroup(right.children().firstOrNull().hvText(), facts = facts)
            }
            val forms = mutableListOf<BazaarSectionsForm>()
            if (doc.select("#monsterstats_rendel img").any { enabled(it) && handler(it) == "do_rename()" }) {
                val form = doc.selectFirst("#rename_form")
                val input = form?.selectFirst("input[name=rename_monster]")
                val action = post(form, "保存名称")
                val currentName = RENAME_DEFAULT.find(scripts)?.groupValues?.get(3) ?: summary()?.title.orEmpty()
                if (input != null && action != null) forms += BazaarSectionsForm("monster-rename", "怪物名称",
                    listOf(BazaarSectionsField(input.attr("name"), "名称", currentName, required = true)), listOf(action))
            }
            val actions = if (doc.select("#monsterstats_rendel img").any { enabled(it) && handler(it) == "do_delete()" }) {
                DELETE_MONSTER.find(scripts)?.let { handler ->
                    post(doc.selectFirst("#delete_form"), "删除怪物", listOf("delete_monster" to handler.groupValues[4]),
                        "删除 ${summary()?.title.orEmpty()}\n${handler.groupValues[2]}")
                }?.let(::listOf).orEmpty()
            } else emptyList()
            return BazaarSectionsPage(HvRoute.MonsterLab, navigation(), groups, forms, actions)
        }

        private fun upgrades(kind: String, upgrade: String, name: String, help: String): List<NativeAction> {
            val form = doc.selectFirst("#${kind}_form") ?: return emptyList()
            val field = "${kind}_count"
            val default = form.selectFirst("input[name=$field]")?.attr("value")?.toIntOrNull() ?: return emptyList()
            val assignments = Regex("""e\(\s*['"]${Regex.escape(field)}['"]\s*\)\.value\s*=\s*(\d+)\s*;""")
                .findAll(scripts).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
            return (listOf(default) + assignments).filter { it > 0 }.distinct().sorted().mapNotNull { count ->
                post(form, "升级 $count 次", listOf("${kind}_upgrade" to upgrade, field to count.toString()),
                    "$name · 升级 $count 次" + if (help.isNotBlank()) "\n下一次升级：$help" else "")
            }
        }

        private fun skills(): BazaarSectionsPage {
            val form = requireNotNull(doc.selectFirst("#skillform"))
            val fields = mutableListOf<BazaarSectionsField>()
            val facts = mutableListOf<Pair<String, String>>()
            val headings = form.select("#monsterskills th").map { it.hvText() }
            form.select("#monsterskills tr").forEach { row ->
                val name = row.selectFirst("input[name^=skill_name_]")?.takeIf(::enabled) ?: return@forEach
                val id = name.attr("name").removePrefix("skill_name_")
                val label = name.attr("placeholder").ifBlank { name.attr("value") }.ifBlank { "Skill $id" }
                fields += BazaarSectionsField(name.attr("name"), "$label · 名称", name.attr("value"), maxLength = name.attr("maxlength").toIntOrNull())
                listOf("type", "elem").forEach propertyLoop@ { property ->
                    val key = "${property}_$id"
                    val input = row.selectFirst("#skill_$key") ?: return@propertyLoop
                    val display = row.selectFirst("#skill_display_$key") ?: return@propertyLoop
                    val shown = SHOW_PROPERTIES.matchEntire(display.attr("onclick"))?.groupValues?.get(1) == key
                    val options = row.select("#skill_select_$key > [onclick]").mapNotNull { option ->
                        val selected = SET_PROPERTY.matchEntire(option.attr("onclick")) ?: return@mapNotNull null
                        if (selected.groupValues[1] != key || !enabled(option)) return@mapNotNull null
                        selected.groupValues[2] to option.hvText()
                    }.distinctBy { it.first }
                    if (shown && enabled(input) && options.isNotEmpty()) fields += BazaarSectionsField(input.attr("name"),
                        "$label · ${if (property == "type") headings.getOrNull(1) ?: "攻击类型" else headings.getOrNull(2) ?: "伤害类型"}",
                        input.attr("value"), kind = "select", options = options)
                }
                val cells = row.children().filter { it.tagName() == "td" }
                val metadata = cells.mapIndexedNotNull { index, cell ->
                    when (index) {
                        1 -> row.selectFirst("#skill_display_type_$id").hvText().takeIf { fields.none { field -> field.name == "skill_type_$id" } }
                        2 -> row.selectFirst("#skill_display_elem_$id").hvText().takeIf { fields.none { field -> field.name == "skill_elem_$id" } }
                        in 3..5 -> "${headings.getOrNull(index).orEmpty()} ${cell.hvText()}".trim()
                        else -> null
                    }?.takeIf(String::isNotBlank)
                }
                if (metadata.isNotEmpty()) facts += label to metadata.joinToString(" · ")
            }
            val control = form.select("img[onclick]").firstOrNull { enabled(it) && SAVE_SKILLS.matches(it.attr("onclick")) }
            val action = control?.let { post(form, "保存技能") }
            val forms = if (action == null) emptyList() else listOf(BazaarSectionsForm("monster-skills", "技能", fields, listOf(action), facts))
            return BazaarSectionsPage(HvRoute.MonsterLab, navigation(), listOfNotNull(summary()), forms)
        }
    }

    private fun pair(row: Element): Pair<String, String>? {
        val children = row.children()
        if (children.size != 2) return null
        return (children[0].hvText() to children[1].hvText()).takeIf { it.first.isNotBlank() && it.second.isNotBlank() }
    }

    private fun handler(node: Element) = node.attr("onclick").trim().removeSuffix(";").trim()
    private fun enabled(node: Element) = !node.hasAttr("disabled") && !node.attr("aria-disabled").equals("true", true) &&
        !imageKey(node).endsWith("_d")
    private fun imageKey(node: Element) = node.attr("src").substringBefore('?').substringAfterLast('/').substringBeforeLast('.')
    private fun imageLabel(node: Element): String {
        val key = imageKey(node).removeSuffix("_a").removeSuffix("_d")
        return IMAGE_LABELS[key] ?: key.replaceFirstChar { it.titlecase() }
    }
    private fun tooltip(node: Element): String {
        val match = UPGRADE_TOOLTIP.matchEntire(node.attr("onmouseover")) ?: return ""
        return Jsoup.parseBodyFragment(match.groupValues[2]).hvText()
    }

    private val LOCATION = Regex("""\s*document\.location(?:\.href)?\s*=\s*(['"`])([\s\S]*?)\1\s*;?\s*""")
    private val SELECT_CLASS = Regex("""\s*select_class\(\s*(\d+)\s*\)\s*;?\s*""")
    private val CREATE_MONSTER = Regex("""\s*create_monster\(\s*['"]([a-z]+)['"]\s*\)\s*;?\s*""")
    private val FEED = Regex("""\s*do_feed\(\s*['"](food|drugs)['"]\s*\)\s*;?\s*""")
    private val CRYSTAL_UPGRADE = Regex("""\s*do_crystal_upgrade\(\s*['"]([a-z_]+)['"]\s*,\s*event\s*\)\s*;?\s*""")
    private val CHAOS_UPGRADE = Regex("""\s*do_chaos_upgrade\(\s*['"]([a-z_]+)['"]\s*,\s*event\s*\)\s*;?\s*""")
    private val UPGRADE_TOOLTIP = Regex("""\s*show_upgrade_text\(\s*(['"])([\s\S]*?)\1\s*\)\s*;?\s*""")
    private val CRYSTAL_COST = Regex("""Upgrade With:\s*(.+?)\s+Cost:\s*([\d,]+)\s+Stock:\s*([\d,]+)""")
    private val CHAOS_COST = Regex("""Upgrade Cost:\s*([\d,]+)\s+Chaos Tokens?\s+Stock:\s*([\d,]+)""")
    private val RENAME_DEFAULT = Regex("""function\s+do_rename\(\s*\)\s*\{\s*(?:var|let|const)\s+monstername\s*=\s*prompt\(\s*(['"])[^'"\\]*\1\s*,\s*(['"])([^'"\\]*)\2\s*\)""")
    private val DELETE_MONSTER = Regex("""function\s+do_delete\(\s*\)\s*\{\s*if\s*\(\s*confirm\(\s*(['"])([^'"\\\r\n]+)\1\s*\)\s*\)\s*\{\s*e\(\s*['"]delete_monster['"]\s*\)\.value\s*=\s*(['"])([A-Za-z0-9_-]+)\3\s*;\s*e\(\s*['"]delete_form['"]\s*\)\.submit\(\s*\)\s*;?\s*\}\s*\}""")
    private val SHOW_PROPERTIES = Regex("""\s*show_properties\(\s*['"]([a-z_0-9]+)['"]\s*\)\s*;?\s*""")
    private val SET_PROPERTY = Regex("""\s*set_property\(\s*this\s*,\s*['"]([a-z_0-9]+)['"]\s*,\s*['"]([a-z]+)['"]\s*\)\s*;?\s*""")
    private val SAVE_SKILLS = Regex("""\s*e\(\s*['"]skillform['"]\s*\)\.submit\(\s*\)\s*;?\s*""")
    private val IMAGE_LABELS = mapOf("str" to "Strength", "dex" to "Dexterity", "agi" to "Agility", "end" to "Endurance",
        "int" to "Intelligence", "wis" to "Wisdom", "crsh" to "Crushing", "slsh" to "Slashing", "prcg" to "Piercing",
        "fire" to "Fire", "cold" to "Cold", "elec" to "Elec", "wind" to "Wind", "holy" to "Holy", "dark" to "Dark")
}
