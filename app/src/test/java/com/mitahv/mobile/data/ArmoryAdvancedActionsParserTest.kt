package com.mitahv.mobile.data

import com.mitahv.mobile.model.HvWorld
import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.ArmoryViewMode
import com.mitahv.mobile.model.NativeAction
import com.mitahv.mobile.model.submit
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class ArmoryAdvancedActionsParserTest {
    @Test fun actualFusionUsesSingleDonorAndOriginalCostsWithoutInventingAnActionField() {
        val url = "https://hentaiverse.org/?s=Bazaar&ss=am&screen=statfuse&upgrade_eqid=303429104".toHttpUrl()
        val page = ArmoryStatFusionParser.parse(document("stat-fusion.html"), HvWorld.Persistent, url)
        val navigation = checkNotNull(BazaarSectionsParser.parse(document("stat-fusion.html").outerHtml(),
            HvRoute.Armory, HvWorld.Persistent, url).armory)
        assertEquals(ArmoryViewMode.Fusion, navigation.mode)
        assertEquals("statfuse", navigation.screen)
        assertEquals("303429104", navigation.backAction!!.url.queryParameter("eqids[]"))
        assertEquals("modify", navigation.backAction!!.url.queryParameter("screen"))
        assertEquals(1_394_176L, navigation.balance)
        val form = page.forms.single()
        val config = checkNotNull(form.armoryStatFusion)
        val values = mapOf("eqids[]:318392104" to "318392104")
        assertEquals(4, form.fields.size)
        assertEquals(2, form.fields.count { it.selectionConfirmation != null })
        assertTrue(form.singleEquipment)
        assertEquals("Legendary Tempestuous Willow Staff of Destruction", config.targetName)
        assertEquals(1_394_176L, config.balance)
        val donor = config.candidates.single { it.id == "318392104" }
        assertEquals(4_780_000L, donor.credits)
        assertEquals(17, donor.stats.sumOf { it.boost })
        assertEquals(13, donor.materials.size)
        assertTrue(checkNotNull(config.validationError(values)).startsWith("Credits 不足"))
        assertTrue(checkNotNull(config.copy(balance = 10_000_000L).validationError(values)).contains("Binding of Negation"))
        val action = form.submit(form.actions.single(), values)
        assertEquals(url, action.url)
        assertEquals("POST", action.method)
        assertEquals(listOf("postoken" to "test-armory-token", "eqids[]" to "318392104"), action.fields)
        assertEquals(2, action.confirmationChecks.size)
        assertTrue(checkNotNull(action.confirmation).contains("永久销毁"))
    }

    @Test fun actualUpgradeAndItemWorldKeepHiddenControlsAndDifferentSubmitSemantics() {
        val document = document("upgrade-available.html")
        val url = "https://hentaiverse.org/?s=Bazaar&ss=am&screen=modify&filter=equipped&eqids%5B%5D=309083529".toHttpUrl()
        val form = checkNotNull(document.selectFirst("#equipform"))
        assertFalse(form.hasAttr("action"))
        val hidden = form.select("input[type=hidden][name]").map { it.attr("name") to it.attr("value") }
        val base = NativeAction(url, fields = hidden, label = "Equipment")
        val upgrade = checkNotNull(ArmoryUpgradeParser.parse(document, base)).entries.single { it.action != null }.action!!
        assertEquals(hidden + ("action" to "upgrade"), upgrade.fields)
        assertEquals("2", upgrade.fields.toMap()["targetlevel"])
        assertTrue(checkNotNull(upgrade.confirmation).contains("25,000 Credits"))
        val itemWorld = checkNotNull(ArmoryUpgradeParser.itemWorldAction(document, HvWorld.Persistent, base))
        assertEquals("POST", itemWorld.method)
        assertEquals("Battle", itemWorld.url.queryParameter("s"))
        assertEquals("iw", itemWorld.url.queryParameter("ss"))
        assertEquals("armor_heavy", itemWorld.url.queryParameter("filter"))
        assertEquals("309083529", itemWorld.url.queryParameter("eqids[]"))
        assertEquals(hidden, itemWorld.fields)
        assertTrue(itemWorld.fields.none { it.first == "action" })
        assertTrue(checkNotNull(itemWorld.confirmation).contains("PFUDOR +150%"))
    }

    @Test fun actualBrokenPouchRepairKeepsReplacementCostsAndBlocksBothChoicesWhenBaseMaterialIsMissing() {
        val damaged = document("charm-pouch-damaged.html")
        val slot = checkNotNull(damaged.selectFirst("#charmslots .eqcharm"))
        assertTrue(ArmoryCharmParser.slotDetails(slot).any { it.second == "丝绸护符袋(T1)（损毁）" })
        val replaced = checkNotNull(document("charm-pouch-replaced-slots.html").selectFirst("#charmslots .eqcharm"))
        assertTrue(ArmoryCharmParser.slotDetails(replaced).any { it.second == "丝绸护符袋(T1)" })
        assertFalse(ArmoryCharmParser.slotDetails(replaced).any { it.second.contains("损毁") })
        val url = "https://alt.hentaiverse.org/isekai/?s=Bazaar&ss=am&screen=modify&filter=equipped&eqids[]=324260".toHttpUrl()
        val hidden = damaged.select("#equipform input[type=hidden][name]").map { it.attr("name") to it.attr("value") }
        val base = NativeAction(url, fields = hidden, label = "修理装备", confirmation = "修理这件装备")
        val form = checkNotNull(ArmoryRepairParser.parse(damaged, base))
        val config = checkNotNull(form.armoryRepair)
        assertTrue(config.needsBaseRepair)
        assertTrue(config.missingBaseMaterials)
        assertFalse(config.missingCharmMaterials)
        assertEquals("replace_charms", form.fields.single().name)
        assertTrue(form.fields.single().checked)
        val checked = mapOf("replace_charms" to "on")
        val unchecked = mapOf("replace_charms" to "")
        assertTrue(config.summary(checked).contains("Silk Charm Pouch" to "1x · 持有 7"))
        assertFalse(config.summary(unchecked).any { it.first == "Silk Charm Pouch" })
        assertTrue(config.summary(unchecked).contains("Scrap Leather" to "24x · 持有 0"))
        assertTrue(checkNotNull(config.validationError(checked)).contains("Scrap Leather"))
        assertTrue(checkNotNull(config.validationError(unchecked)).contains("Scrap Leather"))
        val action = form.actions.single()
        assertEquals("修理这件装备", action.confirmation)
        assertEquals(hidden + ("action" to "repair") + ("replace_charms" to "on"), form.submit(action, checked).fields)
        assertEquals(hidden + ("action" to "repair"), form.submit(action, unchecked).fields)
    }

    private fun document(name: String) = Jsoup.parse(checkNotNull(javaClass.classLoader?.getResource("armory/$name")).readText())
}
