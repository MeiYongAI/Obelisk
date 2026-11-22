package com.mitahv.mobile.data

import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvWorld
import com.mitahv.mobile.model.ArmoryViewMode
import com.mitahv.mobile.model.equipmentSummary
import com.mitahv.mobile.model.submit
import com.mitahv.mobile.model.validationError
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class ArmoryPagesParserTest {
    private val sources by lazy { JSONArray(resource("sources.json")) }
    private fun resource(name: String) = checkNotNull(javaClass.classLoader?.getResource("armory/$name")).readText()
    private fun page(name: String) = BazaarSectionsParser.parse(resource("$name.html"), HvRoute.Armory, HvWorld.Persistent,
        (0 until sources.length()).map { sources.getJSONObject(it) }.single { it.getString("fixture") == "$name.html" }
            .getString("url").toHttpUrl())

    @Test fun actualCharmSlotRetainsChoicesCostsAndEquipmentContext() {
        val form = page("charm-slot-empty").forms.single()
        val config = checkNotNull(form.armoryCharm)
        assertEquals(2, config.slot)
        assertEquals(56, config.maximumPoints)
        assertEquals(17, config.otherPoints)
        assertEquals(17, config.charms.size)
        assertEquals(3, config.pouches.size)
        assertEquals(listOf("charmtype", "pouchtype"), form.fields.map { it.name })
        assertEquals(listOf("0", "0"), form.fields.map { it.value })
        assertEquals("309083529", form.actions.single().url.queryParameter("eqids[]"))
        assertEquals("2", form.actions.single().url.queryParameter("charmslot"))
        assertNotNull(form.validationError(form.actions.single(), mapOf("charmtype" to "0", "pouchtype" to "0")))
    }

    @Test fun actualCharmSelectionBuildsOneNativePostWithCostsAndPreview() {
        val form = page("charm-slot-empty").forms.single()
        val config = checkNotNull(form.armoryCharm)
        val values = mapOf("charmtype" to "20011", "pouchtype" to "1")
        assertNull(form.validationError(form.actions.single(), values))
        assertEquals(setOf("Charm Points" to "28 / 56", "Lesser Fire-proof Charm" to "1x",
            "Infusion of Flames" to "11x", "Kevlar Charm Pouch" to "1x"), config.summary(values).toSet())
        assertTrue(config.details(values).any { it.second.contains("12.00") })
        val action = form.submit(form.actions.single(), values)
        assertEquals("POST", action.method)
        assertEquals("setcharm", action.fields.toMap()["action"])
        assertEquals("test-armory-token", action.fields.toMap()["postoken"])
        assertEquals(listOf("309083529"), action.fields.filter { it.first == "eqids[]" }.map { it.second })
        assertEquals("20011", action.fields.toMap()["charmtype"])
        assertEquals("1", action.fields.toMap()["pouchtype"])
        assertTrue(action.confirmation!!.contains("Infusion of Flames"))
    }

    @Test fun actualCharmRulesRejectConflictsMissingPouchAndExcessPoints() {
        val config = checkNotNull(page("charm-slot-empty").forms.single().armoryCharm)
        assertTrue(config.validationError(mapOf("charmtype" to "1001", "pouchtype" to "0"))!!.contains("Slot 1"))
        assertTrue(config.validationError(mapOf("charmtype" to "20011", "pouchtype" to "2"))!!.contains("Missing 1x Mithril Charm Pouch"))
        assertEquals("Charm Points 不足", config.validationError(mapOf("charmtype" to "21002", "pouchtype" to "0")))
        assertNotNull(config.validationError(mapOf("charmtype" to "999", "pouchtype" to "0")))
    }

    @Test fun scriptDerivedDestroyRuleIgnoresPouchAvailabilityButKeepsOriginalCpReadout() {
        // Derived state exercises hveqc.js recalc_cp/pop_setcharm; supplied slot 2 is actually empty.
        val config = checkNotNull(page("charm-slot-empty").forms.single().armoryCharm)
            .copy(currentCharm = "20011", currentPouch = "1")
        val values = mapOf("charmtype" to "0", "pouchtype" to "2")
        assertNull(config.validationError(values))
        assertEquals(listOf("Charm Points" to "19 / 56"), config.summary(values))
        assertEquals("Destroy Charm", config.label(values))
        assertTrue(config.confirmation(values).contains("护符和护符袋将被销毁"))
    }

    @Test fun scriptDerivedReplacementConsumesOnlyTheChangedComponent() {
        // Replacement states follow the saved script; they are not additional real HTML samples.
        val current = checkNotNull(page("charm-slot-empty").forms.single().armoryCharm)
            .copy(currentCharm = "20011", currentPouch = "1")
        val values = mapOf("charmtype" to "20011", "pouchtype" to "1")
        val worn = current.copy(charmWorn = true)
        assertNull(worn.validationError(values))
        assertEquals("Replace Charm", worn.label(values))
        assertTrue(worn.summary(values).any { it.first == "Lesser Fire-proof Charm" })
        assertFalse(worn.summary(values).any { it.first == "Kevlar Charm Pouch" })
        val brokenPouch = current.copy(pouchBroken = true)
        assertEquals("Repair Pouch", brokenPouch.label(values))
        assertTrue(brokenPouch.summary(values).any { it.first == "Kevlar Charm Pouch" })
        assertFalse(brokenPouch.summary(values).any { it.first == "Lesser Fire-proof Charm" })
    }

    @Test fun actualUnboundUpgradeShowsMaterialsAndKeepsServerDisabledControls() {
        val page = page("upgrade-unbound")
        val navigation = checkNotNull(page.armory)
        assertEquals(ArmoryViewMode.Detail, navigation.mode)
        assertEquals("modify", navigation.screen)
        assertEquals("weapon_2handed", navigation.filter)
        assertNull(navigation.backAction!!.url.queryParameter("eqids[]"))
        assertNull(navigation.backAction!!.url.queryParameter("charmslot"))
        assertEquals("weapon_2handed", navigation.backAction!!.url.queryParameter("filter"))
        val upgrade = page.sections.single { it.title == "Upgrade" }
        assertTrue(upgrade.facts.contains("Mid-Grade Metals" to "100x · 持有 229"))
        assertTrue(upgrade.facts.contains("High-Grade Metals" to "5x · 持有 39"))
        assertTrue(upgrade.facts.any { it.second == "45,000 Credits" })
        assertEquals(2, upgrade.entries.size)
        assertTrue(upgrade.entries.all { !it.enabled && it.action == null })
        assertTrue(upgrade.entries.first().details.any { it.second.contains("soulbound") })
        assertTrue(page.sections.single { it.title == "Charms" }.entries.all { !it.enabled && it.action == null })
        assertTrue(page.sections.single { it.title == "Stat Fusion" }.entries.all { !it.enabled && it.action == null })
        assertFalse(page.actions.any { it.fields.toMap()["action"] == "upgrade" })
        assertFalse(page.actions.any { it.url.queryParameter("s") == "Battle" })
    }

    @Test fun actualUnboundDetailOffersSoulbindingAndStorageButRejectsMissingRepairMaterials() {
        val page = page("upgrade-unbound")
        val bind = page.actions.single { it.fields.toMap()["action"] == "soulbind" }
        assertEquals("311410613", bind.fields.toMap()["eqids[]"])
        assertEquals("POST", bind.method)
        assertTrue(bind.confirmation!!.contains("500 Soul Fragments (8844)"))
        assertTrue(bind.confirmation!!.contains("permanently untradeable"))
        assertTrue(page.actions.any { it.fields.toMap()["action"] == "store" })
        assertFalse(page.actions.any { it.fields.toMap()["action"] == "repair" })
        val repair = page.forms.single { it.armoryRepair != null }
        val config = checkNotNull(repair.armoryRepair)
        assertTrue(config.summary(emptyMap()).contains("Scrap Metal" to "25x · 持有 0"))
        assertTrue(checkNotNull(repair.validationError(repair.actions.single(), emptyMap())).contains("Scrap Metal"))
    }

    @Test fun actualModifyCategoriesKeepSingleSelectionAndGetQuery() {
        val counts = mapOf("equipped" to 15, "weapon_1handed" to 40, "weapon_2handed" to 4, "weapon_staff" to 21,
            "shield" to 13, "armor_cloth" to 15, "armor_light" to 28, "armor_heavy" to 21)
        for ((filter, count) in counts) {
            val page = page("modify-$filter")
            val form = page.forms.single()
            val navigation = checkNotNull(page.armory)
            assertEquals(ArmoryViewMode.List, navigation.mode)
            assertEquals("modify", navigation.screen)
            assertEquals(filter, navigation.filter)
            assertEquals(7, navigation.operations.size)
            assertTrue(navigation.operations.all { it.action.url.queryParameter("filter") == filter })
            assertTrue(navigation.filters.all { it.action.url.queryParameter("screen") == "modify" })
            val equipment = form.fields.filter { it.name == "eqids[]" }
            assertEquals(filter, count, equipment.size)
            assertTrue(form.singleEquipment)
            val selected = equipment.first()
            val action = form.submit(form.actions.single(), mapOf(selected.key to selected.value))
            assertEquals("GET", action.method)
            assertEquals("modify", action.url.queryParameter("screen"))
            assertEquals(filter, action.url.queryParameter("filter"))
            assertEquals(listOf(selected.value), action.url.queryParameterValues("eqids[]"))
            assertTrue(action.fields.isEmpty())
        }
    }

    @Test fun actualOrganizeKeepsTriStateControlsAndRepeatedEquipmentIds() {
        val counts = mapOf("weapon_1handed" to 40, "weapon_2handed" to 4, "weapon_staff" to 21,
            "shield" to 13, "armor_cloth" to 15, "armor_light" to 28, "armor_heavy" to 21)
        for ((filter, count) in counts) {
            val form = page("organize-$filter").forms.single()
            val equipment = form.fields.filter { it.name == "eqids[]" }
            assertEquals(filter, count, equipment.size)
            assertFalse(form.singleEquipment)
            assertEquals(setOf("-1", "0", "1", "2"), form.fields.single { it.name == "set_locked" }.options.map { it.first }.toSet())
            val values = equipment.take(2).associate { it.key to it.value } +
                mapOf("set_pinned" to "1", "set_stored" to "-1", "set_locked" to "2")
            val action = form.submit(form.actions.single(), values)
            assertEquals("POST", action.method)
            assertEquals(equipment.take(2).map { it.value }, action.fields.filter { it.first == "eqids[]" }.map { it.second })
            assertEquals("-1", action.fields.toMap()["set_stored"])
            assertEquals("2", action.fields.toMap()["set_locked"])
        }
    }

    @Test fun actualRepairCategoriesRetainSelectionAndMaterialTotals() {
        val counts = mapOf("equipped" to 4, "weapon_1handed" to 19, "weapon_2handed" to 2, "weapon_staff" to 5,
            "shield" to 8, "armor_cloth" to 4, "armor_light" to 18, "armor_heavy" to 10)
        for ((filter, count) in counts) {
            val form = page("repair-$filter").forms.single()
            assertEquals(filter, count, form.fields.count { it.name == "eqids[]" })
            assertTrue(form.fields.single { it.name == "replace_charms" }.checked)
            assertEquals("repair", form.equipmentMode)
        }
        val form = page("repair-equipped").forms.single()
        val action = form.actions.single()
        assertTrue(form.validationError(action, mapOf("eqids[]:316048964" to "316048964"))!!.contains("Scrap Metal"))
        val values = mapOf("eqids[]:310252307" to "310252307", "replace_charms:on" to "on")
        assertNull(form.validationError(action, values))
        assertTrue(form.equipmentSummary(values).any { it.first == "Energy Cell" && it.second.contains("1") })
        assertTrue(form.submit(action, values).confirmation!!.contains("Featherweight Shard"))
    }

    @Test fun actualSoulbindListChecksTheCombinedFragmentCost() {
        val form = page("soulbind-weapon_1handed").forms.single()
        val equipment = form.fields.filter { it.name == "eqids[]" }
        assertEquals(30, equipment.size)
        val action = form.actions.single()
        assertNull(form.validationError(action, equipment.take(1).associate { it.key to it.value }))
        assertTrue(form.validationError(action, equipment.take(18).associate { it.key to it.value })!!.contains("Soul Fragment"))
        assertEquals(8844L, form.materials.getValue("48001").owned)
        assertTrue(action.confirmation!!.contains("永久灵魂绑定"))
    }

    @Test fun actualEmptyCategoriesDoNotOfferEmptySubmissions() {
        for (name in listOf("organize-salvaged", "repair-new", "repair-salvaged")) {
            val page = page(name)
            assertEquals(ArmoryViewMode.List, page.armory!!.mode)
            assertTrue(name, page.forms.isEmpty())
            assertTrue(name, page.sections.any { it.emptyText.contains("no available equipment") })
        }
        val summary = page("organize-salvaged").armory!!.summary
        assertEquals(listOf("Inventory Capacity", "Storage Capacity"), summary.map { it.first })
    }
}
