package com.mitahv.mobile.data

import com.mitahv.mobile.model.*
import okhttp3.HttpUrl
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal object CharacterSectionsParser {
    fun parse(html: String, route: HvRoute, world: HvWorld, currentUrl: HttpUrl = route.url(world),
              supplementalScripts: List<String> = emptyList()): CharacterSectionsPage =
        parse(Jsoup.parse(html, currentUrl.toString()), route, world, currentUrl, supplementalScripts)

    fun parse(doc: Document, route: HvRoute, world: HvWorld, currentUrl: HttpUrl = route.url(world),
        supplementalScripts: List<String> = emptyList()): CharacterSectionsPage {
        require(route.isAvailableIn(world))
        require(world.containsUrl(currentUrl))
        val scripts = (doc.select("script").map { it.data() } + supplementalScripts).joinToString("\n")
        val page = when (route) {
            HvRoute.Equipment -> equipment(doc, currentUrl, HvScriptObjectParser.parse(scripts, "dynjs_equip"))
            HvRoute.Training -> training(doc, currentUrl)
            HvRoute.ItemInventory -> inventory(doc, currentUrl, HvScriptObjectParser.parse(scripts, "dynjs_itemc"))
            HvRoute.Settings -> settings(doc, currentUrl)
            HvRoute.Abilities -> abilities(doc, currentUrl)
            else -> throw IllegalArgumentException("Not a character subsection")
        }
        val notices = doc.select("#messagebox, #message, .messagebox, .hv_notice, #mainpane .error")
            .map { it.hvText() }.filter { it.isNotBlank() }.distinct()
        return page.copy(notices = notices + page.notices)
    }

    private fun equipment(doc: Document, url: HttpUrl, equipment: JSONObject?): CharacterSectionsPage {
        require(doc.selectFirst("#eqch_outer, #equipselect_outer") != null)
        val groups = mutableListOf<CharacterSectionGroup>()
        val forms = mutableListOf<CharacterSectionForm>()
        val links = mutableListOf<CharacterSectionLink>()
        val currentEquipmentId = doc.select("script").firstNotNullOfOrNull {
            Regex("\\bshowequipped_eqid\\s*=\\s*(\\d+)").find(it.data())?.groupValues?.get(1)
        }
        val setForm = doc.selectFirst("#setform")
        doc.select("#eqsl img").forEach { image ->
            val set = Regex("set(\\d+)_").find(image.attr("src"))?.groupValues?.get(1) ?: return@forEach
            val active = image.attr("src").contains("_on.")
            val action = if (image.attr("onclick").contains("change_set(") && setForm != null)
                formAction(setForm, url, "Set $set", "equip_set" to set) else null
            links += CharacterSectionLink("Set $set", action, active)
        }
        val slotRows = doc.select("#eqsb .eqb, #equipselect_left > .eqb").map { slot ->
            val title = slot.children().firstOrNull().hvText()
            val detail = slot.children().getOrNull(1).hvText()
            val equipmentId = slot.select("[onmouseover]").firstNotNullOfOrNull {
                Regex("equips\\.set\\(\\s*(\\d+)").find(it.attr("onmouseover"))?.groupValues?.get(1)
            } ?: currentEquipmentId.takeIf { slot.parent()?.id() == "equipselect_left" }
            val data = equipmentId?.let { equipment?.optJSONObject(it) }
            val originalName = data?.optString("n")?.takeIf(String::isNotBlank)
            val target = location(slot.attr("onclick"), url)
            val comparison = if (slot.parent()?.id() == "equipselect_left") doc.selectFirst("#equipcompare")?.html().orEmpty() else ""
            CharacterSectionRow(title, if (originalName != null) data?.optString("t")?.takeIf(String::isNotBlank) ?: detail else detail,
                actions = listOfNotNull(target?.let {
                NativeAction(it, "GET", label = if (doc.selectFirst("#equipselect_outer") != null) "返回装备" else "选择装备")
            }), expandedDetail = equipmentDetails(comparison.ifBlank {
                data?.optString("d").orEmpty()
            }), originalEquipmentName = originalName)
        }
        if (slotRows.isNotEmpty()) groups += CharacterSectionGroup("Equipment Slots", slotRows)
        doc.select("#stats_scrollable > table").forEach { table ->
            groups += CharacterSectionGroup(table.previousElementSibling().hvText(), table.select("tr").mapNotNull { row ->
                val cells = row.select("td")
                if (cells.size < 2) null else CharacterSectionRow(cells[1].hvText(), value = cells[0].hvText())
            })
        }
        doc.selectFirst("#equipform")?.let { form ->
            val controls = successfulControls(form)
            val inputs = form.select("#equiplist input[name='eqids[]']")
            val options = inputs.map { input ->
                val row = input.closest("tr")
                val group = generateSequence(row?.previousElementSibling()) { it.previousElementSibling() }
                    .firstOrNull { it.hasClass("eqtplabel") }.hvText()
                val data = equipment?.optJSONObject(input.attr("value"))
                CharacterFieldOption(input.attr("value"), row.hvText().ifBlank { "Equipment ${input.attr("value")}" }, !input.hasAttr("disabled"),
                    detail = equipmentDetails(data?.optString("d").orEmpty()), group = group,
                    originalEquipmentName = data?.optString("n")?.takeIf(String::isNotBlank))
            }
            if (options.any { it.enabled } && form.selectFirst("button[name=action][value=equip]") != null) {
                val selected = inputs.singleOrNull { it.hasAttr("checked") && !it.hasAttr("disabled") }?.attr("value").orEmpty()
                val choices = if (selected.isBlank()) listOf(CharacterFieldOption("", "选择装备", enabled = false)) + options else options
                forms += CharacterSectionForm("Equip Selected", formAction(form, url, "装备").copy(fields = controls + ("action" to "equip")),
                    listOf(CharacterSectionField("eqids[]", "Equipment", selected, CharacterFieldKind.Choice, choices)))
            }
            form.selectFirst("button[name=action][value=unequip]:not([disabled])")?.let {
                groups += CharacterSectionGroup("", listOf(CharacterSectionRow("Unequip Current", actions = listOf(
                    formAction(form, url, "卸下装备", "action" to "unequip")))))
            }
        }
        val empty = doc.select("#equiplist p").map { it.hvText() }.filter { it.isNotBlank() }
        return CharacterSectionsPage(HvRoute.Equipment, navigation = links, groups = groups, forms = forms, notices = empty,
            equipmentSelection = doc.selectFirst("#equipselect_outer") != null)
    }

    private fun training(doc: Document, url: HttpUrl): CharacterSectionsPage {
        val form = requireNotNull(doc.selectFirst("#trainform"))
        val progressPane = doc.selectFirst("#train_progress")
        val rows = doc.select("#train_table tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 7) return@mapNotNull null
            val name = cells[0].hvText()
            val cost = cells[2].hvText()
            val duration = cells[3].hvText()
            val id = row.select("[onclick]:not([disabled])").filterNot { it.attr("src").substringBefore('?').endsWith("_d.png") }
                .firstNotNullOfOrNull { callNumber(it.attr("onclick"), "training.start_training") }
            val detail = listOf(cells[1].hvText()) + if (cost == "-" && duration == "-") emptyList() else listOf("$cost C · $duration")
            CharacterSectionRow(name, detail.joinToString("\n"),
                "${cells[4].hvText()}/${cells[6].hvText()}",
                listOfNotNull(id?.let { formAction(form, url, "训练", "start_train" to it).copy(
                    confirmation = "$name\n$cost C · $duration") }),
                training = CharacterTrainingInfo(
                    effect = cells[1].hvText(),
                    cost = cost.takeUnless { it.isBlank() || it == "-" },
                    duration = duration.takeUnless { it.isBlank() || it == "-" },
                    currentLevel = cells[4].hvText().toIntOrNull(),
                    levelCap = cells[6].hvText().toIntOrNull(),
                ))
        }
        val progress = progressPane?.let { pane ->
            val scripts = doc.select("script").joinToString("\n") { it.data() }
            fun seconds(name: String) = Regex("\\b${Regex.escape(name)}\\s*=\\s*(\\d+)\\s*;")
                .find(scripts)?.groupValues?.get(1)?.toLongOrNull()
            val total = seconds("total_time")?.takeIf { it > 0 }
            val start = seconds("start_time")
            val end = seconds("end_time")
            val fromServer = if (total != null && start != null && end != null)
                100.0 - 100.0 * (end - start).toDouble() / total else 0.0
            val fromMarkup = pane.selectFirst("#train_progcnt").hvText().removeSuffix("%").toDoubleOrNull() ?: 0.0
            val name = pane.selectFirst("strong").hvText().ifBlank { "当前训练" }
            val cancel = pane.select("[onclick]:not([disabled])")
                .firstOrNull { it.attr("onclick").contains("training.cancel_training(") && !it.attr("src").substringBefore('?').endsWith("_d.png") }
                ?.let { formAction(form, url, "取消训练", "cancel_train" to "1").copy(confirmation = "取消 $name？") }
            val reload = Regex("\\breload_to\\s*=\\s*['\"]([^'\"]+)['\"]").find(scripts)?.groupValues?.get(1)
                ?.let(url::resolve)?.takeIf { it.scheme == url.scheme && it.host == url.host && it.encodedPath == url.encodedPath }
            CharacterTrainingProgress(name, maxOf(fromServer, fromMarkup).coerceIn(0.0, 100.0), total, end,
                reload?.let { NativeAction(it, "GET", label = "刷新训练") }, cancel)
        }
        return CharacterSectionsPage(HvRoute.Training, summary = listOf(doc.selectFirst("#networth").hvText()).filter { it.isNotBlank() },
            groups = listOf(CharacterSectionGroup("", rows)), trainingProgress = progress)
    }

    private fun inventory(doc: Document, url: HttpUrl, itemData: JSONObject?): CharacterSectionsPage {
        require(doc.selectFirst("#item_outer") != null)
        val form = doc.selectFirst("#selectionform")
        val itemOptions = mutableListOf<CharacterFieldOption>()
        val rows = doc.select("#inv_item tr").mapNotNull { row ->
            val cells = row.select("td")
            val item = cells.firstOrNull()?.selectFirst("[id^=item_]") ?: return@mapNotNull null
            val id = item.id().removePrefix("item_")
            val name = item.hvText()
            val count = cells.getOrNull(1).hvText()
            val equipable = callNumber(item.attr("onclick"), "itemselector.set_item") == id
            val rareTooltip = jsStrings(item.attr("onmouseover"), "common.show_itemr_box")
            val tooltip = itemData?.optJSONObject(id)?.optString("q").orEmpty().ifBlank {
                rareTooltip.getOrNull(3).orEmpty()
            }.let(::plain)
            if (equipable) itemOptions += CharacterFieldOption(id, "$name · $count", detail = tooltip)
            CharacterSectionRow(name, value = count, expandedDetail = tooltip,
                actions = if (equipable && form != null) listOf(formAction(form, url, "装入", "slot" to "0", "item" to id)) else emptyList(),
                inventory = CharacterInventoryItemInfo(id,
                    if (item.attr("onmouseover").contains("common.show_itemc_box(")) "Consumable"
                    else plain(rareTooltip.getOrNull(4).orEmpty()), inventoryItemKind(id)))
        }
        val forms = if (form == null) emptyList() else doc.select("#item_slots [onclick]").mapNotNull { target ->
            val id = callNumber(target.attr("onclick"), "itemselector.commit_slot") ?: return@mapNotNull null
            val row = target.parent() ?: return@mapNotNull null
            val label = row.children().firstOrNull().hvText()
            val equipped = target.selectFirst("[id^=item_]")?.id()?.removePrefix("item_") ?: "0"
            val kind = when (id.toIntOrNull()) {
                in 101..199 -> CharacterInventoryKind.Scroll
                in 201..299 -> CharacterInventoryKind.Infusion
                else -> CharacterInventoryKind.General
            }
            val compatibleItems = itemOptions.filter {
                kind == CharacterInventoryKind.General || inventoryItemKind(it.value) == kind
            }
            val missingCurrent = if (equipped != "0" && itemOptions.none { it.value == equipped })
                listOf(CharacterFieldOption(equipped, "${target.hvText()} · 0",
                    detail = plain(itemData?.optJSONObject(equipped)?.optString("q").orEmpty()))) else emptyList()
            val options = listOf(CharacterFieldOption("0", "(Empty)")) + compatibleItems + missingCurrent
            CharacterSectionForm("Slot $label", formAction(form, url, "保存", "slot" to id),
                listOf(CharacterSectionField("item", "Battle Item", equipped, CharacterFieldKind.Choice, options)),
                inventorySlot = CharacterInventorySlotInfo(id, label, kind))
        }
        return CharacterSectionsPage(HvRoute.ItemInventory,
            groups = listOf(CharacterSectionGroup("Item Inventory", rows)), forms = forms)
    }

    private fun inventoryItemKind(id: String): CharacterInventoryKind = when (id.toIntOrNull()) {
        // Saved itemc.js uses 12xxx for infusions and 13xxx for scrolls. EHWiki's Character Menu
        // specifies that dedicated N/S slots accept only those types; regular slots accept either.
        in 12000..12999 -> CharacterInventoryKind.Infusion
        in 13000..13999 -> CharacterInventoryKind.Scroll
        else -> CharacterInventoryKind.General
    }

    private fun settings(doc: Document, url: HttpUrl): CharacterSectionsPage {
        val form = requireNotNull(doc.selectFirst("#settings_outer form"))
        val submit = form.selectFirst("input[type=submit][name]")
        val fields = successfulControls(form) + listOfNotNull(submit?.let { it.attr("name") to it.attr("value") })
        val action = formAction(form, url, "保存设置").copy(fields = fields)
        val forms = form.select(".settings_block").mapNotNull { block ->
            val rawTitle = block.children().firstOrNull().hvText()
            // The original bitmap font decodes headings as uppercase; custom fonts preserve case.
            val title = listOf("Challenge Level", "Display Title", "Font Engine", "Equipment Sets",
                "Vital Bar Style", "Shrine Trophy Upgrades", "Quickbar Slots", "Auto-Cast Slots",
                "Auto-Sell / Auto-Salvage").firstOrNull { it.equals(rawTitle, ignoreCase = true) } ?: rawTitle
            val description = block.children().filter {
                it.tagName() == "p" && it.id() != "settings_upkeep" && it.select("input, select, textarea").isEmpty()
            }.map { it.hvText() }.filter(String::isNotBlank).joinToString("\n\n")
            val controls = mutableListOf<CharacterSectionField>()
            val radioGroups = block.select("input[type=radio][name]").groupBy { it.attr("name") }
            for ((name, radios) in radioGroups) {
                val options = radios.map { input ->
                    val row = input.closest("tr")
                    val label = if (row != null) row.select("td").take(2).joinToString(" · ") { it.hvText() } else input.closest("label").hvText()
                    CharacterFieldOption(input.attr("value"), label, !input.hasAttr("disabled"))
                }
                val selected = radios.firstOrNull { it.hasAttr("checked") }?.attr("value") ?: options.firstOrNull()?.value.orEmpty()
                controls += CharacterSectionField(name, title, selected, CharacterFieldKind.Choice, options,
                    enabled = options.any { it.enabled })
            }
            block.select("input[type=checkbox][name], input[type=text][name], select[name]").forEach { input ->
                val name = input.attr("name")
                val rowLabel = input.closest("tr")?.children()?.firstOrNull()?.hvText().orEmpty()
                val label = input.closest("label").hvText().ifBlank { rowLabel }.ifBlank { name }
                val enabled = !input.hasAttr("disabled")
                when {
                    input.tagName() == "select" -> {
                        val options = input.select("option").map { CharacterFieldOption(it.attr("value"), it.hvText(), !it.hasAttr("disabled")) }
                        val selected = input.selectFirst("option[selected]") ?: input.selectFirst("option")
                        val distinctLabel = if (name.startsWith("as_c_")) "$label · Auto-Sell" else if (name.startsWith("as_s_")) "$label · Auto-Salvage" else label
                        controls += CharacterSectionField(name, distinctLabel, selected?.attr("value").orEmpty(), CharacterFieldKind.Choice, options, enabled)
                    }
                    input.attr("type") == "checkbox" -> controls += CharacterSectionField(name, label,
                        input.attr("value").ifBlank { "on" }, CharacterFieldKind.Toggle, enabled = enabled, checked = input.hasAttr("checked"))
                    else -> {
                        val hint = input.closest("tr")?.children()?.drop(1)?.lastOrNull()?.takeIf {
                            it.select("input, select, textarea").isEmpty()
                        }.hvText()
                        controls += CharacterSectionField(name, label, input.attr("value"), CharacterFieldKind.Text,
                            enabled = enabled, maxLength = input.attr("maxlength").toIntOrNull(), hint = hint)
                    }
                }
            }
            if (controls.isEmpty()) null else CharacterSectionForm(title, action, controls, description = description)
        }
        return CharacterSectionsPage(HvRoute.Settings, forms = forms,
            summary = listOf(doc.selectFirst("#settings_upkeep").hvText()).filter { it.isNotBlank() })
    }

    private fun abilities(doc: Document, url: HttpUrl): CharacterSectionsPage {
        require(doc.selectFirst("#ability_outer") != null)
        val groups = mutableListOf<CharacterSectionGroup>()
        val summary = doc.select("#ability_top > div").filter { !it.hasClass("ability_slotbar") && it.selectFirst("img") == null }
            .map { it.hvText() }.filter { it.isNotBlank() }
        val currentLevel = ABILITY_PLAYER_LEVEL.find(doc.selectFirst("#level_readout").hvText())
            ?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
        val availableAbilityPoints = summary.firstNotNullOfOrNull { text ->
            ABILITY_POINTS.matchEntire(text)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
        }
        val links = doc.select("#ability_treelist img").mapNotNull { image ->
            val tree = Regex("/t[ad]([a-z0-9]+)\\.png").find(image.attr("src"))?.groupValues?.get(1) ?: return@mapNotNull null
            val target = location(image.attr("onclick"), url)
            val title = TREE_NAMES[tree] ?: tree.replaceFirstChar { it.uppercase() }
            CharacterSectionLink(title, target?.let { NativeAction(it, "GET", label = title) }, target == null,
                abilityTreeId = tree, abilityFamilies = TREE_FAMILIES[tree].orEmpty())
        }
        fun action(formId: String, label: String, vararg fields: Pair<String, String>): NativeAction? =
            doc.selectFirst("#$formId")?.let { formAction(it, url, label, *fields) }
        val abilitySlots = doc.select("#ability_top [id^=slot_]").map { slot ->
            slot to CharacterAbilitySlotInfo(
                id = slot.id().removePrefix("slot_"),
                family = abilityFamily(slot),
                unlocked = slot.parent()?.hasAttr("ondrop") == true,
                equippedName = jsStrings(slot.attr("onmouseover")).firstOrNull().orEmpty(),
            )
        }
        val abilityOptions = mutableMapOf<String, MutableList<CharacterFieldOption>>()
        val rows = doc.select("#ability_treepane > div").mapNotNull { row ->
            val slot = row.selectFirst("[id^=slot_]") ?: return@mapNotNull null
            val id = slot.id().removePrefix("slot_")
            val hover = jsStrings(slot.attr("onmouseover"))
            val name = hover.getOrNull(0) ?: row.children().firstOrNull().hvText()
            val actions = mutableListOf<NativeAction>()
            row.select("[onclick]:not([disabled])").forEach { control ->
                val call = control.attr("onclick")
                callNumber(call, "do_unlock_ability")?.let { ability ->
                    action("form_unlockability", "升级", "unlock_ability" to ability)?.let(actions::add)
                }
                callNumber(call, "do_reset_ability")?.let { ability ->
                    val cost = callNumbers(call, "do_reset_ability").getOrNull(1) ?: "0"
                    action("form_resetability", "重置", "reset_ability" to ability)?.let {
                        actions += it.copy(confirmation = "$name\n$cost Soul Fragments")
                    }
                }
                callNumber(call, "do_equip_ability")?.let { ability ->
                    action("form_equipslot", "装入", "equip_ability" to ability)?.let(actions::add)
                }
            }
            val family = abilityFamily(slot)
            val acquired = slot.attr("draggable") == "true" || slot.hasAttr("ondragstart") ||
                slot.attr("onclick").contains("do_equip_ability(")
            if (acquired) {
                abilityOptions.getOrPut(family) { mutableListOf() } += CharacterFieldOption(id, name)
            }
            val current = hover.getOrNull(2).orEmpty()
            val next = hover.getOrNull(3).orEmpty()
            val cost = row.children().getOrNull(3).hvText()
            val status = if (current == "Not Acquired") current else if (next == "At Maximum") next
                else cost
            val details = listOfNotNull(hover.getOrNull(1), current.takeUnless { it == "Not Acquired" },
                next.takeUnless { it == "At Maximum" }?.let { "Next: ${plain(it)}" })
                .map(::plain).filter { it.isNotBlank() }.joinToString("\n\n")
            CharacterSectionRow(name, value = status, actions = actions, expandedDetail = details,
                ability = CharacterAbilityInfo(id, family, acquired,
                    equippedSlotIds = abilitySlots.map { it.second }.filter {
                        it.equippedName.isNotBlank() && it.equippedName == name && it.family == family
                    }.map { it.id },
                    upgradeCost = Regex("^Cost:\\s*(.+)$", RegexOption.IGNORE_CASE)
                        .matchEntire(cost)?.groupValues?.get(1).orEmpty(),
                    description = plain(hover.getOrNull(1).orEmpty()),
                    tiers = abilityTiers(row, current, next),
                    upgradeRequirements = abilityUpgradeRequirements(next, currentLevel, availableAbilityPoints)))
        }
        groups += CharacterSectionGroup(links.firstOrNull { it.selected }?.label ?: "Abilities", rows)
        val slotCounts = mutableMapOf<String, Int>()
        val slotRows = abilitySlots.map { (slot, info) ->
            val id = info.id
            val hover = jsStrings(slot.attr("onmouseover"))
            val title = slot.parent()?.attr("title").orEmpty()
            val family = info.family
            val kind = title.substringBefore(" - ").removeSuffix(" Ability Slot")
                .ifBlank { SLOT_FAMILIES[family] ?: "Ability" }
            val ordinal = slotCounts.getOrDefault(kind, 0) + 1
            slotCounts[kind] = ordinal
            val slotLabel = "$kind $ordinal"
            val name = info.equippedName.ifBlank { "(Empty)" }
            val actions = mutableListOf<NativeAction>()
            callNumber(slot.attr("onclick"), "do_unlock_slot")?.let { number ->
                action("form_unlockslot", "解锁", "unlock_slot" to number)?.let(actions::add)
            }
            val unlocked = info.unlocked
            var slotForm: CharacterSectionForm? = null
            if (unlocked) {
                if (callNumber(slot.attr("onclick"), "do_equip_slot") != null)
                    action("form_equipslot", "清空", "equip_slot" to id, "equip_ability" to "0")?.let { actions += it }
                val available = abilityOptions[family].orEmpty()
                if (available.isNotEmpty()) action("form_equipslot", "装入", "equip_slot" to id)?.let {
                    val selected = available.firstOrNull { option -> option.label == name }?.value.orEmpty()
                    val choices = listOf(CharacterFieldOption("", "选择技能", enabled = false)) + available
                    slotForm = CharacterSectionForm(slotLabel, it, listOf(CharacterSectionField("equip_ability", "Ability",
                        selected, CharacterFieldKind.Choice, choices)))
                }
            }
            val detail = if (unlocked) name else title.substringAfter(" - ", title)
            CharacterSectionRow(slotLabel, detail, actions = actions, form = slotForm,
                expandedDetail = hover.drop(1).filterNot { it == "At Maximum" || it == "Not Acquired" }
                    .map(::plain).filter { it.isNotBlank() }.joinToString("\n\n"), abilitySlot = info)
        }
        groups += CharacterSectionGroup("Ability Slots", slotRows)
        doc.select("#ability_top [onclick]").firstOrNull { it.attr("onclick").contains("do_reset_all(") }?.let { reset ->
            val cost = callNumber(reset.attr("onclick"), "do_reset_all") ?: "0"
            action("form_resetall", "重置全部", "reset_all" to "1")?.let {
                groups += CharacterSectionGroup("", listOf(CharacterSectionRow("Reset All", actions = listOf(
                    it.copy(confirmation = "重置全部技能点和精通点分配？\n$cost Soul Fragments")))))
            }
        }
        val currentFamilies = rows.mapNotNull { it.ability?.family?.takeIf(String::isNotBlank) }.toSet()
        val navigation = links.map { if (it.selected) it.copy(abilityFamilies = currentFamilies) else it }
        return CharacterSectionsPage(HvRoute.Abilities, summary = summary, navigation = navigation, groups = groups)
    }

    private fun abilityUpgradeRequirements(next: String, currentLevel: Int?, availableAbilityPoints: Int?): CharacterAbilityUpgradeRequirements? {
        val body = Jsoup.parseBodyFragment(next).body()
        body.select("div").remove()
        val match = ABILITY_REQUIREMENTS.matchEntire(body.text()) ?: return null
        val requiredLevel = match.groupValues[1].replace(",", "").toIntOrNull() ?: return null
        val requiredPoints = match.groupValues[2].replace(",", "").toIntOrNull() ?: return null
        return CharacterAbilityUpgradeRequirements(requiredLevel, requiredPoints, currentLevel, availableAbilityPoints)
    }

    private fun abilityTiers(row: Element, current: String, next: String): List<CharacterAbilityTier> {
        val bar = row.children().getOrNull(2) ?: return emptyList()
        if (bar.classNames().none { ABILITY_TIER_BAR.matches(it) }) return emptyList()
        val states = bar.children().map { pip ->
            val sprite = ABILITY_TIER_IMAGE.find(pip.attr("style"))?.groupValues?.get(1) ?: return emptyList()
            when (sprite.last()) {
                'f' -> CharacterAbilityTierState.Acquired
                'u' -> CharacterAbilityTierState.Available
                'x' -> CharacterAbilityTierState.Locked
                else -> return emptyList()
            }
        }
        val currentTier = states.count { it == CharacterAbilityTierState.Acquired }
        return states.mapIndexed { index, state ->
            val number = index + 1
            val detail = when (number) {
                currentTier -> current
                currentTier + 1 -> next
                else -> ""
            }.takeUnless { it == "Not Acquired" || it == "At Maximum" }.orEmpty()
            val body = Jsoup.parseBodyFragment(detail).body()
            val effects = body.children().filter { it.tagName() == "div" }.joinToString("\n") { it.text() }
            body.select("div").remove()
            CharacterAbilityTier(number, state, requirements = body.text(), effects = effects)
        }
    }

    private fun formAction(form: Element, url: HttpUrl, label: String, vararg changes: Pair<String, String>): NativeAction {
        val action = form.attr("action")
        val target = if (action.isBlank()) url else requireNotNull(url.resolve(action))
        val names = changes.map { it.first }.toSet()
        return NativeAction(target, form.attr("method").ifBlank { "GET" }.uppercase(),
            successfulControls(form).filterNot { it.first in names } + changes, label)
    }

    private fun successfulControls(form: Element): List<Pair<String, String>> = form.select("input[name], select[name], textarea[name]")
        .filterNot { it.hasAttr("disabled") }.mapNotNull { input ->
            val type = input.attr("type").lowercase()
            when {
                type in setOf("submit", "button", "image", "reset", "file") -> null
                type in setOf("checkbox", "radio") && !input.hasAttr("checked") -> null
                input.tagName() == "select" -> (input.selectFirst("option[selected]") ?: input.selectFirst("option"))
                    ?.let { input.attr("name") to it.attr("value") }
                input.tagName() == "textarea" -> input.attr("name") to input.wholeText()
                else -> input.attr("name") to if (type == "checkbox" && !input.hasAttr("value")) "on" else input.attr("value")
            }
        }

    private fun location(call: String, url: HttpUrl): HttpUrl? =
        Regex("document\\.location\\s*=\\s*['\"]([^'\"]+)['\"]").find(call)?.groupValues?.get(1)?.let(url::resolve)
    private fun callNumber(call: String, name: String): String? = callNumbers(call, name).firstOrNull()
    private fun callNumbers(call: String, name: String): List<String> =
        Regex(Regex.escape(name) + "\\(\\s*([0-9]+(?:\\s*,\\s*[0-9]+)*)\\s*\\)").find(call)
            ?.groupValues?.get(1)?.split(',')?.map { it.trim() }.orEmpty()
    private fun plain(value: String): String = Jsoup.parseBodyFragment(value).text()

    /** Only decode quoted literals from an observed tooltip call; never evaluate JavaScript. */
    private fun jsStrings(call: String, name: String = "overability"): List<String> {
        if (!call.trimStart().startsWith("$name(")) return emptyList()
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

    private fun abilityFamily(slot: Element): String =
        Regex("/ab/([rgbp])[ul]\\.png").find(slot.parent()?.attr("style").orEmpty())?.groupValues?.get(1).orEmpty()

    private fun equipmentDetails(html: String): String {
        if (html.isBlank()) return ""
        val doc = Jsoup.parseBodyFragment(html)
        val parts = doc.select(".eq > div").flatMap { block ->
            if (block.hasClass("ex") || block.hasClass("ep")) block.children().map { it.hvText() }
            else listOf(block.hvText())
        }
        return parts.filter { it.isNotBlank() }.joinToString("\n")
    }

    // Slot families are identified by the original slot backgrounds and their empty-slot titles.
    private val SLOT_FAMILIES = mapOf("r" to "Major", "g" to "Supportive", "b" to "Protection Augment", "p" to "Drain Augment")
    // Families observed in the saved original ability trees; mixed trees must stay in both pickers.
    private val TREE_FAMILIES = mapOf(
        "general" to setOf("r"), "onehanded" to setOf("r"), "twohanded" to setOf("r"),
        "dualwield" to setOf("r"), "niten" to setOf("r"), "staff" to setOf("r"),
        "cloth" to setOf("r"), "light" to setOf("r"), "heavy" to setOf("r"),
        "elemental" to setOf("r"), "divine" to setOf("r", "g"), "forbidden" to setOf("r", "g"),
        "supportive1" to setOf("g"), "supportive2" to setOf("g", "b"),
        "deprecating1" to setOf("g"), "deprecating2" to setOf("g", "p"),
    )
    private val ABILITY_TIER_BAR = Regex("aw[0-9]+")
    private val ABILITY_TIER_IMAGE = Regex("/ab/[0-9]+([rgbp][fu]|x)\\.png")
    private val ABILITY_PLAYER_LEVEL = Regex("\\bLv\\.\\s*([\\d,]+)\\b", RegexOption.IGNORE_CASE)
    private val ABILITY_POINTS = Regex("Ability Points:\\s*([\\d,]+)", RegexOption.IGNORE_CASE)
    private val ABILITY_REQUIREMENTS = Regex("Requires Level ([\\d,]+) and ([\\d,]+) Ability Points?")

    private val TREE_NAMES = mapOf("general" to "General", "onehanded" to "One-handed", "twohanded" to "Two-handed",
        "dualwield" to "Dual-wielding", "niten" to "Niten Ichiryu", "staff" to "Staff", "cloth" to "Cloth Armor",
        "light" to "Light Armor", "heavy" to "Heavy Armor", "deprecating1" to "Deprecating I", "deprecating2" to "Deprecating II",
        "supportive1" to "Supportive I", "supportive2" to "Supportive II", "elemental" to "Elemental", "forbidden" to "Forbidden", "divine" to "Divine")
}
