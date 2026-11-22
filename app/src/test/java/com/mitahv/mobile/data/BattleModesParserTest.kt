package com.mitahv.mobile.data

import com.mitahv.mobile.model.BattleModesPage
import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvWorld
import com.mitahv.mobile.model.HvSite
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleModesParserTest {
    @Test fun renamedItemWorldEquipmentUsesAliasAndPreservesOriginalName() {
        val original = "Legendary Ethereal Dagger of Slaughter"
        val alias = "My Legendary Blade"
        val dictionary = """var dynjs_equip={"1001":{"t":"$alias","n":"$original","d":""}};"""
        val page = BattleModesParser.parse(fixture("iw"), HvRoute.ItemWorld, HvWorld.Persistent,
            supplementalScripts = listOf(dictionary)) as BattleModesPage.ItemWorld
        assertEquals(alias, page.equipment.first().name)
        assertEquals(original, page.equipment.first().originalEquipmentName)
        assertEquals("1001", page.equipment.first().start!!.fields.toMap()["eqids[]"])
        assertNull(page.equipment.last().originalEquipmentName)
    }

    @Test fun itemWorldEquipmentDetailsUseOfficialSupplementalDictionary() {
        val original = parse("iw", HvRoute.ItemWorld) as BattleModesPage.ItemWorld
        val id = original.equipment.first().id
        val script = """var dynjs_equip={"$id":{"d":"<div class='eq'><div>Legendary Test Weapon</div><div class='ex'><div>Attack Damage: +500</div><div>Strength: +20</div></div></div>"}};"""
        val page = BattleModesParser.parse(fixture("iw"), HvRoute.ItemWorld, HvWorld.Persistent,
            supplementalScripts = listOf(script)) as BattleModesPage.ItemWorld
        assertEquals("Legendary Test Weapon\nAttack Damage: +500\nStrength: +20", page.equipment.first().equipmentDetails)
        assertEquals(original.equipment.first().start, page.equipment.first().start)
    }

    @Test fun alternatePersistentBattleFormsRetainCurrentOrigin() {
        val current = HvRoute.RingOfBlood.url(HvWorld.Persistent, HvSite.Alt)
        val doc = Jsoup.parse(fixture("rb"))
        doc.selectFirst("#initform")!!.attr("action", "")
        val page = BattleModesParser.parse(doc.outerHtml(), HvRoute.RingOfBlood, HvWorld.Persistent, current) as BattleModesPage.RingOfBlood
        assertEquals(current, page.challenges.first().start!!.url)
        doc.selectFirst("#initform")!!.attr("action", current.toString())
        val explicit = BattleModesParser.parse(doc.outerHtml(), HvRoute.RingOfBlood, HvWorld.Persistent, current) as BattleModesPage.RingOfBlood
        assertEquals(current, explicit.challenges.first().start!!.url)
    }

    @Test
    fun ringOfBloodReadsCostsTokensAndExactChallengeActions() {
        val page = parse("rb", HvRoute.RingOfBlood) as BattleModesPage.RingOfBlood

        assertEquals(8, page.challenges.size)
        assertEquals("You have 188 tokens of blood.", page.tokenBalance)
        val first = page.challenges.first()
        assertEquals("Konata", first.name)
        assertEquals("1 Token", first.entryCost)
        assertEquals("Lv. 50", first.minimumLevel)
        assertEquals("1,000 C", first.clearBonus)
        assertEquals("105", first.start!!.fields.toMap()["initid"])
        assertEquals("fixture-post-token", first.start!!.fields.toMap()["postoken"])
        assertEquals(HvRoute.RingOfBlood.url(HvWorld.Persistent), first.start!!.url)
        assertEquals("Konata\n1 Token", first.start!!.confirmation)
        val cooldown = page.challenges.single { it.name == "Flying Spaghetti Monster" }
        assertEquals("Cooldown: 4H 25M", cooldown.status)
        assertNull(cooldown.start)
    }

    @Test
    fun ringOfBloodPreservesLowLevelServerEmptyMessageWithoutAChallenge() {
        val page = parse("rb-low-level", HvRoute.RingOfBlood) as BattleModesPage.RingOfBlood

        assertEquals("YOU HAVE 0 TOKENS OF BLOOD.", page.tokenBalance)
        assertEquals("There are no challenges available at your level. Check back later!", page.emptyMessage)
        assertTrue(page.challenges.isEmpty())
    }

    @Test
    fun ringOfBloodIgnoresUnnamedChallengeRows() {
        val document = Jsoup.parse(fixture("ar-low-level"))
        document.selectFirst("#arena_list td")!!.empty()
        val page = BattleModesParser.parse(document.outerHtml(), HvRoute.RingOfBlood, HvWorld.Persistent) as BattleModesPage.RingOfBlood

        assertTrue(page.challenges.isEmpty())
    }

    @Test
    fun grindfestReadsRoundsStaminaAndObservedStartId() {
        val page = parse("gr", HvRoute.GrindFest) as BattleModesPage.GrindFest

        assertEquals("1000", page.rounds)
        assertEquals("1", page.staminaCost)
        assertEquals("1", page.start!!.fields.toMap()["initid"])
        assertEquals(HvRoute.GrindFest.url(HvWorld.Persistent), page.start!!.url)
        assertTrue(page.rules.any { it.contains("small credit reward") })
    }

    @Test
    fun towerDisplaysDailyLimitsWithoutInventingAStartForDisabledSample() {
        val page = BattleModesParser.parse(fixture("tw"), HvRoute.Tower, HvWorld.Isekai) as BattleModesPage.Tower

        assertEquals("1st", page.ranking)
        assertEquals("36 (45 Rounds)", page.floor)
        assertEquals("IWBTH, Monster Level 360", page.difficulty)
        assertEquals("1 / 3", page.attempts)
        assertEquals("1 / 1", page.clears)
        assertNull(page.start)
    }

    @Test
    fun itemWorldUsesSingleEquipmentSelectionAndRealMaterialData() {
        val page = parse("iw", HvRoute.ItemWorld) as BattleModesPage.ItemWorld

        assertEquals(7, page.filters.size)
        assertTrue(page.filters.first().selected)
        assertEquals("GET", page.filters[1].action.method)
        assertEquals("weapon_2handed", page.filters[1].action.url.queryParameter("filter"))
        assertEquals(6, page.equipment.size)
        val dagger = page.equipment.first()
        assertEquals("1001", dagger.id)
        assertEquals("Dagger", dagger.group)
        assertEquals("0 / 6 / 25", dagger.tier)
        assertEquals("7", dagger.worldLevel)
        assertEquals("11", dagger.rounds)
        assertEquals("493", dagger.monsterLevel)
        assertEquals("PFUDOR", dagger.difficulty)
        assertEquals(3L, dagger.materials.single().required)
        assertEquals(74L, dagger.materials.single().owned)
        assertEquals("World Seed", dagger.materials.single().name)
        assertEquals(listOf("1001"), dagger.start!!.fields.filter { it.first == "eqids[]" }.map { it.second })
        assertEquals("PFUDOR +300%", page.equipment[2].difficulty)
        assertEquals("700", page.equipment[2].monsterLevel)
    }

    @Test
    fun itemWorldMissingActionPostsToCurrentFilterPage() {
        val currentUrl = "https://hentaiverse.org/?s=Battle&ss=iw&screen=itemworld&filter=weapon_1handed".toHttpUrl()
        val page = BattleModesParser.parse(fixture("iw"), HvRoute.ItemWorld, HvWorld.Persistent, currentUrl) as BattleModesPage.ItemWorld

        assertEquals(currentUrl, page.equipment.first().start!!.url)
        assertEquals(listOf("postoken", "eqids[]"), page.equipment.first().start!!.fields.map { it.first })
    }

    @Test
    fun notEnoughMaterialsAllowsInspectionButPreventsEntry() {
        val html = fixture("iw").replace("\"c\":74", "\"c\":2")
        val page = BattleModesParser.parse(html, HvRoute.ItemWorld, HvWorld.Persistent) as BattleModesPage.ItemWorld

        assertTrue(page.equipment.first().selectable)
        assertEquals(2L, page.equipment.first().materials.single().owned)
        assertEquals("材料不足", page.equipment.first().unavailableReason)
        assertTrue(page.equipment.all { it.start == null })
    }

    @Test
    fun disabledEquipmentAndIncompleteMetadataNeverBecomeActions() {
        val document = Jsoup.parse(fixture("iw"))
        document.selectFirst("input[id=e1001]")!!.attr("disabled", "disabled")
        val disabled = BattleModesParser.parse(document.outerHtml(), HvRoute.ItemWorld, HvWorld.Persistent) as BattleModesPage.ItemWorld
        assertFalse(disabled.equipment.first().selectable)
        assertNull(disabled.equipment.first().start)

        document.select("script").remove()
        val noMetadata = BattleModesParser.parse(document.outerHtml(), HvRoute.ItemWorld, HvWorld.Persistent) as BattleModesPage.ItemWorld
        assertEquals(6, noMetadata.equipment.size)
        assertTrue(noMetadata.equipment.all { it.start == null })
    }

    @Test
    fun missingTokenAndCrossWorldFormAreNotSubmittable() {
        for (mode in listOf("rb", "gr", "iw")) {
            val route = mapOf("rb" to HvRoute.RingOfBlood, "gr" to HvRoute.GrindFest, "iw" to HvRoute.ItemWorld).getValue(mode)
            val document = Jsoup.parse(fixture(mode))
            document.select("input[name=postoken]").remove()
            assertNoAction(BattleModesParser.parse(document.outerHtml(), route, HvWorld.Persistent))

            val external = Jsoup.parse(fixture(mode))
            external.selectFirst("form")!!.attr("action", "https://alt.hentaiverse.org/isekai/?s=Battle&ss=$mode")
            assertNoAction(BattleModesParser.parse(external.outerHtml(), route, HvWorld.Persistent))
        }
    }

    @Test
    fun unavailableTowerWorldAndUnrecognizedPagesAreRejected() {
        expectInvalid { BattleModesParser.parse(fixture("tw"), HvRoute.Tower, HvWorld.Persistent) }
        expectInvalid { BattleModesParser.parse("<div id='mainpane'>Login</div>", HvRoute.GrindFest, HvWorld.Persistent) }
        expectInvalid { BattleModesParser.parse("<div id='mainpane'><div id='battle_main'></div></div>", HvRoute.GrindFest, HvWorld.Persistent) }
        expectInvalid { BattleModesParser.parse(fixture("iw"), HvRoute.ItemWorld, HvWorld.Persistent,
            HvRoute.ItemWorld.url(HvWorld.Isekai)) }
    }

    private fun assertNoAction(page: BattleModesPage) {
        when (page) {
            is BattleModesPage.RingOfBlood -> assertTrue(page.challenges.all { it.start == null })
            is BattleModesPage.GrindFest -> assertNull(page.start)
            is BattleModesPage.ItemWorld -> assertTrue(page.equipment.all { it.start == null })
            is BattleModesPage.Tower -> assertNull(page.start)
        }
    }

    private fun parse(file: String, route: HvRoute) = BattleModesParser.parse(fixture(file), route, HvWorld.Persistent)
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/battle-modes/$name.html")).readText()
    private fun expectInvalid(action: () -> Unit) {
        var rejected = false
        try { action() } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }
}
