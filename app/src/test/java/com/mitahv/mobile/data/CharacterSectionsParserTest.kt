package com.mitahv.mobile.data

import com.mitahv.mobile.model.CharacterAbilityInfo
import com.mitahv.mobile.model.CharacterAbilitySlotInfo
import com.mitahv.mobile.model.CharacterAbilityTier
import com.mitahv.mobile.model.CharacterAbilityTierState
import com.mitahv.mobile.model.CharacterAbilityUpgradeRequirements
import com.mitahv.mobile.model.CharacterFieldKind
import com.mitahv.mobile.model.CharacterInventoryKind
import com.mitahv.mobile.model.CharacterSectionsPage
import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvWorld
import com.mitahv.mobile.model.HvSite
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterSectionsParserTest {
    @Test fun renamedEquipmentKeepsDisplayNameAndOriginalNameSeparate() {
        val original = "Legendary Ethereal Rapier of Slaughter"
        val alias = "My Legendary Blade"
        val dictionary = """var dynjs_equip={"101":{"t":"$alias","n":"$original","d":""},"102":{"t":"$original","d":""}};"""
        val overview = CharacterSectionsParser.parse("""<div id=eqch_outer><div id=eqsb>
            <div class=eqb onclick="document.location='?s=Character&amp;ss=eq&amp;equip_slot=1'">
            <div>Main Hand</div><div onmouseover='equips.set(101)'>$alias</div></div></div></div>""",
            HvRoute.Equipment, HvWorld.Persistent, supplementalScripts = listOf(dictionary))
        assertEquals(alias, overview.groups.single().rows.single().detail)
        assertEquals(original, overview.groups.single().rows.single().originalEquipmentName)

        val selection = CharacterSectionsParser.parse("""<script>const showequipped_eqid=101;</script>
            <form id=equipform method=post><input type=hidden name=equip_slot value=1>
            <div id=equipselect_outer><div id=equipselect_left>
            <div class=eqb onclick="document.location='?s=Character&amp;ss=eq'"><div>Main Hand</div><div>$alias</div></div>
            <div id=equiplist><table><tr><td><label><input name='eqids[]' type=checkbox value=101 checked>🗡️ $alias</label></td></tr>
            <tr><td><label><input name='eqids[]' type=checkbox value=102>$original</label></td></tr></table></div>
            <button name=action value=equip>Equip Selected</button></div></div></form>""",
            HvRoute.Equipment, HvWorld.Persistent, supplementalScripts = listOf(dictionary))
        assertEquals(original, selection.groups.first().rows.single().originalEquipmentName)
        val choices = selection.forms.single().fields.single().options
        assertEquals("🗡️ $alias", choices.first().label)
        assertEquals(original, choices.first().originalEquipmentName)
        assertNull(choices.last().originalEquipmentName)
        assertEquals("101", selection.forms.single().actionWith(emptyMap(), emptyMap()).fields.toMap()["eqids[]"])
    }

    @Test fun alternatePersistentEquipmentKeepsSetsAndSlotActions() {
        val current = HvRoute.Equipment.url(HvWorld.Persistent, HvSite.Alt)
        val html = fixture("equipment").replace("https://hentaiverse.org/", "https://alt.hentaiverse.org/")
        val page = CharacterSectionsParser.parse(html, HvRoute.Equipment, HvWorld.Persistent, current)
        assertEquals("alt.hentaiverse.org", page.navigation[1].action!!.url.host)
        assertEquals("alt.hentaiverse.org", page.groups.first().rows.first().actions.single().url.host)
        assertEquals("/", page.groups.first().rows.first().actions.single().url.encodedPath)
    }

    @Test
    fun equipmentReadsRealSetsSlotsAndStatsWithoutEnablingUnavailableOffhand() {
        val page = parse("equipment", HvRoute.Equipment)
        assertEquals(listOf("Set 1", "Set 2", "Set 3"), page.navigation.map { it.label })
        assertTrue(page.navigation[0].selected)
        assertNull(page.navigation[0].action)
        assertEquals(mapOf("equip_set" to "2"), page.navigation[1].action!!.fields.toMap())
        assertEquals("POST", page.navigation[1].action!!.method)
        val slots = page.groups.first().rows
        assertEquals(7, slots.size)
        assertEquals("Legendary Tempestuous Willow Staff of Destruction", slots.first().detail)
        assertEquals("1", slots.first().actions.single().url.queryParameter("equip_slot"))
        assertEquals("GET", slots.first().actions.single().method)
        assertTrue(slots.single { it.title == "Off Hand" }.actions.isEmpty())
        val damage = page.groups.single { it.title == "Mainhand Attack" }.rows.first()
        assertEquals("Crushing Damage", damage.title)
        assertEquals("4629", damage.value)
    }

    @Test
    fun equipmentUsesOriginalDictionaryForNativeExpandableStats() {
        // Equipment IDs come from the original onmouseover handlers, not the display names.
        val page = CharacterSectionsParser.parse(fixture("equipment"), HvRoute.Equipment, HvWorld.Persistent,
            supplementalScripts = listOf(resource("equipment-data.js")))
        val mainHand = page.groups.first().rows.first()
        assertEquals(6, page.groups.first().rows.count { it.expandedDetail.isNotBlank() })
        assertTrue(mainHand.expandedDetail.contains("Magic Damage"))
        assertTrue(mainHand.expandedDetail.contains("Condition:"))
        assertFalse(mainHand.expandedDetail.contains("<div"))
        assertTrue(page.groups.first().rows.single { it.title == "Off Hand" }.expandedDetail.isEmpty())
        assertEquals("Legendary Tempestuous Willow Staff of Destruction", mainHand.detail)
    }

    @Test
    fun emptyEquipmentSelectionRetainsReturnNavigationWithoutEquipOrUnequip() {
        val page = parse("equipment-selection", HvRoute.Equipment)
        assertEquals(listOf("There are no available equipment of this type."), page.notices)
        assertTrue(page.forms.isEmpty())
        assertEquals(1, page.groups.flatMap { it.rows }.flatMap { it.actions }.size)
        assertEquals(HvRoute.Equipment.url(HvWorld.Persistent), page.groups.first().rows.first().actions.single().url)
    }

    @Test
    fun equipmentCandidatesKeepServerSelectionCategoriesAndOriginalPostFields() {
        val page = CharacterSectionsParser.parse(fixture("equipment-candidates-mainhand"), HvRoute.Equipment, HvWorld.Persistent,
            supplementalScripts = listOf(resource("equipment-candidates-data.js")))
        val form = page.forms.single()
        val field = form.fields.single()
        assertEquals(65, field.options.size)
        assertEquals("1061", field.value)
        assertEquals("Shortsword", field.options.first().group)
        assertEquals("Willow Staff", field.options.single { it.value == field.value }.group)
        assertTrue(field.options.first().detail.contains("Attack Accuracy"))
        assertTrue(field.options.single { it.value == field.value }.detail.contains("Magic Damage"))
        assertTrue(page.groups.first().rows.first().expandedDetail.contains("Magic Damage"))
        assertEquals("1061", form.actionWith(emptyMap(), emptyMap()).fields.toMap()["eqids[]"])
        val equip = form.actionWith(mapOf("eqids[]" to "1001"), emptyMap())
        assertEquals(mapOf("postoken" to "fixture-token", "equip_slot" to "1", "action" to "equip", "eqids[]" to "1001"), equip.fields.toMap())
        assertEquals(1, equip.fields.count { it.first == "eqids[]" })
        val unequip = page.groups.flatMap { it.rows }.flatMap { it.actions }.single { it.label == "卸下装备" }
        assertEquals("unequip", unequip.fields.toMap()["action"])
        assertEquals("fixture-token", unequip.fields.toMap()["postoken"])
        expectInvalid { form.actionWith(mapOf("eqids[]" to "9999"), emptyMap()) }
    }

    @Test
    fun emptyOffhandWaitsForExplicitSelectionAndDoesNotExposeDisabledUnequip() {
        val page = parse("equipment-candidates-offhand", HvRoute.Equipment)
        val form = page.forms.single()
        val field = form.fields.single()
        assertEquals(52, field.options.count { it.enabled })
        assertEquals("", field.value)
        assertFalse(form.canSubmit(emptyMap()))
        expectInvalid { form.actionWith(emptyMap(), emptyMap()) }
        assertTrue(page.groups.flatMap { it.rows }.flatMap { it.actions }.none { it.label == "卸下装备" })
        assertTrue(page.groups.first().rows.first().expandedDetail.isEmpty())
        val chosen = field.options.first { it.enabled }.value
        assertTrue(form.canSubmit(mapOf("eqids[]" to chosen)))
        val action = form.actionWith(mapOf("eqids[]" to chosen), emptyMap())
        assertEquals(mapOf("postoken" to "fixture-token", "equip_slot" to "2", "action" to "equip", "eqids[]" to chosen), action.fields.toMap())
        assertEquals(1, action.fields.count { it.first == "eqids[]" })
    }

    @Test
    fun trainingOnlyExposesObservedEnabledActionsAndKeepsBothHiddenControls() {
        val page = parse("training", HvRoute.Training)
        assertNull(page.trainingProgress)
        val rows = page.groups.flatMap { it.rows }
        assertTrue(rows.single { it.title == "Adept Learner" }.actions.isEmpty())
        assertTrue(rows.single { it.title == "Quartermaster" }.actions.isEmpty())
        val assimilator = rows.single { it.title == "Assimilator" }
        assertEquals("10/25", assimilator.value)
        val training = assimilator.training!!
        assertEquals("+10% Proficiency Experience", training.effect)
        assertEquals("1,183,393", training.cost)
        assertEquals("24 H", training.duration)
        assertEquals(10, training.currentLevel)
        assertEquals(25, training.levelCap)
        val capped = rows.single { it.title == "Adept Learner" }.training!!
        assertEquals(capped.currentLevel, capped.levelCap)
        assertNull(capped.cost)
        assertNull(capped.duration)
        assertEquals("0 H", rows.single { it.title == "Pack Rat" }.training!!.duration)
        assertEquals(listOf("Credits: 1,318,626"), page.summary)
        val action = assimilator.actions.single()
        assertEquals(mapOf("start_train" to "51", "cancel_train" to "0"), action.fields.toMap())
        assertEquals("Assimilator\n1,183,393 C · 24 H", action.confirmation)
        assertEquals(HvRoute.Training.url(HvWorld.Persistent), action.url)
    }

    @Test
    fun activeTrainingReadsActualNameProgressAndCancellationWithoutAllowingAnotherStart() {
        val page = parse("training-active", HvRoute.Training)
        val progress = page.trainingProgress!!
        assertEquals("Ability Boost", progress.name)
        assertEquals(0.41, progress.initialPercent, 0.00001)
        assertEquals(7202L, progress.totalSeconds)
        assertEquals(1795265242L, progress.endsAtServer)
        val cancel = progress.cancelAction!!
        assertEquals(mapOf("start_train" to "0", "cancel_train" to "1"), cancel.fields.toMap())
        assertEquals("取消 Ability Boost？", cancel.confirmation)
        assertTrue(page.groups.flatMap { it.rows }.all { it.actions.isEmpty() })
        assertEquals("300/493", page.groups.single().rows.single { it.title == "Ability Boost" }.value)
        assertEquals(493, page.groups.single().rows.single { it.title == "Ability Boost" }.training!!.levelCap)
        assertEquals("GET", progress.refreshAction!!.method)
        assertEquals(HvRoute.Training.url(HvWorld.Persistent), progress.refreshAction!!.url)
        assertTrue(progress.refreshAction!!.fields.isEmpty())
    }

    @Test
    fun activeTrainingAdvancesFromObservedServerTimingAndCapsAtCompletion() {
        val progress = parse("training-active", HvRoute.Training).trainingProgress!!
        assertEquals(1.41, progress.percentAfter(72_020L), 0.00001)
        assertEquals(0.41, progress.percentAfter(-1000L), 0.00001)
        assertEquals(100.0, progress.percentAfter(7_202_000L), 0.00001)
        assertEquals(7173L, progress.remainingSecondsAfter(0L))
        assertEquals(0L, progress.remainingSecondsAfter(7_202_000L))
        assertNull(progress.copy(totalSeconds = null).remainingSecondsAfter(0L))
        val finalSeconds = progress.copy(initialPercent = 0.0, totalSeconds = 2L)
        assertEquals(1L, finalSeconds.remainingSecondsAfter(1999L))
        assertEquals(0L, finalSeconds.remainingSecondsAfter(2000L))
        // A freshly fetched response's server time takes precedence over an older progress text.
        val advanced = fixture("training-active").replace("var start_time = 1795258040;", "var start_time = 1795261641;")
        val refreshed = CharacterSectionsParser.parse(advanced, HvRoute.Training, HvWorld.Persistent).trainingProgress!!
        assertEquals(50.0, refreshed.initialPercent, 0.00001)
        assertEquals(51.0, refreshed.percentAfter(72_020L), 0.00001)
    }

    @Test
    fun inventoryOnlyListsEquipableConsumablesInBattleSlotChoices() {
        val page = parse("inventory", HvRoute.ItemInventory)
        assertEquals(27, page.groups.single().rows.size)
        assertEquals(2, page.forms.size)
        assertEquals("Slot 1", page.forms[0].title)
        val first = page.forms[0].fields.single()
        assertEquals("11191", first.value)
        assertEquals(27, first.options.size)
        assertTrue(first.options.any { it.value == "11195" && it.label == "Health Potion · 12349" })
        assertFalse(first.options.any { it.value == "20001" })
        val cleared = page.forms[0].actionWith(mapOf("item" to "0"), emptyMap())
        assertEquals(mapOf("slot" to "1", "item" to "0"), cleared.fields.toMap())
        val equipped = page.forms[1].actionWith(mapOf("item" to "11295"), emptyMap())
        assertEquals(mapOf("slot" to "2", "item" to "11295"), equipped.fields.toMap())
    }

    @Test
    fun zeroStockItemAlreadyAssignedToSlotIsNotSilentlyReplaced() {
        val doc = Jsoup.parse(fixture("inventory"))
        doc.selectFirst("#inv_item #item_11191")!!.closest("tr")!!.remove()
        val page = CharacterSectionsParser.parse(doc.outerHtml(), HvRoute.ItemInventory, HvWorld.Persistent)
        val form = page.forms.first()
        assertEquals("11191", form.fields.single().value)
        assertEquals("Health Draught · 0", form.fields.single().options.single { it.value == "11191" }.label)
        assertEquals("11191", form.actionWith(emptyMap(), emptyMap()).fields.toMap()["item"])
    }

    @Test
    fun inventoryUsesRealTooltipsAndOriginalAutomaticSlotAction() {
        val page = CharacterSectionsParser.parse(fixture("inventory-equipped"), HvRoute.ItemInventory, HvWorld.Persistent,
            supplementalScripts = listOf(resource("item-data.js")))
        val potion = page.groups.single().rows.single { it.title == "Health Potion" }
        assertEquals("11195", potion.inventory?.id)
        assertEquals("Consumable", potion.inventory?.category)
        assertEquals(CharacterInventoryKind.General, potion.inventory?.kind)
        assertEquals("Instantly restores a large amount of health.", potion.expandedDetail)
        assertEquals(mapOf("slot" to "0", "item" to "11195"), potion.actions.single().fields.toMap())
        assertNull(potion.actions.single().confirmation)
        val artifact = page.groups.single().rows.single { it.title == "Precursor Artifact" }
        assertEquals("Artifact", artifact.inventory?.category)
        assertTrue(artifact.expandedDetail.contains("ancient and long-lost civilization"))
        assertTrue(artifact.actions.isEmpty())
        assertEquals(25, page.forms.size)
        assertEquals("13299", page.forms.single { it.title == "Slot S1" }.fields.single().value)
        assertEquals("12401", page.forms.single { it.title == "Slot N1" }.fields.single().value)
        val firstSlot = page.forms.first()
        assertEquals("Provides a long-lasting health restoration effect.",
            firstSlot.fields.single().options.single { it.value == "11191" }.detail)
        assertEquals(mapOf("slot" to "201", "item" to "0"),
            page.forms.single { it.title == "Slot N1" }.actionWith(mapOf("item" to "0"), emptyMap()).fields.toMap())
    }

    @Test
    fun dedicatedInventorySlotsOnlyOfferCompatibleItemsRegardlessOfTranslatedNames() {
        val doc = Jsoup.parse(fixture("inventory-equipped"))
        doc.selectFirst("#inv_item #item_13101")!!.text("迅捷卷轴")
        doc.selectFirst("#inv_item #item_12401")!!.text("风暴魔药")
        val page = CharacterSectionsParser.parse(doc.outerHtml(), HvRoute.ItemInventory, HvWorld.Persistent)
        val regular = page.forms.first()
        assertEquals(CharacterInventoryKind.General, regular.inventorySlot?.kind)
        assertTrue(regular.fields.single().options.any { it.value == "13101" })
        assertTrue(regular.fields.single().options.any { it.value == "12401" })
        val scroll = page.forms.single { it.inventorySlot?.label == "S1" }
        assertEquals("101", scroll.inventorySlot?.id)
        assertEquals(CharacterInventoryKind.Scroll, scroll.inventorySlot?.kind)
        assertEquals(setOf("0", "13101", "13111", "13199", "13201", "13211", "13221", "13299"),
            scroll.fields.single().options.map { it.value }.toSet())
        assertTrue(scroll.canSubmit(mapOf("item" to "13101")))
        assertFalse(scroll.canSubmit(mapOf("item" to "11195")))
        val infusion = page.forms.single { it.inventorySlot?.label == "N1" }
        assertEquals(CharacterInventoryKind.Infusion, infusion.inventorySlot?.kind)
        assertEquals(setOf("0", "12101", "12201", "12301", "12401", "12501", "12601"),
            infusion.fields.single().options.map { it.value }.toSet())
        assertFalse(infusion.canSubmit(mapOf("item" to "13101")))
        assertEquals(mapOf("slot" to "201", "item" to "12101"),
            infusion.actionWith(mapOf("item" to "12101"), emptyMap()).fields.toMap())
    }

    @Test
    fun dedicatedInventorySlotKeepsAssignedZeroStockItem() {
        val doc = Jsoup.parse(fixture("inventory-equipped"))
        doc.selectFirst("#inv_item #item_13299")!!.closest("tr")!!.remove()
        val page = CharacterSectionsParser.parse(doc.outerHtml(), HvRoute.ItemInventory, HvWorld.Persistent)
        val scroll = page.forms.single { it.inventorySlot?.label == "S1" }
        assertEquals("13299", scroll.fields.single().value)
        assertEquals("Scroll of the Gods · 0", scroll.fields.single().options.single { it.value == "13299" }.label)
        assertTrue(scroll.canSubmit(emptyMap()))
        assertEquals(mapOf("slot" to "101", "item" to "13299"),
            scroll.actionWith(emptyMap(), emptyMap()).fields.toMap())
    }

    @Test
    fun inventoryWithoutExternalDataKeepsInlineRareItemDescriptionAndUsableSlots() {
        val page = parse("inventory-equipped", HvRoute.ItemInventory)
        assertTrue(page.groups.single().rows.single { it.title == "Precursor Artifact" }.expandedDetail.contains("Shrine of Snowflake"))
        assertTrue(page.groups.single().rows.single { it.title == "Health Potion" }.expandedDetail.isEmpty())
        assertTrue(page.forms.all { it.canSubmit(emptyMap()) })
    }

    @Test
    fun editingOneSettingsGroupPreservesEveryOtherSuccessfulControl() {
        val page = parse("settings", HvRoute.Settings)
        assertEquals(9, page.forms.size)
        val challenge = page.forms.single { it.title == "Challenge Level" }
        assertEquals("7", challenge.fields.single().value)
        assertEquals("PFUDOR · X20.0", challenge.fields.single().options.last().label)
        val old = challenge.action.fields.toMap()
        val edited = challenge.actionWith(mapOf("difflevel" to "2"), emptyMap()).fields.toMap()
        assertEquals(old + ("difflevel" to "2"), edited)
        assertEquals("104", edited["title_override"])
        assertEquals("311", edited["1"])
        assertEquals("411", edited["ac_0"])
        assertEquals("4", edited["as_c_10"])
        assertEquals("6", edited["as_s_10"])
        assertEquals("Apply Changes", edited["submit"])
        assertEquals("on", edited["fontlocal"])
        assertFalse(edited.containsKey("eqs_ability"))
    }

    @Test
    fun settingsCheckboxesAreOmittedWhenUncheckedAndOtherGroupsRemainUnchanged() {
        val page = parse("settings", HvRoute.Settings)
        val font = page.forms.single { it.title == "Font Engine" }
        assertEquals(CharacterFieldKind.Toggle, font.fields.single { it.name == "fontlocal" }.kind)
        val changedFont = font.actionWith(mapOf("fontface" to "sans-serif"), mapOf("fontlocal" to false)).fields.toMap()
        assertFalse(changedFont.containsKey("fontlocal"))
        assertEquals("sans-serif", changedFont["fontface"])
        assertEquals("7", changedFont["difflevel"])
        val sets = page.forms.single { it.title == "Equipment Sets" }
        val changedSet = sets.actionWith(emptyMap(), mapOf("eqs_item" to true)).fields.toMap()
        assertEquals("on", changedSet["eqs_item"])
        assertFalse(changedSet.containsKey("eqs_ability"))
        assertEquals("on", changedSet["fontlocal"])
        val autoSell = page.forms.single { it.title == "Auto-Sell / Auto-Salvage" }
        assertEquals("Cotton Armor · Auto-Sell", autoSell.fields.single { it.name == "as_c_10" }.label)
        assertEquals("Cotton Armor · Auto-Salvage", autoSell.fields.single { it.name == "as_s_10" }.label)
    }

    @Test
    fun settingsRecognizesUppercaseHeadingsFromTheOriginalBitmapFont() {
        val doc = Jsoup.parse(fixture("settings"))
        doc.select(".settings_block").forEach { block ->
            val heading = block.children().first()!!
            heading.text(heading.text().uppercase())
        }
        val page = CharacterSectionsParser.parse(doc.outerHtml(), HvRoute.Settings, HvWorld.Persistent)
        assertEquals(parse("settings", HvRoute.Settings).forms.map { it.title }, page.forms.map { it.title })
        assertEquals("Quickbar Slots", page.forms.single { group -> group.fields.any { it.name == "1" } }.title)
        assertEquals("Challenge Level", page.forms.first().fields.single().label)
    }

    @Test
    fun combinedSettingsSubmitKeepsEditsAcrossGroupsAndOriginalHiddenControls() {
        val doc = Jsoup.parse(fixture("settings"))
        doc.selectFirst("#settings_outer form")!!.appendElement("input")
            .attr("type", "hidden").attr("name", "settings_token").attr("value", "fixture-token")
        val page = CharacterSectionsParser.parse(doc.outerHtml(), HvRoute.Settings, HvWorld.Persistent)
        val form = requireNotNull(page.combinedSettingsForm())
        val values = mapOf("difflevel" to "2", "fontface" to "sans-serif", "1" to "313",
            "ac_0" to "412", "as_c_10" to "2", "as_s_10" to "3")
        val toggles = mapOf("fontlocal" to false, "eqs_item" to true)
        val action = form.actionWith(values, toggles)
        assertEquals((form.action.fields.toMap() + values + ("eqs_item" to "on")) - "fontlocal", action.fields.toMap())
        assertEquals("fixture-token", action.fields.toMap()["settings_token"])
        assertEquals("Apply Changes", action.fields.toMap()["submit"])
        assertEquals("POST", action.method)
        assertEquals(form.action.url, action.url)
        assertTrue(form.canSubmit(values))
        assertFalse(form.canSubmit(values + ("ac_0" to "999")))
    }

    @Test
    fun settingsKeepSourceDescriptionsAndFontHintsSeparateFromControls() {
        val page = parse("settings", HvRoute.Settings)
        val font = page.forms.single { it.title == "Font Engine" }
        assertTrue(font.description.startsWith("Here you can choose a custom font"))
        assertFalse(font.description.contains("Use Custom Font"))
        assertEquals("Allowed: 5 to 20 (points)", font.fields.single { it.name == "fontsize" }.hint)
        assertEquals("Allowed: -8 to 8 pixels (tweak until text appears centered)",
            font.fields.single { it.name == "fontoff" }.hint)
        assertEquals("", font.fields.single { it.name == "fontface" }.hint)
        val autocast = page.forms.single { it.title == "Auto-Cast Slots" }
        assertTrue(autocast.description.contains("below 10%"))
        assertFalse(autocast.description.contains("Upkeep:"))
        assertEquals(listOf("Upkeep: 2.56 MP/round"), page.summary)
        val salvage = page.forms.single { it.title == "Auto-Sell / Auto-Salvage" }
        assertTrue(salvage.description.contains("specified qualify and below"))
        assertTrue(salvage.description.contains("lowest required quality"))
    }

    @Test
    fun settingsRejectsUnavailableChoiceAndOverlongText() {
        val page = parse("settings", HvRoute.Settings)
        expectInvalid { page.forms.first().actionWith(mapOf("difflevel" to "999"), emptyMap()) }
        val doc = Jsoup.parse(fixture("settings"))
        doc.selectFirst("input[name=fontface]")!!.attr("maxlength", "16")
        val limited = CharacterSectionsParser.parse(doc.outerHtml(), HvRoute.Settings, HvWorld.Persistent)
            .forms.single { it.title == "Font Engine" }
        expectInvalid { limited.actionWith(mapOf("fontface" to "x".repeat(17)), emptyMap()) }
    }

    @Test
    fun abilitiesReadPointsTreeNavigationUpgradeAndSlotUnlockWithoutGuessingAcquiredAbilities() {
        val page = parse("abilities", HvRoute.Abilities)
        assertEquals(listOf("ABILITY POINTS: 3", "MASTERY POINTS: 1"), page.summary)
        assertEquals(16, page.navigation.size)
        assertTrue(page.navigation.first().selected)
        val oneHanded = page.navigation.single { it.label == "One-handed" }.action!!
        assertEquals("GET", oneHanded.method)
        assertEquals("onehanded", oneHanded.url.queryParameter("tree"))
        val hp = page.groups.single { it.title == "General" }.rows.first()
        assertEquals("HP Tank", hp.title)
        assertEquals("Not Acquired", hp.value)
        assertAbilityMetadata(CharacterAbilityInfo("1101", "r", acquired = false, upgradeCost = "1 AP"), hp.ability)
        assertEquals(0, hp.ability!!.currentTier)
        assertEquals(10, hp.ability!!.maxTier)
        assertEquals(CharacterAbilityTierState.Available, hp.ability!!.tiers.first().state)
        assertTrue(hp.ability!!.tiers.drop(1).all { it.state == CharacterAbilityTierState.Locked })
        assertEquals("Requires Level 0 and 1 Ability Points", hp.ability!!.tiers.first().requirements)
        assertEquals("Direct Player Stat Modification\n+10% Maximum Health", hp.ability!!.tiers.first().effects)
        assertTrue(hp.ability!!.tiers.drop(1).all { it.requirements.isEmpty() && it.effects.isEmpty() })
        assertEquals("解锁", hp.actions.single().label)
        assertEquals(mapOf("unlock_ability" to "1101"), hp.actions.single().fields.toMap())
        assertNull(hp.actions.single().confirmation)
        val slots = page.groups.single { it.title == "Ability Slots" }.rows
        assertEquals("Major 1", slots.first().title)
        assertEquals(CharacterAbilitySlotInfo("101", "r", unlocked = true), slots.first().abilitySlot)
        assertTrue(slots.first().actions.isEmpty())
        assertTrue(slots.all { it.form == null })
        val locked = slots.single { it.title == "Major 6" }
        assertEquals(CharacterAbilitySlotInfo("113", "r", unlocked = false), locked.abilitySlot)
        val unlock = locked.actions.single()
        assertEquals(mapOf("unlock_slot" to "113"), unlock.fields.toMap())
        assertNull(unlock.confirmation)
        val resetAll = page.groups.flatMap { it.rows }.flatMap { it.actions }.single { it.label == "重置全部" }
        assertEquals(mapOf("reset_all" to "1"), resetAll.fields.toMap())
        assertTrue(resetAll.confirmation!!.contains("0 Soul Fragments"))
    }

    @Test
    fun inactiveCategoryWithoutNavigationIsNotTheSelectedTree() {
        val doc = Jsoup.parse(fixture("abilities"))
        doc.selectFirst("img[src$=tdonehanded.png]")!!.removeAttr("onclick")
        val links = CharacterSectionsParser.parse(doc.outerHtml(), HvRoute.Abilities, HvWorld.Persistent).navigation
        assertEquals("general", links.single { it.selected }.abilityTreeId)
        assertNull(links.single { it.abilityTreeId == "onehanded" }.action)
    }

    @Test
    fun abilitySlotTreeChoicesExcludeIncompatibleCategoriesAndChooseUsefulDefaults() {
        val page = parse("abilities", HvRoute.Abilities)
        fun slot(family: String) = CharacterAbilitySlotInfo("test", family, unlocked = true)
        fun treeIds(family: String) = page.abilityTreesFor(slot(family)).map { it.abilityTreeId }.toSet()
        assertEquals(setOf("general", "onehanded", "twohanded", "dualwield", "niten", "staff",
            "cloth", "light", "heavy", "elemental", "divine", "forbidden"), treeIds("r"))
        assertEquals(setOf("supportive1", "supportive2", "deprecating1", "deprecating2", "divine", "forbidden"), treeIds("g"))
        assertEquals(setOf("supportive2"), treeIds("b"))
        assertEquals(setOf("deprecating2"), treeIds("p"))
        assertEquals("general", page.preferredAbilityTreeFor(slot("r"))!!.abilityTreeId)
        assertEquals("supportive1", page.preferredAbilityTreeFor(slot("g"))!!.abilityTreeId)
        assertEquals("supportive2", page.preferredAbilityTreeFor(slot("b"))!!.abilityTreeId)
        assertEquals("deprecating2", page.preferredAbilityTreeFor(slot("p"))!!.abilityTreeId)
        assertNull(page.preferredAbilityTreeFor(slot("unknown")))

        // Display labels and unavailable links must not determine the selected destination.
        val unavailableDefault = page.copy(navigation = page.navigation.map { link ->
            link.copy(label = "translated", action = link.action.takeUnless { link.abilityTreeId == "supportive1" })
        })
        assertFalse(unavailableDefault.abilityTreesFor(slot("g")).any { it.abilityTreeId == "supportive1" })
        assertEquals(unavailableDefault.abilityTreesFor(slot("g")).first(),
            unavailableDefault.preferredAbilityTreeFor(slot("g")))
    }

    @Test
    fun abilitySlotTreeChoiceKeepsCompatibleCurrentTreeIncludingMixedFamilies() {
        val cloth = parse("abilities-cloth", HvRoute.Abilities)
        val major = CharacterAbilitySlotInfo("101", "r", unlocked = true)
        assertEquals("cloth", cloth.preferredAbilityTreeFor(major)!!.abilityTreeId)
        val supportive = parse("abilities-supportive", HvRoute.Abilities)
        val current = supportive.navigation.single { it.selected }
        assertEquals("supportive2", current.abilityTreeId)
        assertEquals(setOf("g", "b"), current.abilityFamilies)
        assertEquals(current, supportive.preferredAbilityTreeFor(CharacterAbilitySlotInfo("201", "g", unlocked = true)))
        assertEquals(current, supportive.preferredAbilityTreeFor(CharacterAbilitySlotInfo("301", "b", unlocked = true)))
        assertEquals("general", supportive.preferredAbilityTreeFor(major)!!.abilityTreeId)
    }

    @Test
    fun acquiredAbilityActionsUseFormFieldsAndRetainCurrentTreeUrl() {
        // Synthetic acquired state exercises handlers evidenced by the inline script, without a live mutation.
        val doc = Jsoup.parse(fixture("abilities"))
        doc.selectFirst("#slot_1101")!!.attr("draggable", "true").attr("onclick", "do_equip_ability(1101)")
        doc.selectFirst("#ability_treepane > div")!!.append("<img onclick='do_reset_ability(1101, 25)'>")
        doc.selectFirst("#slot_101")!!.attr("onmouseover", "overability(0, 'HP Tank','HP bonus','Tier 1','Tier 2')")
            .attr("onclick", "do_equip_slot(101)")
        val url = "https://hentaiverse.org/?s=Character&ss=ab&tree=general".toHttpUrl()
        val page = CharacterSectionsParser.parse(doc.outerHtml(), HvRoute.Abilities, HvWorld.Persistent, url)
        val advance = page.groups.first().rows.first().actions.single { it.label == "升级" }
        assertEquals(mapOf("unlock_ability" to "1101"), advance.fields.toMap())
        assertEquals(url, advance.url)
        val slot = page.groups.single { it.title == "Ability Slots" }.rows.first()
        assertEquals(CharacterAbilitySlotInfo("101", "r", unlocked = true, equippedName = "HP Tank", mayUpgrade = true), slot.abilitySlot)
        assertAbilityMetadata(CharacterAbilityInfo("1101", "r", acquired = true,
            equippedSlotIds = listOf("101"), upgradeCost = "1 AP"), page.groups.first().rows.first().ability)
        assertNotNull(slot.form)
        val equip = slot.form!!.actionWith(mapOf("equip_ability" to "1101"), emptyMap())
        assertEquals(mapOf("equip_slot" to "101", "equip_ability" to "1101"), equip.fields.toMap())
        assertEquals(url, equip.url)
        assertEquals(mapOf("equip_slot" to "101", "equip_ability" to "0"), slot.actions.single().fields.toMap())
        val reset = page.groups.first().rows.first().actions.single { it.label == "重置" }
        assertEquals(mapOf("reset_ability" to "1101"), reset.fields.toMap())
        assertTrue(reset.confirmation!!.contains("25 Soul Fragments"))
    }

    @Test
    fun acquiredAbilitiesUseRealEquipHandlersAndKeepFullEffectsOutOfSummary() {
        val page = parse("abilities-cloth", HvRoute.Abilities)
        val row = page.groups.first().rows.first()
        assertEquals("Cloth Spellacc", row.title)
        assertEquals("At Maximum", row.value)
        assertAbilityMetadata(CharacterAbilityInfo("3101", "r", acquired = true, equippedSlotIds = listOf("117")), row.ability)
        assertEquals(3, row.ability!!.currentTier)
        assertEquals(3, row.ability!!.maxTier)
        assertTrue(row.ability!!.tiers.all { it.state == CharacterAbilityTierState.Acquired })
        assertEquals("Requires Level 240 and 5 Ability Points", row.ability!!.tiers.last().requirements)
        assertTrue(row.ability!!.tiers.last().effects.endsWith("+1 Magic Accuracy"))
        assertTrue(row.ability!!.tiers.dropLast(1).all { it.requirements.isEmpty() && it.effects.isEmpty() })
        assertTrue(row.detail.isEmpty())
        assertTrue(row.expandedDetail.contains("+1 Magic Accuracy"))
        assertTrue(row.expandedDetail.contains("Level 240"))
        assertFalse(row.expandedDetail.contains("<strong>"))
        val equip = row.actions.single { it.label == "装入" }
        assertEquals(mapOf("equip_slot" to "0", "equip_ability" to "3101"), equip.fields.toMap())
        assertEquals("cloth", equip.url.queryParameter("tree"))
        assertNull(equip.confirmation)
        val firstSlot = page.groups.single { it.title == "Ability Slots" }.rows.first()
        assertEquals("Major 1", firstSlot.title)
        assertEquals("HP Tank", firstSlot.detail)
        assertEquals(CharacterAbilitySlotInfo("101", "r", unlocked = true, equippedName = "HP Tank"), firstSlot.abilitySlot)
        assertEquals("", firstSlot.form!!.fields.single().value)
        assertFalse(firstSlot.form!!.canSubmit(emptyMap()))
        expectInvalid { firstSlot.form!!.actionWith(emptyMap(), emptyMap()) }
        assertTrue(firstSlot.form!!.canSubmit(mapOf("equip_ability" to "3101")))
    }

    @Test
    fun abilitySlotChoicesRespectOriginalSlotFamiliesAndCurrentEquippedValue() {
        val page = parse("abilities-supportive", HvRoute.Abilities)
        val slots = page.groups.single { it.title == "Ability Slots" }.rows
        val major = slots.first()
        assertEquals("Major 1", major.title)
        assertNull(major.form)
        val supportive = slots.single { it.detail == "Better Spark" }
        assertTrue(supportive.title.startsWith("Supportive "))
        assertEquals(CharacterAbilitySlotInfo("215", "g", unlocked = true, equippedName = "Better Spark"), supportive.abilitySlot)
        val spark = page.groups.first().rows.single { it.title == "Better Spark" }
        assertAbilityMetadata(CharacterAbilityInfo("4110", "g", acquired = true, equippedSlotIds = listOf("215")), spark.ability)
        val form = supportive.form!!
        assertEquals("4110", form.fields.single().value)
        assertEquals(listOf("4110", "4101"), form.fields.single().options.filter { it.enabled }.map { it.value })
        assertTrue(form.canSubmit(emptyMap()))
        assertEquals(mapOf("equip_slot" to "215", "equip_ability" to "4110"), form.actionWith(emptyMap(), emptyMap()).fields.toMap())
        val augment = slots.single { it.title == "Protection Augment 1" }
        assertEquals("Frost Spike Shield", augment.detail)
        assertEquals(CharacterAbilitySlotInfo("301", "b", unlocked = true, equippedName = "Frost Spike Shield"), augment.abilitySlot)
        val frost = page.groups.first().rows.single { it.title == "Frost Spike Shield" }
        assertAbilityMetadata(CharacterAbilityInfo("4112", "b", acquired = true, equippedSlotIds = listOf("301")), frost.ability)
        val flame = page.groups.first().rows.single { it.title == "Flame Spike Shield" }
        assertAbilityMetadata(CharacterAbilityInfo("4111", "b", acquired = false, upgradeCost = "3 AP"), flame.ability)
        assertEquals(5, flame.ability!!.maxTier)
        assertTrue(flame.ability!!.tiers.all { it.state == CharacterAbilityTierState.Available })
        assertTrue(flame.ability!!.tiers.drop(1).all { it.requirements.isEmpty() && it.effects.isEmpty() })
        assertEquals(mapOf("unlock_ability" to "4111"), flame.actions.single().fields.toMap())
        assertNull(flame.actions.single().confirmation)
        assertEquals(listOf("4112"), augment.form!!.fields.single().options.filter { it.enabled }.map { it.value })
        assertFalse(augment.form!!.canSubmit(mapOf("equip_ability" to "4110")))
        assertFalse(form.canSubmit(mapOf("equip_ability" to "4112")))
        val clear = augment.actions.single { it.label == "清空" }
        assertEquals(mapOf("equip_slot" to "301", "equip_ability" to "0"), clear.fields.toMap())
        val emptyMajor = slots.single { it.detail == "(Empty)" && it.title.startsWith("Major") }
        assertTrue(emptyMajor.actions.isEmpty())
    }

    @Test
    fun abilityUpgradeRequirementsReadOriginalReadoutAndBothPointFontFormats() {
        // Original ab-supportive1 page: #level_readout = PFUDOR Lv.493; AP 113, MP 3.
        val plain = Jsoup.parse(fixture("abilities-supportive"))
        plain.body().prepend("<div id='level_readout'><div class='fc4 far fcb'><div>PFUDOR Lv.493</div></div></div>")
        val page = CharacterSectionsParser.parse(plain.outerHtml(), HvRoute.Abilities, HvWorld.Persistent)
        val flame = page.groups.first().rows.single { it.title == "Flame Spike Shield" }.ability!!
        assertEquals(CharacterAbilityUpgradeRequirements(10, 3, 493, 113), flame.upgradeRequirements)
        assertEquals(true, flame.upgradeRequirements!!.levelMet)
        assertEquals(0, flame.upgradeRequirements!!.missingAbilityPoints)
        assertNull(page.groups.first().rows.single { it.title == "Better Spark" }.ability!!.upgradeRequirements)

        // The original fixture's AP/MP use bitmap glyphs. Encode LV.493 with the same hvg.css alphabet.
        val bitmap = Jsoup.parse(fixture("abilities"))
        bitmap.body().prepend("<div id='level_readout'><div class='fl f4b'><div class='c5l'></div><div class='c5v'></div>" +
            "<div class='c4a'></div><div class='c44'></div><div class='c49'></div><div class='c43'></div></div></div>")
        val hp = CharacterSectionsParser.parse(bitmap.outerHtml(), HvRoute.Abilities, HvWorld.Persistent).groups.first().rows.first()
        assertEquals(CharacterAbilityUpgradeRequirements(0, 1, 493, 3), hp.ability!!.upgradeRequirements)
    }

    @Test
    fun abilityUpgradeShortfallsDistinguishLevelPointsAndBothWithoutAddingServerActions() {
        // Flame Spike Shield's actual next-tier tooltip requires Level 10 and 3 Ability Points.
        // Only the player snapshot varies; a missing server upgrade handler remains missing.
        for ((level, points) in listOf(9 to 3, 10 to 2, 9 to 2, 10 to 3)) {
            val doc = Jsoup.parse(fixture("abilities-supportive"))
            doc.body().prepend("<div id='level_readout'>Normal Lv.$level</div>")
            doc.select("#ability_top > div").first { it.hvText().startsWith("Ability Points:") }.text("Ability Points: $points")
            doc.select("#ability_treepane [onclick]").filter { it.attr("onclick").contains("do_unlock_ability") }.forEach {
                it.removeAttr("onclick")
            }
            val flame = CharacterSectionsParser.parse(doc.outerHtml(), HvRoute.Abilities, HvWorld.Persistent)
                .groups.first().rows.single { it.title == "Flame Spike Shield" }
            val requirements = flame.ability!!.upgradeRequirements!!
            assertEquals(level >= 10, requirements.levelMet)
            assertEquals(if (points == 2) 1 else 0, requirements.missingAbilityPoints)
            assertFalse(flame.actions.any { action -> action.fields.any { it.first == "unlock_ability" } })
        }
    }

    @Test
    fun abilityUpgradeUnknownStatsAndUnsupportedRequirementsNeverInventShortfalls() {
        val fixturePage = parse("abilities", HvRoute.Abilities)
        val knownPoints = fixturePage.groups.first().rows.first().ability!!.upgradeRequirements!!
        assertNull(knownPoints.currentLevel)
        assertNull(knownPoints.levelMet)
        assertEquals(0, knownPoints.missingAbilityPoints)

        val doc = Jsoup.parse(fixture("abilities"))
        doc.select("#ability_top > div").filter { it.hvText().startsWith("ABILITY POINTS:") }.forEach { it.remove() }
        val unknown = CharacterSectionsParser.parse(doc.outerHtml(), HvRoute.Abilities, HvWorld.Persistent)
            .groups.first().rows.first().ability!!.upgradeRequirements!!
        assertNull(unknown.currentLevel)
        assertNull(unknown.availableAbilityPoints)
        assertNull(unknown.missingAbilityPoints)

        val hp = doc.selectFirst("#slot_1101")!!
        hp.attr("onmouseover", hp.attr("onmouseover").replace("1 Ability Points</strong>", "1 Ability Points</strong> and 100 proficiency"))
        val unrecognized = CharacterSectionsParser.parse(doc.outerHtml(), HvRoute.Abilities, HvWorld.Persistent)
            .groups.first().rows.first().ability!!
        assertNull(unrecognized.upgradeRequirements)
        assertTrue(unrecognized.tiers.first().requirements.contains("100 proficiency"))
    }

    @Test
    fun partialAbilityTiersKeepOnlyObservedCurrentAndNextEffectsWithoutEnablingUpgrade() {
        // These rank images and tooltip values are from the phone's read-only General response.
        val doc = Jsoup.parse(fixture("abilities"))
        val sourceRow = doc.selectFirst("#ability_treepane > div")!!
        val slot = sourceRow.selectFirst("#slot_1101")!!
        slot.attr("draggable", "true").attr("onclick", "do_equip_ability(1101)")
        slot.attr("onmouseover", "overability(0, 'HP Tank','Increases your maximum HP. This adds 10% to your total HP per tier.'," +
            "'Requires <strong>Level 0</strong> and <strong>1 Ability Points</strong><div><strong>Direct Player Stat Modification</strong></div><div>+10% Maximum Health</div>'," +
            "'Requires <strong>Level 25</strong> and <strong>2 Ability Points</strong><div><strong>Direct Player Stat Modification</strong></div><div>+20% Maximum Health</div>')")
        sourceRow.child(2).removeAttr("onclick").child(0).attr("style", "background-image:url(/y/ab/10rf.png)")
        sourceRow.child(3).text("Cost: 2 AP")
        val row = CharacterSectionsParser.parse(doc.outerHtml(), HvRoute.Abilities, HvWorld.Persistent).groups.first().rows.first()
        val info = row.ability!!
        assertEquals(1, info.currentTier)
        assertEquals(10, info.maxTier)
        assertEquals("Increases your maximum HP. This adds 10% to your total HP per tier.", info.description)
        assertEquals(CharacterAbilityTier(1, CharacterAbilityTierState.Acquired,
            "Requires Level 0 and 1 Ability Points", "Direct Player Stat Modification\n+10% Maximum Health"), info.tiers[0])
        assertEquals(CharacterAbilityTier(2, CharacterAbilityTierState.Locked,
            "Requires Level 25 and 2 Ability Points", "Direct Player Stat Modification\n+20% Maximum Health"), info.tiers[1])
        assertTrue(info.tiers.drop(2).all { it.state == CharacterAbilityTierState.Locked && it.requirements.isEmpty() && it.effects.isEmpty() })
        assertEquals("2 AP", info.upgradeCost)
        assertFalse(row.actions.any { action -> action.fields.any { it.first == "unlock_ability" } })
        assertEquals(mapOf("equip_slot" to "0", "equip_ability" to "1101"), row.actions.single().fields.toMap())
    }

    @Test
    fun actionlessFormsPreserveIsekaiBaseAndCurrentQuery() {
        val current = "https://alt.hentaiverse.org/isekai/?s=Character&ss=ab&tree=general".toHttpUrl()
        val html = fixture("abilities").replace("https://hentaiverse.org/", "https://alt.hentaiverse.org/isekai/")
        val page = CharacterSectionsParser.parse(html, HvRoute.Abilities, HvWorld.Isekai, current)
        assertEquals(current, page.groups.first().rows.first().actions.single().url)
        assertTrue(page.navigation.mapNotNull { it.action }.all { it.url.host == "alt.hentaiverse.org" && it.url.encodedPath == "/isekai/" })
    }

    @Test
    fun serverNoticesRemainVisibleAndUnrelatedPagesAreRejected() {
        val html = fixture("settings").replace("<body>", "<body><div id='messagebox'>Unable to save settings.</div>")
        assertEquals(listOf("Unable to save settings."), CharacterSectionsParser.parse(html, HvRoute.Settings, HvWorld.Persistent).notices)
        for (route in listOf(HvRoute.Equipment, HvRoute.Abilities, HvRoute.Training, HvRoute.ItemInventory, HvRoute.Settings)) {
            expectInvalid { CharacterSectionsParser.parse("<div>Login</div>", route, HvWorld.Persistent) }
        }
        expectInvalid { CharacterSectionsParser.parse(fixture("training"), HvRoute.Training, HvWorld.Isekai) }
        expectInvalid { CharacterSectionsParser.parse(fixture("abilities"), HvRoute.Abilities, HvWorld.Persistent,
            HvRoute.Abilities.url(HvWorld.Isekai)) }
    }

    private fun parse(file: String, route: HvRoute): CharacterSectionsPage =
        CharacterSectionsParser.parse(fixture(file), route, HvWorld.Persistent)

    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/character-sections/$name.html")).readText()
    private fun resource(name: String) = requireNotNull(javaClass.getResource("/character-sections/$name")).readText()

    private fun assertAbilityMetadata(expected: CharacterAbilityInfo, actual: CharacterAbilityInfo?) {
        assertNotNull(actual)
        assertEquals(expected, actual!!.copy(description = "", tiers = emptyList(), upgradeRequirements = null))
    }

    private fun expectInvalid(block: () -> Unit) {
        var rejected = false
        try { block() } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }
}
