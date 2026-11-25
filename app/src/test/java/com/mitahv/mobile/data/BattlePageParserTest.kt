package com.mitahv.mobile.data

import com.mitahv.mobile.model.BattleScene
import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvSite
import com.mitahv.mobile.model.HvWorld
import org.json.JSONObject
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BattlePageParserTest {
    @Test fun actionLogHandlesPlainTextMarkupEntitiesAndBitmapGlyphsLikeTheBattlePage() {
        val page = BattlePageParser.parseBattle(activeFixture("ring-of-blood"))
        val messages = listOf("  You\t hit   for\u00a0100.\n", "Lost &amp; found", "One<br>two",
            "<span class='c3a'></span><span class='c3b'></span>", "Plain \u200Btext.")
        val batch = org.json.JSONArray().apply { messages.forEach { put(JSONObject().put("t", it)) } }
        val updated = applyTurn(page, JSONObject().put("textlog", batch))
        assertEquals(listOf("Plain text.", "AB", "One two", "Lost & found", "You hit for 100."),
            updated.log.take(messages.size))
    }

    @Test fun actionResponseParsesFromOneBattleDocumentWithoutGenericPagePipeline() {
        val page = BattlePageParser.parseBattle(activeFixture("ring-of-blood"))
        val updatedVitals = Jsoup.parse(activeFixture("encounter")).selectFirst("#pane_vitals")!!.html()
        val updated = BattlePageParser.parseActionResponse(
            page,
            JSONObject().put("pane_vitals", updatedVitals),
            HvWorld.Persistent,
            HvRoute.Battle.url(HvWorld.Persistent, HvSite.Main),
        )

        assertEquals(listOf("22412", "5005", "2168", "0"), requireNotNull(updated).playerVitals.map { it.value })
        assertFalse(BattlePageParser.hasActionUpdate(JSONObject()))
    }

    @Test fun sceneSurvivesLogEvictionAndCanBeRecognizedFromAnAjaxRound() {
        val ring = BattlePageParser.parseBattle(activeFixture("ring-of-blood"))
        assertEquals(BattleScene.Colosseum, ring.scene)
        val log = org.json.JSONArray().apply { repeat(101) { put(JSONObject().put("t", "An ordinary turn")) } }
        val next = applyTurn(ring, JSONObject().put("textlog", log))
        assertFalse(next.log.any { it.startsWith("Initializing") }); assertEquals(ring.scene, next.scene)
        assertEquals(1 to 1, next.round.current to next.round.total)
        val init = org.json.JSONArray().put(JSONObject().put("t", "Initializing Item World (Round 2 / 5) ..."))
        val itemWorld = applyTurn(next.copy(scene = null), JSONObject().put("textlog", init))
        assertEquals(BattleScene.Item, itemWorld.scene)
        assertEquals(2 to 5, itemWorld.round.current to itemWorld.round.total)
    }

    @Test fun singleRoundEncounterRequiresAnIdentifiableStart() {
        val page = BattlePageParser.parseBattle(activeFixture("encounter"))
        assertEquals(null to null, page.round.current to page.round.total)
        val init = org.json.JSONArray().put(JSONObject().put("t", "Initializing random encounter ..."))
        val encounter = applyTurn(page, JSONObject().put("textlog", init))
        assertEquals(1 to 1, encounter.round.current to encounter.round.total)
    }

    @Test fun consecutivePaneUpdatesMatchFullPageMergeAndReuseUnchangedLists() {
        var html = activeFixture("ring-of-blood")
        var current = BattlePageParser.parseBattle(html)
        val other = Jsoup.parse(activeFixture("encounter"))
        val panes = JSONObject()
        for (id in listOf("pane_vitals", "pane_effects", "pane_action", "pane_monster", "pane_quickbar",
                "table_magic", "table_skills", "pane_item")) {
            panes.put(id, other.getElementById(id)!!.html())
        }
        for (turn in listOf(panes, JSONObject().put("exp", 311).put("textlog", org.json.JSONArray()
                .put(JSONObject().put("t", "First")).put(JSONObject().put("t", "<b>Second</b>"))),
                JSONObject().put("pane_completion", "").put("pane_monster", ""), JSONObject().put("exp", 1))) {
            val next = applyTurn(current, turn)
            html = BattlePageParser.mergeActionResponse(html, turn)
            assertEquals(BattlePageParser.parseBattle(html).copy(encounterKey = next.encounterKey, turnKey = next.turnKey), next)
            if (!turn.has("table_magic")) org.junit.Assert.assertSame(current.spells, next.spells)
            current = next
        }
    }

    @Test fun suppliedUtilitarianVitalsReadBothHealthIdsAndUpdateFromTheResponsePane() {
        val active = Jsoup.parse(activeFixture("ring-of-blood"))
        active.selectFirst("#dvrhd")!!.attr("id", "dvrhb")
        val before = BattlePageParser.parseBattle(active.outerHtml())
        assertEquals(listOf("25080", "5576", "2168", "0"), before.playerVitals.map { it.value })
        assertEquals(listOf(1f, 1f, 1f), before.playerVitals.take(3).map { it.fraction })

        val updatedVitals = Jsoup.parse(activeFixture("encounter")).selectFirst("#pane_vitals")!!.html()
        val updated = applyTurn(before, JSONObject().put("pane_vitals", updatedVitals))
        assertEquals(listOf("22412", "5005", "2168", "0"), updated.playerVitals.map { it.value })
        assertEquals(370f / 414f, updated.playerVitals[0].fraction)
        assertEquals(372f / 414f, updated.playerVitals[1].fraction)

        val defeat = requireNotNull(javaClass.getResource("/battle-completion/item-world-defeat.html")).readText()
        val defeatVitals = Jsoup.parse(defeat).selectFirst("#pane_vitals")!!.html()
        val defeated = applyTurn(updated, JSONObject().put("pane_vitals", defeatVitals))
        assertTrue(defeated.playerVitals.isEmpty())
    }

    @Test fun standardVitalIdsUseTheirOwnBarWidthsAndAcceptHealthReadoutChanges() {
        // Standard selector/width variation is taken from the supplied hvb.css
        // (lines 23-43), not presented as a captured Standard battle response.
        val standard = Jsoup.parse(activeFixture("ring-of-blood"))
        standard.select("#dvbc, #dvrc").remove()
        listOf("dvbh", "dvbm", "dvbs", "dvrhd", "dvrm", "dvrs").forEach { id ->
            standard.selectFirst("#$id")!!.attr("id", id.drop(1))
        }
        standard.selectFirst("#vbh img")!!.attr("style", "width:496px")
        standard.selectFirst("#vbm img")!!.attr("style", "width:207px")
        standard.selectFirst("#vbs img")!!.attr("style", "width:207px")
        val before = BattlePageParser.parseBattle(standard.outerHtml())
        assertEquals(listOf("25080", "5576", "2168"), before.playerVitals.map { it.value })
        assertEquals(listOf(1f, 1f, 1f), before.playerVitals.map { it.fraction })

        standard.selectFirst("#vrhd")!!.attr("id", "vrhb").text("12540")
        standard.selectFirst("#vbh img")!!.attr("style", "width:248px")
        standard.selectFirst("#vrm")!!.text("2788")
        standard.selectFirst("#vbm img")!!.attr("style", "width:103.5px")
        val updated = applyTurn(before, JSONObject().put("pane_vitals", standard.selectFirst("#pane_vitals")!!.html()))
        assertEquals(listOf("12540", "2788", "2168"), updated.playerVitals.map { it.value })
        assertEquals(listOf(0.5f, 0.5f, 1f), updated.playerVitals.map { it.fraction })
    }

    @Test fun capturedPresetVitalsReadTheThreeValuesAndDoNotInventMissingOvercharge() {
        val capturedMarkup = """
            <div id="battle_main">
              <div id="pane_vitals" class="nosel">
                <div id="vbh"><div><img src="bar_dgreen.png" style="width:496px"></div></div>
                <div id="vbm"><div><img src="bar_blue.png" style="width:184px"></div></div>
                <div id="vbs"><div><img src="bar_red.png" style="width:207px"></div></div>
                <div id="vrhd">25095</div><div id="vrm">4963</div><div id="vrs">2168</div>
              </div>
              <script>var battle_token = "fixture";</script>
            </div>
        """.trimIndent()

        val vitals = BattlePageParser.parseBattle(capturedMarkup).playerVitals
        assertEquals(listOf("25095", "4963", "2168"), vitals.map { it.value })
        assertEquals(listOf(1f, 184f / 207f, 1f), vitals.map { it.fraction })
        assertFalse(vitals.any { it.name == "Overcharge" })
    }

    @Test fun standardOverchargeIsReadFromThePresetDotIndicatorWhenPresent() {
        val standard = Jsoup.parse(activeFixture("ring-of-blood"))
        standard.select("#dvbc, #dvrc").remove()
        listOf("dvbh", "dvbm", "dvbs", "dvrhd", "dvrm", "dvrs").forEach { id ->
            standard.selectFirst("#$id")!!.attr("id", id.drop(1))
        }
        standard.selectFirst("#pane_vitals")!!.append(
            "<div id=\"vcp\"><div><div></div><div></div><div id=\"vcr\"></div></div></div>",
        )

        val overcharge = BattlePageParser.parseBattle(standard.outerHtml()).playerVitals.last()
        assertEquals("Overcharge", overcharge.name)
        assertEquals("2.5", overcharge.value)
        assertEquals(0.25f, overcharge.fraction)
    }

    @Test fun suppliedRingOfBloodIncludesEffectsOverchargeAndDisabledSkills() {
        val page = BattlePageParser.parseBattle(activeFixture("ring-of-blood"))
        assertEquals("fixture-battle-token", page.token)
        assertEquals(4, page.playerVitals.size)
        assertEquals("Overcharge", page.playerVitals.last().name)
        assertEquals("0", page.playerVitals.last().value)
        assertEquals(5, page.playerEffects.size)
        assertEquals("autocast", page.playerEffects.first().duration)
        assertEquals(5, page.skills.size)
        assertEquals(2, page.skills.count { it.enabled })
        assertFalse(page.skills.single { it.name == "FUS RO DAH" }.enabled)
        assertEquals(4, page.skills.single { it.name == "FUS RO DAH" }.chargeCost)
        assertEquals("sfus", page.skills.single { it.name == "FUS RO DAH" }.iconKey)
        assertEquals(10, page.skills.single { it.name == "FUS RO DAH" }.cooldown)
        assertEquals(6, page.controls.size)
        assertTrue(page.controls.all { it.enabled })
        assertTrue(page.enemies.all { it.targetable })
        assertTrue(page.enemies.all { it.magicFraction != null && it.spiritFraction != null })
        assertFalse(page.canContinue)
    }

    @Test fun suppliedEncounterRetainsUnavailableCureAndUsedPotion() {
        val page = BattlePageParser.parseBattle(activeFixture("encounter"))
        assertEquals(10, page.enemies.size)
        assertEquals(16, page.quickActions.size)
        assertEquals("0530", page.quickActions.single { it.name == "Cure" }.iconKey)
        assertEquals("0130", page.spells.single { it.name == "Fiery Blast" }.iconKey)
        assertTrue(page.items.all { it.iconKey == null })
        assertFalse(page.quickActions.single { it.name == "Cure" }.enabled)
        assertFalse(page.spells.single { it.name == "Cure" }.enabled)
        assertEquals(64, page.spells.single { it.name == "Cure" }.manaCost)
        assertEquals(25, page.items.size)
        assertFalse(page.items.single { it.name == "Health Potion" }.enabled)
        assertTrue(page.items.single { it.name == "Mana Potion" }.enabled)
        assertTrue(page.enemies.first().effects.any { it.name == "Freezing Limbs" && it.duration == "3" })
        assertEquals("regen", page.playerEffects.first { it.name == "Regen" }.iconKey)
        assertEquals("coldslow", page.enemies.first().effects.first { it.name == "Freezing Limbs" }.iconKey)
        assertTrue(page.spells.any { it.group == "Damage" })
        assertTrue(page.items.any { it.group == "Scrolls" })
    }

    @Test fun cooldownCountsOnlyObservedTurnsAndZeroTurnItemsKeepTheCount() {
        val doc = Jsoup.parse(activeFixture("encounter"))
        doc.selectFirst("#textlog tbody")!!.html("""
            <tr><td>You use Health Potion.</td></tr><tr><td class="tls"><hr></td></tr>
            <tr><td>You cast Cure.</td></tr>
        """.trimIndent())
        val page = BattlePageParser.parseBattle(doc)
        assertFalse(page.spells.first { it.name == "Cure" }.enabled)
        assertEquals(2, page.remainingCooldowns["Cure"])
        fun log(text: String) = JSONObject().put("textlog", org.json.JSONArray().put(JSONObject().put("t", text)))
        val next = applyTurn(page, log("You hit Target for 10 damage."))
        assertEquals(1, next.remainingCooldowns["Cure"])
        assertTrue(applyTurn(next, log("Cooldown expired for Cure")).remainingCooldowns.isEmpty())
        assertTrue(applyTurn(next, log("Initializing Item World (Round 2 / 5) ...")).remainingCooldowns.isEmpty())
        assertTrue(BattlePageParser.parseBattle(activeFixture("encounter")).remainingCooldowns.isEmpty())
    }

    @Test fun newMonsterEffectRetainsItsIconKeyAndExpiresWhenThePaneClears() {
        val page = BattlePageParser.parseBattle(activeFixture("encounter"))
        val monsters = Jsoup.parse(activeFixture("encounter")).selectFirst("#pane_monster")!!
        val icon = monsters.selectFirst(".btm6 img[onmouseover]")!!
        icon.attr("src", "/y/e/another_status.png?v=2")
        val updated = applyTurn(page, JSONObject().put("pane_monster", monsters.html()))
        val effect = updated.enemies.first().effects.first()
        assertEquals("another_status", effect.iconKey)
        monsters.select(".btm6").empty()
        assertTrue(applyTurn(updated, JSONObject().put("pane_monster", monsters.html())).enemies.all { it.effects.isEmpty() })
        assertEquals(page.playerEffects, updated.playerEffects)
    }

    @Test fun battleItemMetadataSurvivesPaneRefreshWhenScriptsAreRetained() {
        val script = """var dynjs_itemc={"11191":{"n":"Health Draught","q":"Provides <b>restoration</b>."}};"""
        val page = BattlePageParser.parseBattle(activeFixture("ring-of-blood"), listOf(script))
        assertEquals("Provides restoration.", page.items.single { it.name == "Health Draught" }.description)
        val pane = Jsoup.parse(activeFixture("ring-of-blood")).selectFirst("#pane_item")!!.html()
        val next = applyTurn(page, JSONObject().put("pane_item", pane))
        assertEquals(page.items.single { it.name == "Health Draught" }.description, next.items.single { it.name == "Health Draught" }.description)
        val enriched = next.copy(itemText = next.itemText +
            ("11191" to com.mitahv.mobile.model.BattleItemText("Health Draught", "New description")))
        assertEquals("New description", applyTurn(enriched, JSONObject().put("pane_item", pane))
            .items.single { it.name == "Health Draught" }.description)
    }

    @Test fun repeatedPanesReuseListsButStillAcknowledgeTurnsAndChangedMarkup() {
        val doc = Jsoup.parse(activeFixture("encounter"))
        val original = BattlePageParser.parseBattle(doc).copy(remainingCooldowns = mapOf("Cure" to 2))
        val monsterHtml = doc.getElementById("pane_monster")!!.html()
        val vitalsHtml = doc.getElementById("pane_vitals")!!.html()
        val message = "You hit Target for 10 damage."
        val response = JSONObject().put("pane_monster", monsterHtml).put("pane_vitals", vitalsHtml)
            .put("textlog", org.json.JSONArray().put(JSONObject().put("t", message)))
        val first = applyTurn(original, response)
        val second = applyTurn(first, response)
        assertSame(first.enemies, second.enemies)
        assertSame(first.playerVitals, second.playerVitals)
        assertNotSame(first.turnKey, second.turnKey)
        assertEquals(listOf(message, message), second.log.take(2))
        assertEquals(1, first.remainingCooldowns["Cure"])
        assertTrue(second.remainingCooldowns.isEmpty())

        val reset = second.copy(enemies = emptyList())
        assertEquals(first.enemies, applyTurn(reset, response).enemies)
        val renamedHtml = monsterHtml.replace(second.enemies.first().name, "Updated enemy")
        val changed = applyTurn(second, JSONObject().put("pane_monster", renamedHtml))
        assertEquals("Updated enemy", changed.enemies.first().name)
        assertEquals(first.enemies, applyTurn(changed, response).enemies)
        assertTrue(applyTurn(changed, JSONObject().put("pane_monster", "")).enemies.isEmpty())
        assertTrue(applyTurn(second, JSONObject().put("pane_completion", "")).isComplete)
    }

    @Test fun completionResponseStopsActionsButOnlyKnownControlEnablesContinue() {
        for (content in listOf("<img id='btcp' onclick='battle.battle_continue()' src='/y/battle/continue.png'>", "")) {
            val merged = BattlePageParser.mergeActionResponse(activeFixture("ring-of-blood"), JSONObject().put("pane_completion", content))
            val completed = BattlePageParser.parseBattle(merged)
            assertTrue(completed.isComplete)
            assertEquals(content.isNotBlank(), completed.canContinue)
            val refreshed = BattlePageParser.mergeActionResponse(merged, JSONObject())
            assertFalse(BattlePageParser.parseBattle(refreshed).canContinue)
            assertFalse(BattlePageParser.parseBattle(refreshed).isComplete)
        }
    }

    @Test fun logBatchMatchesOfficialPrependOrderAndServerDisabledTargetsStayDisabled() {
        val log = org.json.JSONArray().put(JSONObject().put("t", "First"))
            .put(JSONObject().put("t", "Second")).put(JSONObject().put("t", "Third"))
        val page = BattlePageParser.parseBattle(BattlePageParser.mergeActionResponse(activeFixture("ring-of-blood"), JSONObject().put("textlog", log)))
        assertEquals(listOf("Third", "Second", "First"), page.log.take(3))
        val doc = Jsoup.parse(activeFixture("ring-of-blood"))
        doc.selectFirst("#mkey_1")!!.removeAttr("onclick")
        doc.selectFirst("#ckey_focus")!!.removeAttr("onclick")
        val disabled = BattlePageParser.parseBattle(doc.outerHtml())
        assertFalse(disabled.enemies.first().targetable)
        assertFalse(disabled.controls.single { it.mode == "focus" }.enabled)
    }

    @Test
    fun parsesLiveBattleStateAndActionTypes() {
        val page = BattlePageParser.parseBattle(battleHtml)

        assertEquals("runtime-token", page.token)
        assertEquals("12000", page.playerVitals.first().value)
        assertEquals(0.5f, page.playerVitals.first().fraction)
        assertEquals("Target", page.enemies.single().name)
        assertEquals(0.5f, page.enemies.single().healthFraction)
        assertEquals("Cure", page.quickActions.single().name)
        assertFalse(page.quickActions.single().targetsEnemy)
        assertEquals("Fire", page.spells.single().name)
        assertTrue(page.spells.single().targetsEnemy)
    }

    @Test
    fun mergesBattleResponsePanesAndLog() {
        val update = JSONObject()
            .put("pane_monster", "<div id='mkey_1'><div class='btm2'>99</div><div class='btm3'>Target</div><div class='chbd'><img alt='health' style='width:24px'></div></div>")
            .put("textlog", org.json.JSONArray().put(JSONObject().put("t", "You strike the target.").put("c", "l")))
            .put("exp", 123.0)
        val html = BattlePageParser.mergeActionResponse(battleHtml, update)
        val page = BattlePageParser.parseBattle(html)

        assertEquals(0.2f, page.enemies.single().healthFraction)
        assertEquals("You strike the target.", page.log.first())
        assertEquals(123f, page.experienceBarWidth)
        assertTrue(page.completion.isEmpty())
    }

    @Test
    fun parsesChallengeTokenAndStartControl() {
        val page = BattlePageParser.parseArena(
            """
            <table id="arena_list"><tbody>
              <tr><th>Challenge</th><th>Highest Clear</th></tr>
              <tr><td>Sample Trial</td><td>PFUDOR</td><td>Lv. 20</td><td>5</td><td>X1.0</td>
                <td>1</td><td>100 C</td><td><img onclick="init_battle(205,1)"></td></tr>
            </tbody></table>
            <form id="initform"><input name="postoken" value="fresh-post-token"></form>
            """.trimIndent(),
        )

        assertEquals("fresh-post-token", page.postToken)
        assertEquals(205, page.challenges.single().id)
        assertTrue(page.challenges.single().available)
        assertEquals("1", page.challenges.single().entryCost)
    }

    @Test
    fun readsBitmapArenaChallengeFromLowLevelAccount() {
        val page = BattlePageParser.parseArena(fixture("ar-low-level"))
        val challenge = page.challenges.single()

        assertEquals("FIRST BLOOD", challenge.name)
        assertEquals("-", challenge.highestClear)
        assertEquals("LV. 1", challenge.minimumLevel)
        assertEquals("2", challenge.rounds)
        assertEquals("X1.0", challenge.experienceMultiplier)
        assertEquals("100 C", challenge.clearBonus)
        assertEquals(1, challenge.id)
        assertTrue(challenge.available)
        assertTrue(page.emptyMessage.isEmpty())
    }

    @Test
    fun fullWidthEmptyRowIsMessageRatherThanChallenge() {
        val page = BattlePageParser.parseArena(fixture("rb-low-level"))

        assertTrue(page.challenges.isEmpty())
        assertEquals("There are no challenges available at your level. Check back later!", page.emptyMessage)
    }

    @Test
    fun disabledStartControlAndCooldownDoNotCreateStartActions() {
        listOf("disabled", "aria-disabled", "image").forEach { state ->
            val document = Jsoup.parse(fixture("ar-low-level"))
            val control = document.selectFirst("#arena_list img[onclick]")!!
            when (state) {
                "disabled" -> control.attr("disabled", "disabled")
                "aria-disabled" -> control.attr("aria-disabled", "true")
                "image" -> control.attr("src", "/y/arena/startchallenge_d.png")
            }
            assertFalse(state, BattlePageParser.parseArena(document.outerHtml()).challenges.single().available)
        }
        val cooldownPage = BattlePageParser.parseArena(fixture("rb"))
        val cooldown = cooldownPage.challenges.single { it.name == "Flying Spaghetti Monster" }
        assertEquals("Cooldown: 4H 25M", cooldown.availabilityInfo)
        assertEquals("", cooldown.entryCost)
        assertFalse(cooldown.available)
    }

    @Test
    fun unnamedAndMergedNameRowsCannotBecomeChallenges() {
        val document = Jsoup.parse(fixture("ar-low-level"))
        document.selectFirst("#arena_list td")!!.empty()
        assertTrue(BattlePageParser.parseArena(document.outerHtml()).challenges.isEmpty())
        document.selectFirst("#arena_list td")!!.text("No challenges").attr("colspan", "8")
        assertTrue(BattlePageParser.parseArena(document.outerHtml()).challenges.isEmpty())
    }

    private fun applyTurn(page: com.mitahv.mobile.model.BattlePage, response: JSONObject) = requireNotNull(
        BattlePageParser.parseActionResponse(page, response, HvWorld.Persistent, HvRoute.Battle.url(HvWorld.Persistent)),
    )

    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/battle-modes/$name.html")).readText()
    private fun activeFixture(name: String) = requireNotNull(javaClass.getResource("/battle-active/$name.html")).readText()

    private companion object {
        val battleHtml = """
            <div id="battle_main">
              <div id="pane_completion"></div><div id="pane_effects"></div>
              <div id="pane_action"></div>
              <div id="pane_vitals">
                <div id="dvbh"><img style="width:207px"></div>
                <div id="dvbm"><img style="width:414px"></div>
                <div id="dvbs"><img style="width:100px"></div>
                <div id="dvrhd">12000</div><div id="dvrm">5000</div><div id="dvrs">1000</div>
              </div>
              <div id="pane_quickbar"><div id="quickbar">
                <div id="qb1" onclick="battle.lock_action(this,1,'magic',311); battle.set_friendly_skill(311); battle.touch_and_go()" onmouseover="battle.set_infopane_spell('Cure', 'Restores health.')"></div>
              </div></div>
              <div id="pane_skill"><table id="table_skills"><div id="1001" class="btsd" onclick="battle.lock_action(this,1,'magic',1001); battle.set_friendly_skill(1001); battle.touch_and_go()"><span>Flee</span></div></table></div>
              <div id="pane_magic"><table id="table_magic"><div id="111" class="btsd" onclick="battle.lock_action(this,1,'magic',111); battle.set_hostile_skill(111)" onmouseover="battle.set_infopane_spell('Fire', 'Hits one enemy.')"><span>Fire</span></div></table></div>
              <div id="pane_item"></div>
              <div id="pane_monster"><div id="mkey_1"><div class="btm2">493</div><div class="btm3">Target</div><div class="chbd"><img alt="health" style="width:60px"></div></div></div>
              <div id="pane_log"><table id="textlog"><tbody><tr><td>Battle begins.</td></tr></tbody></table></div>
              <img id="expbar" style="width:64px">
              <script>var battle_token = "runtime-token";</script>
            </div>
        """.trimIndent()
    }
}
