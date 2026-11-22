package com.mitahv.mobile.data

import com.mitahv.mobile.model.*
import okhttp3.HttpUrl
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Native Bazaar controls decoded from the supplied official HTML and hvc.js. */
object BazaarSectionsParser {
    fun parse(html: String, route: HvRoute, world: HvWorld, currentUrl: HttpUrl = route.url(world), supplementalScripts: List<String> = emptyList()): BazaarSectionsPage =
        parse(Jsoup.parse(html, currentUrl.toString()), route, world, currentUrl, supplementalScripts)

    fun parse(document: Document, route: HvRoute, world: HvWorld, currentUrl: HttpUrl = route.url(world),
        supplementalScripts: List<String> = emptyList()): BazaarSectionsPage {
        require(route.category == HvSection.Bazaar && route.isAvailableIn(world))
        require(world.containsUrl(currentUrl) &&
            currentUrl.queryParameterValues("s") == listOf(route.section) && currentUrl.queryParameterValues("ss") == listOf(route.subsection))
        val context = Page(document, route, world, currentUrl, supplementalScripts)
        val root = when (route) {
            HvRoute.Bazaar -> "#itshop_outer"; HvRoute.Shrine -> "#shrine_outer"; HvRoute.Market -> "#market_outer"
            HvRoute.Armory -> "#armory_outer"; HvRoute.MonsterLab -> "#monster_outer"; HvRoute.MoogleMail -> "#mmail_outer"
            else -> "#lotteryform"
        }
        require(context.doc.selectFirst(root) != null) { "未取得 ${route.label} 页面，请重新加载" }
        val page = when (route) {
            HvRoute.Bazaar -> context.shop()
            HvRoute.Shrine -> context.shrine()
            HvRoute.Market -> context.market()
            HvRoute.Armory -> context.armory()
            HvRoute.MonsterLab -> MonsterLabParser.parse(document, world, currentUrl, supplementalScripts)
            HvRoute.MoogleMail -> context.mail()
            HvRoute.WeaponLottery, HvRoute.ArmorLottery -> context.lottery()
            else -> error("Unsupported Bazaar route")
        }
        val messages = context.doc.select("#messagebox_inner > p").map { it.hvText() }.filter(String::isNotBlank)
        return page.copy(messages = (page.messages + messages).distinct())
    }

    private class Page(val doc: Document, val route: HvRoute, val world: HvWorld, val current: HttpUrl, supplementalScripts: List<String>) {
        private val sources = supplementalScripts + doc.select("script").map { it.data() }
        private fun dictionary(name: String): JSONObject = JSONObject().apply {
            sources.forEach { source -> HvScriptObjectParser.parse(source, name)?.let { data -> data.keys().forEach { key -> put(key, data.get(key)) } } }
        }
        private val equipment by lazy { dictionary("dynjs_equip") }
        private val storeEquipment by lazy { dictionary("dynjs_eqstore") }
        private val itemDescriptions by lazy { dictionary("dynjs_itemc") }
        private fun equipment(id: String): JSONObject? = storeEquipment.optJSONObject(id) ?: equipment.optJSONObject(id)
        private fun url(value: String): HttpUrl? {
            val target = if (value.isBlank()) current else current.resolve(value) ?: return null
            return target.takeIf {
                world.containsUrl(it) && it.queryParameterValues("s") == listOf(route.section) &&
                    it.queryParameterValues("ss") == listOf(route.subsection)
            }
        }
        private fun destination(node: Element): HttpUrl? {
            if (node.hasAttr("href")) return url(node.attr("href"))
            return LOCATION.find(node.attr("onclick"))?.groupValues?.get(1)?.let(::url)
        }
        private fun get(node: Element, label: String = node.hvText()): NativeAction? =
            destination(node)?.let { NativeAction(it, method = "GET", label = label) }
        private fun links(selector: String): List<BazaarSectionsLink> = doc.select(selector).mapNotNull { node ->
            val title = node.hvText().ifBlank { imageLabel(node) }
            val action = get(node, title) ?: return@mapNotNull null
            if (title.isBlank()) return@mapNotNull null
            BazaarSectionsLink(title, action, node.selectFirst(".cfbs, .market_cur, .armory_cur") != null || action.url == current)
        }.distinctBy { it.action.url }
        private fun marketItemTitle(): String? = doc.select("#market_itemheader a").map { it.hvText().trim() }
            .firstOrNull { it.isNotBlank() && it !in setOf("◀", "▶") }
        private fun marketListReturnLink(): BazaarSectionsLink? {
            if (current.queryParameter("screen") !in setOf("browseitems", "buyorders", "sellorders") || current.queryParameter("itemid").isNullOrBlank()) return null
            val listUrl = current.newBuilder().removeAllQueryParameters("itemid").build()
            val action = NativeAction(listUrl, method = "GET", label = "返回列表")
            return BazaarSectionsLink("返回列表", action)
        }
        private fun marketItemLinks(): List<BazaarSectionsLink> = doc.select("#market_itemheader a").mapNotNull { node ->
            val title = when (node.hvText().trim()) {
                "◀" -> "Previous item"
                "▶" -> "Next item"
                else -> return@mapNotNull null
            }
            val action = get(node, title) ?: return@mapNotNull null
            BazaarSectionsLink(title, action, action.url == current)
        }.distinctBy { it.action.url }
        private fun hidden(form: Element): List<Pair<String, String>> = form.select("input[type=hidden][name]")
            .filterNot { it.hasAttr("disabled") }.map { it.attr("name") to it.attr("value") }
        private fun post(form: Element?, label: String, fields: List<Pair<String, String>> = emptyList(), confirmation: String? = null): NativeAction? {
            if (form == null) return null
            val target = url(form.attr("action")) ?: return null
            val names = fields.map { it.first }.toSet()
            return NativeAction(target, form.attr("method").ifBlank { "GET" }.uppercase(),
                hidden(form).filterNot { it.first in names } + fields, label, confirmation)
        }
        private fun variable(name: String): Long? = Regex("\\b${Regex.escape(name)}\\s*=\\s*(\\d+)")
            .find(doc.select("script").joinToString("\n") { it.data() })?.groupValues?.get(1)?.toLongOrNull()

        fun shop(): BazaarSectionsPage {
            val form = doc.selectFirst("#shopform")
            val credits = variable("current_credits")
            val owned = doc.select("#item_pane [onclick]").mapNotNull { node ->
                val match = SHOP.find(node.attr("onclick")) ?: return@mapNotNull null
                if (match.groupValues[1] != "item_pane") return@mapNotNull null
                match.groupValues[2] to (match.groupValues[3].toLongOrNull() ?: return@mapNotNull null)
            }.toMap()
            val groups = listOf("item_pane" to "Your Inventory", "shop_pane" to "Item Shop").map { (pane, title) ->
                val entries = doc.select("#$pane [onclick]").mapNotNull { node ->
                    val match = SHOP.find(node.attr("onclick")) ?: return@mapNotNull null
                    if (match.groupValues[1] != pane) return@mapNotNull null
                    val id = match.groupValues[2]
                    val stock = match.groupValues[3].toLongOrNull() ?: return@mapNotNull null
                    val price = match.groupValues[4].toLongOrNull() ?: return@mapNotNull null
                    val buying = pane == "shop_pane"
                    val label = if (buying) "购买" else "出售"
                    val action = post(form, label, listOf("select_mode" to pane, "select_item" to id), "$label ${node.hvText()}")
                    val maximum = if (buying && price > 0 && credits != null) minOf(stock, credits / price) else stock
                    val description = itemDescriptions.optJSONObject(id)?.optString("q").orEmpty().ifBlank {
                        shopTooltipStrings(node.attr("onmouseover")).getOrNull(3).orEmpty()
                    }
                    BazaarSectionsEntry("$pane-$id", node.hvText(), listOf("库存" to stock.toString(), "单价" to "$price C"),
                        quantity = action?.let { BazaarSectionsQuantity(it, "select_count", maximum, price) }, enabled = maximum > 0,
                        moreDetails = description.takeIf(String::isNotBlank)?.let { listOf("" to Jsoup.parseBodyFragment(it).hvText()) }.orEmpty(),
                        shopItem = BazaarShopItem(id, stock, owned[id] ?: 0))
                }
                BazaarSectionsGroup(title, entries, emptyText = doc.selectFirst("#$pane").hvText().takeIf { entries.isEmpty() }.orEmpty())
            }
            return BazaarSectionsPage(route, links("#filterbar a"), groups,
                messages = emptyList(), shopBalance = credits).let { page -> if (credits == null) page else page.copy(sections =
                    listOf(BazaarSectionsGroup("Credits", facts = listOf("余额" to "$credits C"))) + groups) }
        }

        /** Decode the same quoted tooltip literals used by the inventory page; never execute JavaScript. */
        private fun shopTooltipStrings(call: String): List<String> {
            if (!call.trimStart().startsWith("common.show_itemr_box(")) return emptyList()
            val result = mutableListOf<String>()
            var i = call.indexOf('(') + 1
            while (i < call.length) {
                val quote = call[i++]
                if (quote != '\'' && quote != '"') continue
                val value = StringBuilder()
                while (i < call.length) {
                    val c = call[i++]
                    if (c == quote) break
                    if (c == '\\' && i < call.length) value.append(call[i++]) else value.append(c)
                }
                result += value.toString()
            }
            return result
        }

        fun shrine(): BazaarSectionsPage {
            val form = doc.selectFirst("#shopform")
            val rewards = doc.select("#accept_equip input[onclick]").mapNotNull { node ->
                val match = REWARD.find(node.attr("onclick")) ?: return@mapNotNull null
                val type = match.groupValues[1]
                val group = when (type) { "armor_cloth" -> "Cloth"; "armor_light" -> "Light"; "armor_heavy" -> "Heavy"; else -> "" }
                Triple(listOf(group, node.attr("value")).filter(String::isNotBlank).joinToString(" "), type, match.groupValues[2])
            }
            val entries = doc.select("#inv_item [onclick]").mapNotNull { node ->
                val match = SHRINE.find(node.attr("onclick")) ?: return@mapNotNull null
                val id = match.groupValues[1]
                val count = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                val required = match.groupValues[3].toLongOrNull() ?: return@mapNotNull null
                val trophy = id.toIntOrNull()?.let { it in 30000..39999 } == true
                val options = if (trophy) rewards else listOf(Triple(doc.selectFirst("#accept_reward")?.attr("value").orEmpty().ifBlank { "Random Reward" }, "", ""))
                val actions = options.mapNotNull { (label, type, slot) ->
                    post(form, label, listOf("select_item" to id, "select_reward_type" to type, "select_reward_slot" to slot),
                        "献祭 $required × ${node.hvText()}\n奖励：$label")
                }
                BazaarSectionsEntry(id, node.hvText(), listOf("持有 / 需要" to "$count / $required"),
                    action = actions.singleOrNull(), choices = if (actions.size > 1) actions else emptyList(), enabled = count >= required && required > 0)
            }
            val rules = doc.select("#shrine_artifact p, #shrine_artifact li, #shrine_trophy p, #shrine_collectible p")
                .map { it.hvText() }.filter(String::isNotBlank)
            val facts = listOfNotNull(
                Regex("You cannot currently receive more than ([+\\d,]+) to any primary stat")
                    .find(doc.selectFirst("#shrine_artifact").hvText())?.let { "Primary Stat Bonus Limit" to it.groupValues[1] },
                Regex("You have handed in ([\\d,]+) credits worth of trophies")
                    .find(doc.selectFirst("#shrine_trophy").hvText())?.let { "Trophy Value Offered" to "${it.groupValues[1]} C" },
            )
            val groups = mutableListOf(BazaarSectionsGroup("The Shrine", entries, facts,
                emptyText = if (entries.isEmpty()) "没有可献祭的物品" else ""))
            if (rules.isNotEmpty()) groups += BazaarSectionsGroup("", entries = listOf(
                BazaarSectionsEntry("shrine-rules", "Rules", moreDetails = rules.map { "" to it })))
            return BazaarSectionsPage(route, sections = groups)
        }

        fun market(): BazaarSectionsPage {
            val balances = doc.select(".credit_balance").map { it.children().firstOrNull().hvText() to it.children().lastOrNull().hvText() }
            val forms = mutableListOf<BazaarSectionsForm>()
            doc.selectFirst("#market_xfer form")?.let { form ->
                form(form, "transfer", "Credits", includeDisabledSubmit = true)?.let {
                    forms += it.copy(facts = balances, validation = "transfer", balance = number(balances.getOrNull(0)?.second.orEmpty()), stock = number(balances.getOrNull(1)?.second.orEmpty()))
                }
            }
            for ((selector, title, kind) in listOf(Triple("#market_itemsell form", "Your Sell Order", "market_sell"), Triple("#market_itembuy form", "Your Buy Order", "market_buy"))) {
                doc.selectFirst(selector)?.let { node ->
                    form(node, kind, title, includeDisabledSubmit = true)?.let { order ->
                        val buying = kind == "market_buy"
                        val itemName = doc.select("#market_itemheader a").firstOrNull { it.hvText() !in setOf("◀", "▶") }.hvText()
                        forms += order.copy(validation = kind, balance = variable("market_balance"), stock = variable("player_stock"), batchSize = variable("batch_size") ?: 1,
                            actions = order.actions.map { action ->
                                val cancel = action.fields.any { it.first in setOf("buyorder_cancel", "sellorder_cancel") }
                                val label = if (cancel) "撤销${if (buying) "买单" else "卖单"}" else if (action.label == "Update") "更新挂单" else "${if (buying) "挂买单" else "挂卖单"}"
                                action.copy(label = label, confirmation = "$label${if (itemName.isBlank()) "" else "：$itemName"}")
                            },
                            facts = listOf("每批数量" to (variable("batch_size")?.toString().orEmpty()), "交易" to doc.selectFirst("#market_iteminfo").hvText()).filter { it.second.isNotBlank() },
                            presets = node.select("[onclick]").mapNotNull { control -> marketPreset(control) } +
                                if (kind == "market_sell" && node.selectFirst("#sell_order_stock_field[onclick]") != null) {
                                    variable("player_stock")?.let { stock -> listOf(BazaarSectionsPreset("全部库存", mapOf("market_sell/sellorder_batchcount" to (stock / (variable("batch_size") ?: 1).coerceAtLeast(1)).toString()))) }.orEmpty()
                                } else emptyList())
                    }
                }
            }
            doc.selectFirst("#form_market_itemfilter")?.let { node ->
                post(node, "应用筛选")?.let { action -> forms += BazaarSectionsForm("stock-filter", "库存筛选", fields(node), listOf(action)) }
            }
            val groups = mutableListOf<BazaarSectionsGroup>()
            doc.selectFirst("#market_itemlist table")?.let { groups += table(it, "Items") }
            doc.select(".market_itemorders").forEach { node -> node.selectFirst("table")?.let { groups += table(it, node.children().firstOrNull().hvText()) } }
            doc.select("table#market_price, table#market_recent, #market_price table, #market_recent table, #market_accountlog table, #market_tradelog table, #market_orders table, #market_right > table").forEachIndexed { i, node ->
                val heading = when (node.id().ifBlank { node.parent()?.id().orEmpty() }) { "market_price" -> "Price History"; "market_recent" -> "Recent Trades"; "market_accountlog" -> "Account Log"; "market_tradelog" -> "Trade Log"; else -> node.previousElementSibling().hvText().ifBlank { if (i == 0) "Market History" else "Trades" } }
                groups += table(node, heading)
            }
            if (groups.isEmpty() && forms.size <= 1) {
                val text = doc.selectFirst("#market_right")?.clone()?.apply { select("script, form, #filterbar").remove() }.hvText()
                if (text.isNotBlank()) groups += BazaarSectionsGroup("Market", emptyText = text)
            }
            val pageLinks = links("#market_right .pager a, #market_right > div > a, #market_itembody > div > div > a")
            val backToList = marketListReturnLink()
            val viewLinks = links("#market_left > a")
            val filterLinks = links("#filterbar a")
            val itemLinks = marketItemLinks()
            val navigationUrls = (viewLinks + filterLinks + itemLinks + listOfNotNull(backToList)).map { it.action.url }.toSet()
            val marketLinks = BazaarMarketLinks(
                views = viewLinks,
                filters = filterLinks,
                items = itemLinks,
                pages = pageLinks.filterNot { it.action.url in navigationUrls },
                backToList = backToList,
                currentItem = marketItemTitle(),
            )
            val allLinks = (marketLinks.views + marketLinks.filters + marketLinks.items + marketLinks.pages + listOfNotNull(marketLinks.backToList))
                .distinctBy { it.action.url }
            return BazaarSectionsPage(route, links = allLinks, sections = groups, forms = forms, marketLinks = marketLinks)
        }

        fun armory(): BazaarSectionsPage {
            if (current.queryParameter("screen") == "statfuse") return ArmoryStatFusionParser.parse(doc, world, current, sources)
                .copy(armory = armoryInfo())
            val equipform = doc.selectFirst("#equipform")
            if (doc.selectFirst("#equipmodify_outer") != null && equipform != null) return equipmentDetail(equipform)
                .copy(armory = armoryInfo())
            val script = doc.select("script").joinToString("\n") { it.data() }
            val mode = Regex("\\b(?:const|let|var)\\s+title\\s*=\\s*[\"']([^\"']+)[\"']").find(script)?.groupValues?.get(1)
                ?: current.queryParameter("screen").orEmpty()
            val eqitems = HvScriptObjectParser.parse(script, "eqitems")
            val itemdata = HvScriptObjectParser.parse(script, "itemdata")
            val costs = doc.select("#equiplist input[name='eqids[]']").associate { input ->
                val id = input.attr("value")
                val entry = eqitems?.optJSONObject(id)
                val materialCost = entry?.optJSONObject("m")
                id to BazaarSectionsEquipmentCost(
                    credits = entry?.opt("c")?.toString()?.toLongOrNull()
                        ?: if (mode in setOf("purchase", "sell")) number(input.closest("tr")?.select("td")?.lastOrNull().hvText()) else null,
                    items = materialCost?.keys()?.asSequence()?.associateWith { materialCost.opt(it)?.toString()?.toLongOrNull() ?: 0 },
                )
            }
            val materials = itemdata?.keys()?.asSequence()?.mapNotNull { id ->
                val entry = itemdata.optJSONObject(id) ?: return@mapNotNull null
                val owned = entry.opt("c")?.toString()?.toLongOrNull() ?: return@mapNotNull null
                val label = entry.optString("n")
                if (label.isBlank()) null else id to BazaarSectionsMaterial(label, owned)
            }?.toMap().orEmpty()
            val forms = equipform?.let { form(it, "equipment", doc.selectFirst("#equipsubmit")?.attr("value").orEmpty(), includeDisabledSubmit = true) }
                ?.takeIf { it.fields.any { field -> field.name == "eqids[]" } }
                ?.let { listOf(it.copy(validation = "equipment", equipmentMode = mode,
                    singleEquipment = Regex("\\bmultiselect\\s*=\\s*false").containsMatchIn(script),
                    balance = variable("credit_balance"), equipmentCosts = costs, materials = materials,
                    actions = if (mode == "soulbind") it.actions.map { action ->
                        action.copy(confirmation = "${action.confirmation.orEmpty()}\n装备将永久灵魂绑定，无法交易。")
                    } else it.actions,
                    fields = it.fields.map { field ->
                        if (field.name != "eqids[]") field else equipment(field.value)?.let { detail ->
                            field.copy(label = detail.optString("t").ifBlank { field.label },
                                details = equipmentFacts(Jsoup.parseBodyFragment(detail.optString("d"))),
                                originalEquipmentName = detail.optString("n").takeIf(String::isNotBlank))
                        } ?: field
                    })) }.orEmpty()
            val empty = doc.selectFirst("#equiplist .eqselall p").hvText()
            val facts = doc.select("#equipblurb table tr").mapNotNull { row ->
                val cells = row.select("td").map { it.hvText() }
                if (cells.size < 2) null else cells.first().removeSuffix(":") to cells.drop(1).joinToString(" ")
            }
            val groups = listOf(BazaarSectionsGroup("Equipment", facts = facts, emptyText = empty)) +
                doc.select("#eqcredits").map { BazaarSectionsGroup("Credits", emptyText = it.hvText()) }
            return BazaarSectionsPage(route, links("#armory_left > a, #filterbar a"), groups, forms, armory = armoryInfo())
        }

        private fun armoryInfo(): ArmoryPageInfo {
            val mode = when {
                current.queryParameter("screen") == "statfuse" -> ArmoryViewMode.Fusion
                doc.selectFirst("#equipmodify_outer") != null -> ArmoryViewMode.Detail
                else -> ArmoryViewMode.List
            }
            // Operation links also carry filter; their source containers define their role.
            val operations = links("#armory_left > a")
            val filters = links("#filterbar a")
            val screen = current.queryParameter("screen")
                ?: operations.firstOrNull { it.selected }?.action?.url?.queryParameter("screen").orEmpty()
            val filter = filters.firstOrNull { it.selected }?.action?.url?.queryParameter("filter")
                ?: current.queryParameter("filter").orEmpty()
            val back = when (mode) {
                ArmoryViewMode.Fusion -> doc.selectFirst("#eqback a")?.let { get(it, "返回装备改装") }
                ArmoryViewMode.Detail -> NativeAction(current.newBuilder().removeAllQueryParameters("eqids[]")
                    .removeAllQueryParameters("charmslot").build(), method = "GET", label = "返回列表")
                ArmoryViewMode.List -> null
            }
            val summary = doc.select("#equipblurb table tr").mapNotNull { row ->
                val cells = row.select("td").map { it.hvText() }
                if (cells.size < 2) null else cells.first().removeSuffix(":") to cells.drop(1).joinToString(" ")
            }
            return ArmoryPageInfo(mode, current, screen, filter, operations, filters, back, summary, variable("credit_balance"))
        }

        private fun equipmentDetail(equipform: Element): BazaarSectionsPage {
            val heading = doc.selectFirst("#equipinfo .showequip > div")
            val equipmentId = equipform.selectFirst("input[type=hidden][name='eqids[]']")?.attr("value")
                ?: current.queryParameterValues("eqids[]").singleOrNull()
            val data = equipmentId?.let(::equipment)
            // hveqc renders renamed equipment as the alias followed by (original name).
            val originalName = data?.optString("n")?.takeIf(String::isNotBlank)
                ?: heading?.children()?.getOrNull(1)?.hvText()?.takeIf { it.startsWith('(') && it.endsWith(')') }
                    ?.removeSurrounding("(", ")")?.takeIf(String::isNotBlank)
            val title = if (originalName != null) data?.optString("t")?.takeIf(String::isNotBlank)
                ?: heading?.children()?.firstOrNull().hvText().ifBlank { heading.hvText() } else heading.hvText()
            val actions = mutableListOf<NativeAction>()
            val forms = mutableListOf<BazaarSectionsForm>()
            val groups = mutableListOf<BazaarSectionsGroup>()
            val script = doc.select("script").joinToString("\n") { it.data() }
            doc.select("#equipaction button[name=action]").forEach { button ->
                if (button.hasAttr("disabled")) return@forEach
                val value = button.attr("value")
                val label = button.hvText()
                when (value) {
                    "rename" -> post(equipform, label, listOf("action" to "rename"), "重命名 $title")?.let { action ->
                        val currentName = Regex("pop_rename\\('[^']*',\\s*'([^']*)'\\)").find(button.attr("onclick"))?.groupValues?.get(1).orEmpty()
                        forms += BazaarSectionsForm("rename", label, listOf(BazaarSectionsField("eqname", "装备名称", currentName, maxLength = 50)), listOf(action))
                    }
                    "repair" -> post(equipform, label, confirmation = "$label\n$title")?.let { base ->
                        ArmoryRepairParser.parse(doc, base)?.let(forms::add)
                    }
                    "soulbind" -> armoryDialog(script, "armory_soulbind")?.let { soulbind ->
                        val confirmation = soulbind.select("p").joinToString("\n") { it.hvText() }
                        soulbind.selectFirst("button:not([disabled])[name=action][value=soulbind]")?.let {
                            post(equipform, label, listOf("action" to "soulbind"), confirmation)?.let(actions::add)
                        }
                        soulbind.selectFirst("button[disabled]")?.let {
                            groups += BazaarSectionsGroup("Soulbind", emptyText = it.attr("title"))
                        }
                    }
                    "pin", "unpin", "lock", "unlock", "protect", "unprotect", "store", "unequip" -> post(equipform, label, listOf("action" to value),
                        "$label\n$title" + if (value == "unequip") "\n会从所有人格的装备套装中卸下此装备，并可能卸下依赖它的装备。" else "")?.let(actions::add)
                }
            }
            post(equipform, "Attach Charm", listOf("action" to "setcharm"), title)?.let { action ->
                ArmoryCharmParser.parse(doc, action)?.let(forms::add)
            }
            val stats = equipmentFacts(doc.selectFirst("#eqstats"))
            groups.add(0, BazaarSectionsGroup(title, facts = stats, originalEquipmentName = originalName))
            val charms = doc.select("#charmslots .eqcharm").mapIndexed { index, node ->
                BazaarSectionsEntry("charm-$index", node.children().firstOrNull().hvText(), details = ArmoryCharmParser.slotDetails(node),
                    action = get(node, "Charm Slot ${index + 1}"), enabled = !node.hasClass("eqdisabled"))
            }
            if (charms.isNotEmpty()) groups += BazaarSectionsGroup("Charms", charms,
                facts = if (forms.any { it.armoryCharm != null }) emptyList() else listOf("" to doc.selectFirst("#cpreadout").hvText()))
            doc.selectFirst("#eqbaserolls")?.let { base ->
                base.selectFirst("table")?.let { groups += table(it, "Base Stat Rolls") }
                val disabled = armoryDisabledControls(base)
                if (disabled.isNotEmpty()) groups += BazaarSectionsGroup("Stat Fusion", disabled)
            }
            post(equipform, "Upgrade")?.let { base ->
                ArmoryUpgradeParser.parse(doc, base)?.let(groups::add)
                ArmoryUpgradeParser.itemWorldAction(doc, world, base)?.let(actions::add)
            }
            doc.select("#equipmodify_middle button[onclick]:not([disabled])").forEach { get(it, it.hvText())?.let(actions::add) }
            return BazaarSectionsPage(route, links("#armory_left > a, #filterbar a"), groups, forms, actions)
        }

        private fun armoryDialog(script: String, name: String): Document? =
            Regex("function\\s+${Regex.escape(name)}\\(\\)\\s*\\{[\\s\\S]*?innerHTML\\s*=\\s*`([^`]*?)`")
                .find(script)?.groupValues?.get(1)?.let(Jsoup::parseBodyFragment)

        private fun armoryDisabledControls(container: Element): List<BazaarSectionsEntry> =
            container.select("button[disabled]").mapIndexed { index, button ->
                BazaarSectionsEntry("${container.id()}-disabled-$index", button.hvText(),
                    details = listOfNotNull(button.attr("title").takeIf(String::isNotBlank)?.let { "" to it }), enabled = false)
            }

        fun mail(): BazaarSectionsPage {
            val groups = mutableListOf<BazaarSectionsGroup>()
            doc.selectFirst("#mmail_list")?.let { groups += table(it, "MoogleMail") }
            val forms = mutableListOf<BazaarSectionsForm>()
            val pageActions = mutableListOf<NativeAction>()
            doc.selectFirst("#mailform")?.let { mail ->
                val composing = mail.selectFirst("[onclick*=mmail_send]") != null
                val cod = variable("attach_cod") ?: 0
                val actions = mail.select("[onclick]").mapNotNull { node ->
                    when (node.attr("onclick").trim().removeSuffix(";")) {
                        "mooglemail.mmail_send()" -> post(mail, "发送", listOf("action" to "send"), "发送 MoogleMail\n邮费：${variable("send_cost") ?: 0} C\n附件：${variable("attach_count") ?: 0}\nCoD：${variable("attach_cod") ?: 0} C")
                        "mooglemail.mmail_save()" -> post(mail, "保存草稿", listOf("action" to "save"))
                        "mooglemail.mmail_discard()" -> post(mail, "丢弃", listOf("action" to "discard"), "丢弃此邮件草稿？")
                        else -> null
                    }
                }
                val attachments = mutableListOf<BazaarSectionsEntry>()
                val attachmentActions = mutableMapOf<Element, NativeAction>()
                doc.select("#mmail_outer [onclick]").forEach { node ->
                    val click = node.attr("onclick").trim().removeSuffix(";")
                    val remove = MAIL_REMOVE.matchEntire(click)
                    val action = when {
                        remove != null -> {
                            val title = node.parents().firstOrNull { it.parent()?.id() == "mmail_attachlist" }.hvText()
                            val all = remove.groupValues[1] == "0"
                            val label = if (composing) { if (all) "移除全部附件" else "移除附件" } else { if (all) "领取全部附件" else "领取附件" }
                            val actionLabel = if (!composing && cod > 0) "支付 $cod C 并领取" else label
                            post(mail, if (title.isBlank()) actionLabel else "$actionLabel：$title",
                                listOf("action" to "attach_remove", "action_value" to remove.groupValues[1]),
                                if (!composing && cod > 0) "${label}将支付 $cod C" else "$label${if (title.isBlank()) "" else "：$title"}")
                        }
                        click == "mooglemail.return_mail()" -> {
                            val recall = node.attr("src").substringAfterLast('/').startsWith("recallmail.")
                            post(mail, if (recall) "撤回邮件" else "退回邮件", listOf("action" to "return_message"),
                                if (recall) "撤回此邮件及剩余附件？" else "将此邮件及剩余附件退回寄信人？")
                        }
                        else -> null
                    }
                    if (action != null) {
                        if (remove != null) node.parents().firstOrNull { it.parent()?.id() == "mmail_attachlist" }
                            ?.let { attachmentActions[it] = action }
                        if (composing) attachments += BazaarSectionsEntry("mail-action-${attachments.size}", action.label, action = action, category = "已附加")
                        else pageActions += action
                    }
                }
                doc.selectFirst("#newcod")?.takeIf { !it.hasAttr("disabled") && doc.selectFirst("[onclick*=set_cod]") != null }?.let { input ->
                    post(mail, "设置 CoD", listOf("action" to "attach_cod"), "设置附件货到付款金额")?.let { action ->
                        attachments += BazaarSectionsEntry("mail-cod", "CoD", quantity = BazaarSectionsQuantity(action, "action_value",
                            input.attr("max").toLongOrNull() ?: input.attr("maxlength").toIntOrNull()?.takeIf { it in 1..18 }?.let { "9".repeat(it).toLong() } ?: Long.MAX_VALUE,
                            initial = input.attr("value").toLongOrNull() ?: variable("attach_cod") ?: 0, minimum = 0), category = "已附加")
                    }
                }
                for ((pane, title) in listOf("credits" to "Credits", "hath" to "Hath")) {
                    val panel = doc.selectFirst("#mmail_attach$pane") ?: continue
                    if (panel.selectFirst("[onclick*=apply_attachment]") == null) continue
                    val stock = number(panel.children().firstOrNull().hvText()) ?: 0
                    val action = post(mail, "添加 $title", listOf("action" to "attach_add", "select_item" to "0", "select_pane" to pane), "附件：$title") ?: continue
                    attachments += BazaarSectionsEntry("attach-$pane", title, listOf("持有" to stock.toString()),
                        quantity = BazaarSectionsQuantity(action, "select_count", stock), enabled = stock > 0, category = "货币")
                }
                for ((selector, pane) in listOf("#mmail_attachitem #item [onclick]" to "item", "#mmail_attachequip [onclick]" to "equip")) {
                    doc.select(selector).forEach { node ->
                        val match = MAIL_ITEM.find(node.attr("onclick")) ?: return@forEach
                        if (node.attr("data-locked") == "1") return@forEach
                        val id = match.groupValues[1]
                        val count = if (pane == "equip") 1 else number(node.closest("tr")?.select("td")?.lastOrNull().hvText()) ?: return@forEach
                        val data = if (pane == "equip") equipment(id) else null
                        val originalName = data?.optString("n")?.takeIf(String::isNotBlank)
                        val title = data?.optString("t")?.takeIf { originalName != null && it.isNotBlank() } ?: node.hvText()
                        val protected = pane == "equip" && node.attr("onclick").contains("confirm(")
                        val action = post(mail, "添加附件", listOf("action" to "attach_add", "select_item" to id, "select_pane" to pane),
                            "附件：$title" + if (protected) "\n这是受保护装备，仍要附加到邮件？" else "") ?: return@forEach
                        val moreDetails = if (pane == "equip") data?.optString("d")?.let { equipmentFacts(Jsoup.parseBodyFragment(it)) }.orEmpty()
                            else itemDescriptions.optJSONObject(id)?.optString("q")?.takeIf(String::isNotBlank)?.let { listOf("" to Jsoup.parseBodyFragment(it).hvText()) }.orEmpty()
                        attachments += BazaarSectionsEntry("attach-$pane-$id", title, listOf("持有" to count.toString()), quantity = BazaarSectionsQuantity(action, "select_count", count), enabled = count > 0,
                            moreDetails = moreDetails, category = if (pane == "equip") "装备" else "道具",
                            originalEquipmentName = originalName)
                    }
                }
                if (actions.isNotEmpty()) forms += BazaarSectionsForm("mail", "MoogleMail", fields(mail), actions, attachments = attachments, validation = "mail")
                if (!composing) {
                    val info = mail.select("input:not([type=hidden]):not([type=submit]):not([type=button]), textarea").mapNotNull { control ->
                        val value = if (control.tagName() == "textarea") control.wholeText() else control.attr("value")
                        if (value.isBlank()) null else (if (control.tagName() == "textarea") "正文" else fieldLabel(control)) to value
                    }
                    if (info.isNotEmpty()) groups += BazaarSectionsGroup("MoogleMail", facts = info)
                }
                val attached = doc.select("#mmail_attachlist > div").mapIndexed { index, node ->
                    val equipId = node.select("[onmouseover]").firstNotNullOfOrNull {
                        MAIL_EQUIPMENT.find(it.attr("onmouseover"))?.groupValues?.get(1)
                    }
                    val itemId = node.select("[id^=item_]").firstNotNullOfOrNull {
                        it.id().removePrefix("item_").takeIf { id -> id.toLongOrNull() != null }
                    }
                    val data = equipId?.let(::equipment)
                    val originalName = data?.optString("n")?.takeIf(String::isNotBlank)
                    val details = if (equipId != null) data?.optString("d")
                        ?.let { equipmentFacts(Jsoup.parseBodyFragment(it)) }.orEmpty()
                    else itemId?.let { itemDescriptions.optJSONObject(it)?.optString("q") }
                        ?.takeIf(String::isNotBlank)?.let { listOf("" to Jsoup.parseBodyFragment(it).hvText()) }.orEmpty()
                    val title = data?.optString("t")?.takeIf { originalName != null && it.isNotBlank() } ?: node.hvText()
                    BazaarSectionsEntry("attached-$index", title, action = attachmentActions[node], moreDetails = details,
                        category = if (equipId != null) "装备" else "", originalEquipmentName = originalName)
                }
                val attachmentFacts = listOf("#mmail_attachcount", "#mmail_currentcod")
                    .map { "" to doc.selectFirst(it).hvText() }.filter { it.second.isNotBlank() }
                if (attached.isNotEmpty()) groups += BazaarSectionsGroup("附件", attached,
                    facts = attachmentFacts)
            }
            return BazaarSectionsPage(route, links("#filterbar a, #mmail_pager a, #mmail_showbuttons [onclick]"), groups, forms, pageActions.distinct(),
                mail = mailInfo())
        }

        private fun mailInfo(): MailPageInfo {
            val form = doc.selectFirst("#mailform")
            val mode = when {
                form?.selectFirst("[onclick*=mmail_send]") != null -> MailViewMode.Compose
                form != null -> MailViewMode.Detail
                else -> MailViewMode.List
            }
            val folders = links("#filterbar a")
            val folder = current.queryParameter("filter")
                ?: folders.firstOrNull { it.selected }?.action?.url?.queryParameter("filter") ?: "inbox"
            val composeAction = folders.firstOrNull { it.action.url.queryParameter("filter") == "new" }?.action
            val listAction = if (mode == MailViewMode.Compose) {
                folders.firstOrNull { it.action.url.queryParameter("filter") == "inbox" }?.action
            } else NativeAction(current.newBuilder().removeAllQueryParameters("mid").removeAllQueryParameters("reply")
                .setQueryParameter("filter", folder).build(), method = "GET", label = "返回列表")
            val pager = doc.select("#mmail_pager > div")
            val rows = doc.select("#mmail_list tr").mapNotNull { row ->
                val cells = row.children().filter { it.tagName() == "td" }
                if (cells.size != 4 || cells.any { it.hasAttr("colspan") }) return@mapNotNull null
                val action = get(row) ?: return@mapNotNull null
                val id = action.url.queryParameter("mid") ?: return@mapNotNull null
                val values = cells.map { it.hvText() }
                MailListRow(id, values[0], values[1], values[2], values[3],
                    values[3].equals("Never", ignoreCase = true), action)
            }
            val detail = form?.let { mail ->
                val values = mail.select("#mmail_left tr input:not([type=hidden])").associate { control ->
                    val label = fieldLabel(control).trim().removeSuffix(":").lowercase()
                    val key = when (label) {
                        "to", "收件人" -> "to"
                        "from", "寄信人", "发件人" -> "from"
                        "subject", "主题" -> "subject"
                        else -> label
                    }
                    key to control.attr("value")
                }
                MailMessageDetail(values["to"].orEmpty(), values["from"].orEmpty(), values["subject"].orEmpty(),
                    mail.selectFirst("textarea")?.wholeText().orEmpty())
            }
            val counts = Regex("([\\d,]+)\\s*/\\s*([\\d,]+)").find(doc.selectFirst("#mmail_attachcount").hvText())
            return MailPageInfo(mode, folder, folders,
                previousPage = pager.getOrNull(0)?.selectFirst("a[href]")?.let { get(it) },
                nextPage = pager.getOrNull(1)?.selectFirst("a[href]")?.let { get(it) },
                listAction = listAction, composeAction = composeAction,
                replyAction = links("#mmail_showbuttons [onclick]").firstOrNull { it.action.url.queryParameter("reply") != null }?.action,
                rows = rows, detail = detail, postage = variable("send_cost"),
                attachmentCount = variable("attach_count") ?: counts?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull(),
                attachmentLimit = counts?.groupValues?.get(2)?.replace(",", "")?.toLongOrNull(), cod = variable("attach_cod"))
        }

        fun lottery(): BazaarSectionsPage {
            val form = doc.selectFirst("#lotteryform")
            val text = doc.selectFirst("#rightpane").hvText()
            val balance = Regex("currently have ([\\d,]+) GP").find(text)?.groupValues?.get(1)?.let(::number)
            val price = Regex("ticket costs ([\\d,]+) GP").find(text)?.groupValues?.get(1)?.let(::number)
            val tickets = Regex("You hold ([\\d,]+) of ([\\d,]+) sold tickets").find(text)
            val drawingTitle = doc.selectFirst("#lottery_eqname")?.previousElementSibling()?.hvText()
                ?.takeIf { it.startsWith("Grand Prize for ") }
            val goldenStock = doc.select("#rightpane > div").firstNotNullOfOrNull {
                Regex("Stock: ([\\d,]+)").matchEntire(it.hvText())?.groupValues?.get(1)
            }
            val paragraphs = doc.select("#rightpane p").map { it.hvText() }.filter(String::isNotBlank)
            val drawingStatus = paragraphs.firstOrNull {
                it.startsWith("Today's drawing") || it == "Today's ticket sale is closed."
            }
            val facts = listOfNotNull(drawingTitle?.let { "" to it }, balance?.let { "GP" to it.toString() },
                price?.let { "每张" to "$it GP" }, tickets?.let { "持有 / 已售" to "${it.groupValues[1]} / ${it.groupValues[2]}" },
                goldenStock?.let { "Golden Lottery Tickets" to it },
                drawingStatus?.let { "" to it })
            val entries = mutableListOf<BazaarSectionsEntry>()
            if (doc.selectFirst("[onclick=submit_buy()]") != null && balance != null && price != null && price > 0) {
                post(form, "购买彩票", confirmation = route.label)?.let { action -> entries += BazaarSectionsEntry("tickets", "购买彩票", quantity = BazaarSectionsQuantity(action, "ticket_count", balance / price, price, "GP"), enabled = balance >= price) }
            }
            val actions = mutableListOf<NativeAction>()
            if (doc.selectFirst("[onclick=submit_glt()]") != null) post(form, "使用 Golden Lottery Ticket", listOf("golden_tickets" to "1"), "使用 1 张 Golden Lottery Ticket？")?.let(actions::add)
            val optOut = doc.selectFirst("#leftpane img[src*=lottery_donotwant]")
            if (doc.selectFirst("[onclick=submit_dnw()]") != null) post(form, "放弃头奖", listOf("prize_optout" to "1"), "放弃本期头奖装备？此操作不可撤销。")?.let(actions::add)
            val optOutStatus = optOut?.takeUnless { it.hasAttr("onclick") }?.attr("title")?.takeIf(String::isNotBlank)
            val prize = doc.selectFirst("#lottery_eqname").hvText()
            val equipmentFacts = doc.select("#lottery_eqstat .eq > div").flatMap { node ->
                if (node.hasClass("ex") || node.hasClass("ep")) node.children().map { "" to it.hvText() }
                else listOf("" to node.hvText())
            }.filter { it.second.isNotBlank() }
            val prizeLines = doc.select("#leftpane div").filter { it.children().isEmpty() }
            val grandWinners = prizeLines.mapNotNull { node ->
                val line = node.hvText()
                val label = line.substringBefore(":")
                if (label in setOf("Equip Winner", "Core Winner")) label to line.substringAfter(":").trim() else null
            }
            val runners = prizeLines.filter { Regex("^[2-5](nd|rd|th) Prize:").containsMatchIn(it.hvText()) }
                .map { node ->
                    val line = node.hvText()
                    val winner = node.nextElementSibling()?.hvText()?.takeIf { it.startsWith("Winner:") }
                        ?.substringAfter(":")?.trim()
                    BazaarSectionsEntry("lottery-result-${line.first()}", line.substringBefore(":"),
                        details = listOfNotNull("" to line.substringAfter(":").trim(), winner?.let { "Winner" to it }))
                }
            val groups = mutableListOf(BazaarSectionsGroup(route.label, entries, facts))
            if (prize.isNotBlank()) groups += BazaarSectionsGroup("", entries = listOf(
                BazaarSectionsEntry("lottery-prize", prize,
                    details = grandWinners + listOfNotNull(optOutStatus?.let { "" to it }), moreDetails = equipmentFacts)))
            if (runners.isNotEmpty()) groups += BazaarSectionsGroup("", entries = runners)
            val rules = paragraphs.filterNot { it == drawingStatus }
            if (rules.isNotEmpty()) groups += BazaarSectionsGroup("", entries = listOf(
                BazaarSectionsEntry("lottery-rules", "Rules", moreDetails = rules.map { "" to it })))
            val today = doc.selectFirst("#mainpane img[src*=lottery_today][onclick]")?.let { get(it, "Current Drawing") }
            val navigation = links("#mainpane img[onclick]").map { link ->
                // The next period and today's drawing can share a URL; keep one explicit current-period entry.
                if (today != null && link.action.url == today.url) link.copy(label = today.label, action = today) else link
            }
            return BazaarSectionsPage(route, navigation, groups, actions = actions)
        }

        private fun table(node: Element, title: String): BazaarSectionsGroup {
            val headers = node.select("tr").firstOrNull { it.select("th").isNotEmpty() }?.select("th")?.map { it.hvText() }.orEmpty()
            val rows = node.select("tr").mapIndexedNotNull { index, row ->
                val cells = row.children().filter { it.tagName() == "td" }
                if (cells.isEmpty() || cells.any { it.hasAttr("colspan") } || cells.any { it.selectFirst("input") != null }) return@mapIndexedNotNull null
                val values = cells.map { it.hvText() }
                val action = get(row, values.firstOrNull().orEmpty()) ?: row.selectFirst("a[href]")?.let { get(it, values.firstOrNull().orEmpty()) }
                BazaarSectionsEntry("${node.id()}-$title-$index", values.firstOrNull().orEmpty(),
                    values.drop(1).mapIndexed { col, value -> headers.getOrNull(col + 1).orEmpty() to value }.filter { it.second.isNotBlank() }, action,
                    prefill = marketPreset(row))
            }
            val emptyText = if (rows.isEmpty()) node.select("td[colspan]").joinToString("\n") { it.hvText() } else ""
            return BazaarSectionsGroup(title, rows, emptyText = emptyText)
        }

        private fun marketPreset(node: Element): BazaarSectionsPreset? {
            val match = MARKET_AUTOFILL.matchEntire(node.attr("onclick").trim().removeSuffix(";")) ?: return null
            val source = match.groupValues[1]
            val ownPrice = match.groupValues[2]
            val otherPrice = match.groupValues[3].toLongOrNull() ?: return null
            val count = match.groupValues[4].toLongOrNull() ?: return null
            val target = if (source == "sell") "buy" else "sell"
            val entered = mutableMapOf("market_$source/${source}order_batchprice" to ownPrice)
            val minimum = mutableMapOf<String, Long>()
            if (otherPrice > 0) {
                entered["market_$target/${target}order_batchprice"] = otherPrice.toString()
                minimum["market_$target/${target}order_batchcount"] = count
            }
            val label = node.hvText().takeIf { node.tagName() != "tr" }.orEmpty().ifBlank { "填入订单" }
            return BazaarSectionsPreset(label, entered, minimum)
        }

        private fun form(node: Element, key: String, title: String, includeDisabledSubmit: Boolean = false): BazaarSectionsForm? {
            val actions = node.select("input[type=submit], button[type=submit]").filter { includeDisabledSubmit || !it.hasAttr("disabled") }.mapNotNull { button ->
                val label = (if (button.tagName() == "button") button.hvText() else button.attr("value")).trim(' ', '▲', '▼', '\u00a0')
                if (label.isBlank()) return@mapNotNull null
                val submit = if (button.hasAttr("name")) listOf(button.attr("name") to button.attr("value")) else emptyList()
                val mutation = node.attr("method").equals("post", true)
                post(node, label, submit, if (mutation) label else null)
            }
            if (actions.isEmpty()) return null
            return BazaarSectionsForm(key, title, fields(node), actions)
        }

        private fun fields(form: Element): List<BazaarSectionsField> {
            val controls = form.select("input[name], select[name], textarea[name]").filterNot {
                it.attr("type") in setOf("hidden", "submit", "button", "image") ||
                    it.hasAttr("disabled") && !(it.attr("name") == "eqids[]" && it.closest("tr")?.attr("onclick")?.contains("confirm_unprotect(") == true)
            }
            val result = mutableListOf<BazaarSectionsField>()
            val radios = mutableSetOf<String>()
            for (node in controls) {
                val name = node.attr("name")
                val type = if (node.tagName() == "textarea") "textarea" else if (node.tagName() == "select") "select" else node.attr("type").ifBlank { "text" }
                if (type == "radio") {
                    if (!radios.add(name)) continue
                    val group = controls.filter { it.attr("name") == name }
                    val options = group.map { it.attr("value") to radioLabel(it, name) }
                    result += BazaarSectionsField(name, fieldLabel(node), group.firstOrNull { it.hasAttr("checked") }?.attr("value").orEmpty(), "select", options)
                } else {
                    val options = if (type == "select") node.select("option").map { it.attr("value") to it.hvText() } else emptyList()
                    val value = when (type) {
                        "textarea" -> node.wholeText()
                        "select" -> node.selectFirst("option[selected]")?.attr("value") ?: options.firstOrNull()?.first.orEmpty()
                        "checkbox" -> node.attr("value").ifBlank { "on" }
                        else -> node.attr("value")
                    }
                    val numeric = type == "number" || name in NUMERIC_FIELDS
                    result += BazaarSectionsField(name, fieldLabel(node), value, if (numeric) "number" else type, options,
                        node.hasAttr("required"), node.attr("maxlength").toIntOrNull(), node.hasAttr("checked"),
                        if (numeric) node.attr("min").toLongOrNull() ?: 0 else null, node.attr("max").toLongOrNull(),
                        if (type == "checkbox") "$name:$value" else name,
                        selectionConfirmation = if (node.hasAttr("disabled") && name == "eqids[]") "将此受保护装备加入本次选择？\n${fieldLabel(node)}" else null)
                }
            }
            return result
        }

        private fun fieldLabel(node: Element): String {
            val name = node.attr("name")
            FIELD_LABELS[name]?.let { return it }
            if (name == "eqids[]") return node.closest("tr").hvText()
            val id = node.id()
            if (id.isNotBlank()) doc.select("label[for]").firstOrNull { it.attr("for") == id }?.hvText()?.takeIf(String::isNotBlank)?.let { return it }
            node.closest("tr")?.children()?.firstOrNull()?.hvText()?.trim()?.removeSuffix(":")?.takeIf(String::isNotBlank)?.let {
                return when (it) { "To" -> "收件人"; "From" -> "寄信人"; "Subject" -> "主题"; else -> it }
            }
            return node.parent().hvText().takeIf(String::isNotBlank) ?: "选项"
        }
        private fun radioLabel(node: Element, name: String): String {
            val value = node.attr("value")
            if (name in setOf("set_pinned", "set_stored", "set_locked")) return when (value) {
                "-1" -> "Clear"; "0" -> "Unchanged"; "1" -> if (name == "set_locked") "Protect" else "Enable"; "2" -> "Lock"; else -> value
            }
            return node.parent().hvText().ifBlank { value }
        }
    }

    private fun number(value: String): Long? = Regex("[\\d,]+").find(value)?.value?.replace(",", "")?.toLongOrNull()
    private fun equipmentFacts(node: Element?): List<Pair<String, String>> = node?.select(".eq > div")?.flatMap { row ->
        if (row.hasClass("ex") || row.hasClass("ep")) row.children().map { "" to it.hvText() } else listOf("" to row.hvText())
    }?.filter { it.second.isNotBlank() }.orEmpty()
    private fun imageLabel(node: Element): String = node.attr("alt").ifBlank {
        when {
            node.attr("src").contains("prev") -> "上一期"
            node.attr("src").contains("next") -> "下一期"
            node.attr("src").contains("lottery_today") -> "Current Drawing"
            node.attr("src").contains("reply.png") -> "回复"
            else -> ""
        }
    }
    private val LOCATION = Regex("document\\.location(?:\\.href)?\\s*=\\s*['\"]([^'\"]+)['\"]")
    private val SHOP = Regex("itemshop\\.set_item\\('([^']+)',\\s*(\\d+),\\s*(\\d+),\\s*(\\d+)")
    private val SHRINE = Regex("snowflake\\.set_shrine_item\\(\\s*(\\d+),\\s*(\\d+),\\s*(\\d+)")
    private val REWARD = Regex("snowflake\\.submit_shrine_reward\\('([^']*)','([^']*)'\\)")
    private val MAIL_ITEM = Regex("mooglemail\\.set_mooglemail_item\\(\\s*(\\d+)")
    private val MAIL_REMOVE = Regex("mooglemail\\.remove_attachment\\(\\s*(\\d+)\\s*\\)")
    private val MAIL_EQUIPMENT = Regex("equips\\.set\\(\\s*(\\d+)")
    private val MARKET_AUTOFILL = Regex("autofill_from_(sell|buy)_order\\(\\s*(\\d+),\\s*(\\d+),\\s*(\\d+)\\s*\\)")
    private val NUMERIC_FIELDS = setOf("account_amount", "buyorder_batchcount", "buyorder_batchprice", "sellorder_batchcount", "sellorder_batchprice")
    private val FIELD_LABELS = mapOf("account_amount" to "金额", "buyorder_batchcount" to "批数", "sellorder_batchcount" to "批数",
        "buyorder_batchprice" to "每批价格 (C)", "sellorder_batchprice" to "每批价格 (C)", "playerstock" to "仅显示持有物品", "marketstock" to "仅显示有货物品",
        "set_pinned" to "Pin", "set_stored" to "Store", "set_locked" to "Protection", "replace_charms" to "Replace Charms",
        "message_to_name" to "收件人", "message_from_name" to "寄信人", "message_subject" to "主题", "message_body" to "正文")
}
