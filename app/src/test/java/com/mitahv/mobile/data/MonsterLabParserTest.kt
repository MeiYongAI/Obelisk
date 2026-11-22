package com.mitahv.mobile.data

import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvWorld
import com.mitahv.mobile.model.submit
import com.mitahv.mobile.model.validationError
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonsterLabParserTest {
    @Test fun initialCreationOnlySelectsAClassWithoutCreatingAMonster() {
        val page = parse("create", "&create=new")
        assertEquals(13, page.links.size)
        assertTrue(page.links.none { it.selected })
        assertTrue(page.sections.isEmpty())
        assertTrue(page.actions.isEmpty())
        page.links.forEach { link ->
            assertEquals("POST", link.action.method)
            assertNull(link.action.confirmation)
            assertEquals("new", link.action.url.queryParameter("create"))
            assertEquals("", link.action.fields.toMap()["selected_patk"])
            assertTrue(link.action.fields.toMap()["selected_class"]!!.toInt() > 0)
        }
    }

    @Test fun allThirteenSuppliedClassesKeepTheirActualStatsAndAttackChoices() {
        val cases = listOf(
            Triple("arthropods", "1", listOf("crsh", "prcg")),
            Triple("avions", "2", listOf("prcg", "slsh")),
            Triple("beasts", "3", listOf("slsh", "prcg")),
            Triple("celestials", "4", listOf("slsh", "crsh")),
            Triple("daimons", "5", listOf("slsh", "prcg")),
            Triple("dragonkin", "6", listOf("prcg", "crsh")),
            Triple("elementals", "7", listOf("fire", "elec", "wind", "cold")),
            Triple("giants", "8", listOf("crsh")),
            Triple("humanoids", "10", listOf("prcg", "slsh", "crsh")),
            Triple("mechanoids", "12", listOf("prcg", "slsh")),
            Triple("reptilians", "13", listOf("prcg", "slsh")),
            Triple("sprites", "14", listOf("prcg", "slsh")),
            Triple("undeads", "15", listOf("crsh", "slsh")),
        )
        cases.forEach { (name, id, attackTypes) ->
            val page = parse("create-$name", "&create=new")
            assertEquals(name, 13, page.links.size)
            assertEquals(name, id, page.links.single { it.selected }.action.fields.toMap()["selected_class"])
            assertEquals(name, 6, page.sections.single { it.title == "Primary" }.facts.size)
            assertEquals(name, 6, page.sections.single { it.title == "Element" }.facts.size)
            assertEquals(name, 3, page.sections.single { it.title == "Defense" }.facts.size)
            val attacks = page.sections.single { it.title == "创建怪物" }.entries
            assertEquals(name, attackTypes, attacks.map { it.action!!.fields.toMap()["selected_patk"] })
            attacks.forEach { entry ->
                val action = requireNotNull(entry.action)
                assertEquals(id, action.fields.toMap()["selected_class"])
                assertEquals("POST", action.method)
                assertTrue(action.confirmation!!.contains(entry.title))
            }
        }
        assertEquals("-25", parse("create-humanoids", "&create=new").sections.single { it.title == "Element" }.facts.toMap()["Dark"])
    }

    @Test fun existingMonsterListAndItsRealActionsRemainAvailable() {
        val page = parse("list")
        assertTrue(page.sections.single().entries.size > 1)
        assertTrue(page.sections.single().entries.all { it.action?.method == "GET" })
        assertTrue(page.actions.any { it.fields.toMap()["feed_all"] == "food" })
        assertTrue(page.actions.any { it.fields.toMap()["feed_all"] == "drugs" })
        assertTrue(page.actions.any { it.fields.toMap()["unlock_slot"] == "1" })
        assertTrue(page.actions.any { it.method == "GET" && it.url.queryParameter("create") == "new" })
    }

    @Test fun createdUnnamedMonsterKeepsItsSlotAndTheRemainingEmptySlot() {
        val page = parse("unnamed-list")
        val entries = page.sections.single().entries
        assertEquals(60, entries.size)
        val monster = entries.single { it.title == "Unnamed Celestial" }
        assertEquals("59", monster.details.toMap()["Slot"])
        assertEquals("0", monster.details.toMap()["Level"])
        assertEquals("Celestial", monster.details.toMap()["Species"])
        val monsterAction = requireNotNull(monster.action)
        assertEquals("GET", monsterAction.method)
        assertEquals("59", monsterAction.url.queryParameter("slot"))
        val empty = entries.single { it.details.toMap()["Slot"] == "60" }
        assertEquals("new", empty.action!!.url.queryParameter("create"))
    }

    @Test fun monsterListAndDetailsRetainActualHungerAndMoraleBarLevels() {
        val list = parse("unnamed-list").sections.single().entries.first().details.toMap()
        assertEquals("72.5%", list["Hunger"])
        assertEquals("75.8%", list["Morale"])
        val stats = parse("slot", "&slot=1").sections.first().facts.toMap()
        assertEquals("72.5%", stats["Hunger"])
        assertEquals("75.8%", stats["Morale"])

        val document = Jsoup.parse(fixture("slot"))
        val hunger = document.selectFirst("#monster_head .msl > div:nth-child(5) img")!!
        val morale = document.selectFirst("#monster_head .msl > div:nth-child(6) img")!!
        hunger.attr("style", "width:0px")
        morale.attr("style", "width:120px")
        val limits = MonsterLabParser.parse(document.outerHtml(), HvWorld.Persistent, url("&slot=1")).sections.first().facts.toMap()
        assertEquals("0.0%", limits["Hunger"])
        assertEquals("100.0%", limits["Morale"])

        hunger.removeAttr("style")
        morale.remove()
        val unknown = MonsterLabParser.parse(document.outerHtml(), HvWorld.Persistent, url("&slot=1")).sections.first().facts.toMap()
        assertFalse(unknown.containsKey("Hunger"))
        assertFalse(unknown.containsKey("Morale"))
    }

    @Test fun unnamedMonsterRetainsTheActivationRequirementAndActualSkillsLinkWithoutInventingRename() {
        val page = parse("unnamed-slot", "&slot=59")
        assertEquals("Unnamed Celestial", page.sections.first().title)
        assertEquals("0", page.sections.first().facts.toMap()["Level"])
        assertEquals(listOf("You still have to feed this monsters enough crystals to reach powerlevel 25 and give it a name to activate it."),
            page.messages)
        assertTrue(page.forms.none { it.key == "monster-rename" })
        val skills = page.links.single { it.label == "技能" }
        assertEquals("GET", skills.action.method)
        assertEquals("59", skills.action.url.queryParameter("slot"))
        assertEquals("skills", skills.action.url.queryParameter("pane"))
        assertFalse(skills.selected)
        assertEquals("属性", page.links.single { it.selected }.label)
        assertEquals("58", page.links.single { it.label == "上一只" }.action.url.queryParameter("slot"))
        assertEquals("1", page.links.single { it.label == "下一只" }.action.url.queryParameter("slot"))
    }

    @Test fun bazaarDispatchPreservesUnnamedActivationMessageAlongsideServerFeedback() {
        val document = Jsoup.parse(fixture("unnamed-slot"))
        document.body().appendElement("div").attr("id", "messagebox_inner")
            .appendElement("p").text("Monster created.")
        val page = BazaarSectionsParser.parse(document.outerHtml(), HvRoute.MonsterLab, HvWorld.Persistent, url("&slot=59"))
        assertEquals(listOf(
            "You still have to feed this monsters enough crystals to reach powerlevel 25 and give it a name to activate it.",
            "Monster created.",
        ), page.messages)
        assertTrue(page.forms.none { it.key == "monster-rename" })
    }

    @Test fun unnamedMonsterKeepsSingleTokenCostAndLocksHigherPowerlevelUpgrades() {
        val page = parse("unnamed-slot", "&slot=59")
        val chaos = page.sections.single { it.title == "Chaos" }.entries
        val scavenging = chaos.single { it.title == "Scavenging" }
        assertEquals("0 / 20", scavenging.details.toMap()["等级"])
        assertEquals("1 / 127 Chaos Tokens", scavenging.details.toMap()["下次消耗 / 库存"])
        assertEquals(listOf("1", "5", "10"), scavenging.choices.map { it.fields.toMap()["chaos_count"] })
        assertTrue(scavenging.choices.all { it.fields.toMap()["chaos_upgrade"] == "affect" })
        assertEquals(1, chaos.count { it.enabled })
        val fortitude = chaos.single { it.title == "Fortitude" }
        assertTrue(fortitude.moreDetails.any { it.second == "Next upgrade available at powerlevel 5" })
        val crystal = page.sections.flatMap { it.entries }.filter { it.key.startsWith("crystal-") }
        assertEquals(12, crystal.size)
        assertTrue(crystal.all { it.enabled && it.choices.size == 3 })
    }

    @Test fun accuracyPercentageKeepsItsContextSeparateFromTheChaosUpgrade() {
        val page = parse("unnamed-slot", "&slot=59")
        val stats = page.sections.flatMap { it.facts }.toMap()
        assertEquals("0%", stats["命中率"])
        assertFalse(stats.containsKey("Accuracy"))
        assertTrue(page.sections.single { it.title == "Chaos" }.entries.any { it.title == "Accuracy" })
    }

    @Test fun actualUnnamedDeleteUsesItsRenderedControlFormAndCapturedConfirmation() {
        val action = parse("unnamed-slot", "&slot=59").actions.single()
        assertEquals("删除怪物", action.label)
        assertEquals("POST", action.method)
        assertEquals("59", action.url.queryParameter("slot"))
        assertEquals(listOf("delete_monster" to "fixture-delete-token"), action.fields)
        val confirmation = requireNotNull(action.confirmation)
        assertTrue(confirmation.contains("Unnamed Celestial"))
        assertTrue(confirmation.contains("This action cannot be reversed."))

        val withoutControl = Jsoup.parse(fixture("unnamed-slot"))
        withoutControl.select("#monsterstats_rendel img").remove()
        assertTrue(MonsterLabParser.parse(withoutControl.outerHtml(), HvWorld.Persistent, url("&slot=59")).actions.isEmpty())
        val withoutHandler = Jsoup.parse(fixture("unnamed-slot"))
        withoutHandler.select("script").remove()
        assertTrue(MonsterLabParser.parse(withoutHandler.outerHtml(), HvWorld.Persistent, url("&slot=59")).actions.isEmpty())
        val foreignTarget = Jsoup.parse(fixture("unnamed-slot"))
        foreignTarget.selectFirst("#delete_form")!!.attr("action", "https://example.test/?s=Bazaar&ss=ml&slot=59")
        assertTrue(MonsterLabParser.parse(foreignTarget.outerHtml(), HvWorld.Persistent, url("&slot=59")).actions.isEmpty())
    }

    @Test fun slotNavigationRetainsTheActualSlotAndPaneUrls() {
        val stats = parse("slot", "&slot=1")
        assertEquals("属性", stats.links.single { it.selected }.label)
        assertEquals("58", stats.links.single { it.label == "上一只" }.action.url.queryParameter("slot"))
        assertEquals("2", stats.links.single { it.label == "下一只" }.action.url.queryParameter("slot"))
        assertNull(stats.links.single { it.label == "怪物列表" }.action.url.queryParameter("slot"))
        val skills = parse("skills", "&slot=1&pane=skills")
        assertEquals("技能", skills.links.single { it.selected }.label)
        assertEquals("skills", skills.links.single { it.label == "上一只" }.action.url.queryParameter("pane"))
        assertEquals("skills", skills.links.single { it.label == "下一只" }.action.url.queryParameter("pane"))
    }

    @Test fun slotRenamingUsesTheActualFormAndOnlyAvailableFoodControlSubmits() {
        val page = parse("slot", "&slot=1")
        val rename = page.forms.single { it.key == "monster-rename" }
        assertEquals("Fixture Monster 1", rename.fields.single().value)
        val submitted = rename.submit(rename.actions.single(), mapOf("rename_monster" to "New Monster"))
        assertEquals(listOf("rename_monster" to "New Monster"), submitted.fields)
        assertEquals("1", submitted.url.queryParameter("slot"))
        assertTrue(rename.validationError(rename.actions.single(), mapOf("rename_monster" to "")) != null)
        val entries = page.sections.flatMap { it.entries }
        val food = entries.single { it.key == "feed-feedmonster" }
        assertEquals("food", food.action!!.fields.toMap()["food_action"])
        assertTrue(food.details.any { it.second.contains("18271") })
        val pills = entries.single { it.key == "feed-drugmonster" }
        assertFalse(pills.enabled)
        assertNull(pills.action)
        assertTrue(page.actions.none { it.fields.any { field -> field.first == "delete_monster" } })
    }

    @Test fun nameableNewMonsterUsesAnEmptyInitialNameAndItsActualRenameForm() {
        val page = parse("unnamed-nameable", "&slot=59")
        assertEquals("28", page.sections.first().facts.toMap()["Level"])
        assertEquals(listOf("You still have to give this monster a name to activate it."), page.messages)
        val rename = page.forms.single { it.key == "monster-rename" }
        assertEquals("", rename.fields.single().value)
        assertTrue(rename.validationError(rename.actions.single(), mapOf("rename_monster" to "")) != null)
        val action = rename.submit(rename.actions.single(), mapOf("rename_monster" to "Named Celestial"))
        assertEquals("POST", action.method)
        assertEquals("59", action.url.queryParameter("slot"))
        assertEquals(listOf("rename_monster" to "Named Celestial"), action.fields)
    }

    @Test fun newCelestialSkillEditsOnlyItsNameAndPreservesFixedTypeAndDamage() {
        val page = parse("unnamed-skills", "&slot=59&pane=skills")
        val form = page.forms.single()
        assertEquals("技能", page.links.single { it.selected }.label)
        assertEquals(listOf("skill_name_0"), form.fields.map { it.name })
        assertEquals("", form.fields.single().value)
        assertEquals(30, form.fields.single().maxLength)
        assertTrue(form.facts.any { it.first == "Judgement Strike" && it.second.contains("Physical") &&
            it.second.contains("Slashing") && it.second.contains("50% MP") })
        val action = form.submit(form.actions.single(), mapOf("skill_name_0" to "Test Strike"))
        assertEquals("POST", action.method)
        assertEquals("59", action.url.queryParameter("slot"))
        assertEquals("skills", action.url.queryParameter("pane"))
        assertEquals(mapOf("update_skills" to "1", "skill_type_0" to "p", "skill_elem_0" to "patk", "skill_name_0" to "Test Strike"),
            action.fields.toMap())
    }

    @Test fun crystalUpgradesKeepDisabledStatsAndUseOnlyTheCapturedBatchCounts() {
        val entries = parse("slot", "&slot=1").sections.flatMap { it.entries }.filter { it.key.startsWith("crystal-") }
        assertEquals(12, entries.size)
        assertEquals(4, entries.count { it.enabled })
        assertFalse(entries.single { it.title == "Strength" }.enabled)
        val endurance = entries.single { it.title == "Endurance" }
        assertEquals(listOf("1", "5", "10"), endurance.choices.map { it.fields.toMap()["crystal_count"] })
        assertTrue(endurance.choices.all { it.fields.toMap()["crystal_upgrade"] == "pa_end" })
        assertEquals("24183 / 35226", endurance.details.toMap()["下次消耗 / 库存"])
        assertTrue(endurance.choices.last().confirmation!!.contains("下一次升级"))

        val document = Jsoup.parse(fixture("slot"))
        document.select("script").remove()
        val withoutBatchHandlers = MonsterLabParser.parse(document.outerHtml(), HvWorld.Persistent, url("&slot=1"))
        assertEquals(listOf("1"), withoutBatchHandlers.sections.flatMap { it.entries }.single { it.title == "Endurance" }
            .choices.map { it.fields.toMap()["crystal_count"] })
    }

    @Test fun chaosUpgradesRetainRanksCostsAndDisabledMaximum() {
        val chaos = parse("slot", "&slot=1").sections.single { it.title == "Chaos" }.entries
        assertEquals(12, chaos.size)
        assertFalse(chaos.first().enabled)
        assertTrue(chaos.first().choices.isEmpty())
        assertEquals("20 / 20", chaos.first().details.toMap()["等级"])
        val health = chaos.single { it.choices.firstOrNull()?.fields?.toMap()?.get("chaos_upgrade") == "health" }
        assertEquals("9 / 20", health.details.toMap()["等级"])
        assertEquals("10 / 127 Chaos Tokens", health.details.toMap()["下次消耗 / 库存"])
        assertEquals(listOf("1", "5", "10"), health.choices.map { it.fields.toMap()["chaos_count"] })
        assertTrue(health.moreDetails.any { it.second.contains("5%") })
    }

    @Test fun skillSaveKeepsFixedHiddenPropertiesAndSubmitsAllEditableNamesAndSelections() {
        val page = parse("skills", "&slot=1&pane=skills")
        val form = page.forms.single()
        assertEquals(6, form.fields.size)
        assertEquals(setOf("skill_name_0", "skill_name_200", "skill_elem_200", "skill_name_400", "skill_type_400", "skill_elem_400"), form.fields.map { it.name }.toSet())
        assertTrue(form.fields.filter { it.name.startsWith("skill_name_") }.all { it.value == "" && it.maxLength == 30 })
        assertEquals(listOf("prcg", "slsh"), form.fields.single { it.name == "skill_elem_200" }.options.map { it.first })
        assertEquals(listOf("p", "m"), form.fields.single { it.name == "skill_type_400" }.options.map { it.first })
        assertEquals(listOf("dark", "phys"), form.fields.single { it.name == "skill_elem_400" }.options.map { it.first })
        val values = form.fields.associate { it.key to it.value } + mapOf("skill_name_400" to "Test Skill", "skill_type_400" to "m", "skill_elem_400" to "phys")
        val action = form.submit(form.actions.single(), values)
        assertEquals("POST", action.method)
        assertEquals("skills", action.url.queryParameter("pane"))
        assertEquals(10, action.fields.size)
        assertEquals("1", action.fields.toMap()["update_skills"])
        assertEquals("p", action.fields.toMap()["skill_type_0"])
        assertEquals("patk", action.fields.toMap()["skill_elem_0"])
        assertEquals("p", action.fields.toMap()["skill_type_200"])
        assertEquals("", action.fields.toMap()["skill_name_0"])
        assertEquals("Test Skill", action.fields.toMap()["skill_name_400"])
        assertEquals("m", action.fields.toMap()["skill_type_400"])
        assertEquals("phys", action.fields.toMap()["skill_elem_400"])
        assertTrue(form.facts.any { it.first == "Shadow Blade" && it.second.contains("120") && it.second.contains("100% SP") })
    }

    @Test fun onlyRenderedPropertySelectorsCanEditTheirOwnHiddenField() {
        val document = Jsoup.parse(fixture("skills"))
        document.selectFirst("#skill_display_elem_200")!!.removeAttr("onclick")
        document.selectFirst("#skill_select_type_400 > div")!!.attr("onclick", "set_property(this, 'type_0', 'm')")
        val form = MonsterLabParser.parse(document.outerHtml(), HvWorld.Persistent, url("&slot=1&pane=skills")).forms.single()
        assertTrue(form.fields.none { it.name == "skill_elem_200" })
        assertEquals(listOf("m"), form.fields.single { it.name == "skill_type_400" }.options.map { it.first })
        val action = form.submit(form.actions.single(), form.fields.associate { it.key to it.value })
        assertEquals("prcg", action.fields.toMap()["skill_elem_200"])
    }

    @Test fun missingControlsAndForeignFormTargetsNeverBecomeActions() {
        val document = Jsoup.parse(fixture("slot"))
        document.selectFirst("#rename_form")!!.attr("action", "https://example.test/?s=Bazaar&ss=ml&slot=1")
        document.selectFirst("#food_form")!!.attr("action", "https://alt.hentaiverse.org/isekai/?s=Bazaar&ss=ml&slot=1")
        document.selectFirst("#crystal_form")!!.attr("action", "?s=Character&ss=ch")
        val page = MonsterLabParser.parse(document.outerHtml(), HvWorld.Persistent, url("&slot=1"))
        assertTrue(page.forms.none { it.key == "monster-rename" })
        assertTrue(page.sections.flatMap { it.entries }.filter { it.key.startsWith("feed-") || it.key.startsWith("crystal-") }
            .all { !it.enabled && it.action == null && it.choices.isEmpty() })
        val skills = Jsoup.parse(fixture("skills"))
        skills.select("#skillform > img").remove()
        assertTrue(MonsterLabParser.parse(skills.outerHtml(), HvWorld.Persistent, url("&slot=1&pane=skills")).forms.isEmpty())
    }

    private fun parse(name: String, query: String = "") = MonsterLabParser.parse(fixture(name), HvWorld.Persistent, url(query))
    private fun url(query: String) = (HvRoute.MonsterLab.url(HvWorld.Persistent).toString() + query).toHttpUrl()
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/monster-lab/$name.html")).readText()
}
