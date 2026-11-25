package com.mitahv.mobile.data

import com.google.gson.JsonParser
import com.mitahv.mobile.model.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class JpxNativeParityTest {
    private val world = HvWorld.Persistent
    private fun battle() = Jsoup.parse(javaClass.getResource("/battle-active/ring-of-blood.html")!!.readText())
    private fun RuleContext.match(key: String, value: String, target: Int? = null): Boolean {
        val condition = JsonParser.parseString("""{"key":"$key","value":$value}""").asJsonObject
        return runBlocking { matches(listOf(condition), target) }
    }
    private fun context(page: BattlePage) = RuleContext(page, world.name, page.scene, SpellSchool.Fire)

    @Test fun fullAndIncrementalLogsCountOneActionPerTurnAndKeepHistoryNewestFirst() {
        val doc = battle()
        val log = doc.selectFirst("#textlog tbody")!!
        log.html("""<tr><td>Initializing Item World (Round 1 / 5) ...</td></tr>""")
        var incremental = BattlePageParser.parseBattle(doc)
        val turns = listOf(
            listOf("You hit Monster for 12 damage.", "Your offhand attack hits Monster for 3 damage."),
            listOf("You cast Cure.", "You gain the effect Cloak of the Fallen."),
            listOf("You use Mana Potion."),
            listOf("You gain the effect Defending."),
        )
        for (turn in turns) {
            log.prepend("<tr><td class='tls'><hr></td></tr>")
            val batch = JSONArray()
            for (line in turn) {
                log.prepend("<tr><td>$line</td></tr>")
                batch.put(JSONObject().put("t", line))
            }
            incremental = BattlePageParser.parseActionResponse(incremental, JSONObject().put("textlog", batch), world, HvRoute.Battle.url(world))!!
        }
        val full = BattlePageParser.parseBattle(doc)
        assertEquals(full.round, incremental.round)
        assertEquals(listOf("Defend", "Mana Potion", "Cure", "Attack"), full.round.history)
        assertEquals(1, full.round.casts["Cloak of the Fallen"])
        val ctx = context(full)
        assertTrue(ctx.match("pActionCounts", """[1,1,"Attack","Cure","Defend","Mana Potion"]"""))
        assertTrue(ctx.match("pActionCounts", """[0,0,"Spirit"]"""))
        assertTrue(ctx.match("pActionHistory", """[2,4,"Mana Potion","Attack"]"""))
        assertFalse(ctx.match("pActionHistory", """[1,2,"Cure"]"""))
        assertTrue(ctx.match("pTotalActionCounts", """[1,1,"Cure"]"""))
    }

    @Test fun newRoundResetsHistoryButRetainsEncounterTotalsOnlyAcrossConfirmedContinuation() {
        val first = BattlePageParser.parseBattle(battle()).copy(round =
            BattleRoundFacts(current = 1, total = 3, sawStart = true).observe(listOf("You cast Cure.")),
            isComplete = true, continuation = BattleContinuation("Next"))
        val next = first.copy(isComplete = false, round = BattleRoundFacts(current = 2, total = 3, sawStart = true),
            encounterKey = Any(), continuation = null)
        val continued = context(next.afterRoundContinuation(first))
        assertTrue(continued.match("pTotalActionCounts", """[1,1,"Cure"]"""))
        assertTrue(continued.match("pActionCounts", """[0,0,"Cure"]"""))
        assertFalse(continued.match("pActionHistory", """[1,4,"Cure"]"""))
        assertThrows(UnknownRuleField::class.java) { context(next).match("pTotalActionCounts", """[0,0,"Cure"]""") }
    }

    @Test fun cooldownConditionsDistinguishRemainingDisabledReadyAndAbsent() {
        val spell = BattleActionOption("test", "Inferno", "magic", 112, true, enabled = false)
        val p = BattlePageParser.parseBattle(battle()).copy(spells = listOf(spell), skills = emptyList(), quickActions = emptyList(),
            remainingCooldowns = mapOf("Inferno" to 3))
        assertTrue(context(p).match("pActionCooldown", """[3,3,"T2"]"""))
        assertTrue(context(p.copy(remainingCooldowns = emptyMap())).match("pActionCooldown", """[9999,9999,"Inferno"]"""))
        assertTrue(context(p.copy(spells = listOf(spell.copy(enabled = true)))).match("pActionCooldown", """[0,0,"Inferno"]"""))
        assertFalse(context(p.copy(spells = emptyList())).match("pActionCooldown", """[0,9999,"Inferno"]"""))
    }

    @Test fun hpPrioritiesAndThresholdsUseJpxHundredthsAndRelativeMaxForMissingSpawnLog() {
        val initial = BattlePageParser.parseBattle(battle())
        val p = initial.copy(round = BattleRoundFacts(), enemies = listOf(
            initial.enemies[0].copy(healthFraction = 1 / 120f),
            initial.enemies[1].copy(healthFraction = 15 / 120f)))
        val ctx = context(p)
        assertTrue(ctx.match("tHP", "[0.01,0.01]", 0))
        assertTrue(ctx.match("tHP", "[0.13,0.13]", 1))
        val rule = JsonParser.parseString("""{"priorityRule":"Current HP High to Low"}""").asJsonObject
        assertEquals(1, runBlocking { ctx.selectTarget(rule, emptyList()) })
    }

    @Test fun playerHealthThresholdUsesJpxRoundingAtHalfAThousandth() {
        // A 31 px health bar in HV's standard layout is exactly 31/496 = 0.0625.
        val fraction = 31 / 496f
        val p = BattlePageParser.parseBattle(battle()).copy(playerVitals = listOf(BattleVital("Health", "100", fraction)))
        assertTrue(context(p).match("pHP", "[0.063,0.063]"))
        assertFalse(context(p).match("pHP", "[0,0.062]"))
    }

    @Test fun difficultyReadsHvHeaderAndUsesTowerBandsAndHighArenaOverride() {
        val doc = battle()
        doc.select("#level_readout").remove()
        doc.body().append("<div id='level_readout'>Hard Lv.493</div>")
        val p = BattlePageParser.parseBattle(doc)
        assertTrue(context(p).match("difficulty", """["Hard"]"""))
        val tower = p.copy(scene = BattleScene.Tower, round = BattleRoundFacts(floor = 34))
        assertTrue(context(tower).match("difficulty", """["IWBTH"]"""))
        val arena = p.copy(scene = BattleScene.Arena, round = BattleRoundFacts(total = 90))
        assertTrue(context(arena).match("difficulty", """["PFUDOR"]"""))
    }
}
