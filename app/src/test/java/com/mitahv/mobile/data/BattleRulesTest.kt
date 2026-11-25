package com.mitahv.mobile.data

import com.mitahv.mobile.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BattleRulesTest {
    private val world = HvWorld.Persistent
    private val page = BattlePageParser.parseBattle(javaClass.getResource("/battle-active/ring-of-blood.html")!!.readText())
    private val target = """{"type":"target","priorityRule":"Bottom Up"}"""
    private val attack = """{"type":"normalAttack"}"""
    private fun program(vararg rows: String) = BattleRules(JpxImport.read("""{"version":1,"Staff_General":{"rules":[${rows.joinToString()}],"kb_a":[{"type":"stop"}]}}""".byteInputStream(), "fixture", world.name))
    private fun BattleRules.run(p: BattlePage = page, school: SpellSchool? = SpellSchool.Fire) = runBlocking { next(p, world.name, p.scene, school) { _, _ -> ScriptDecision.Error("script failed") } }

    @Test fun publishedElementOverridesDetectedSchoolForTierSpellsAndConditions() {
        val json = """{"battleVersion":20251216,"Staff_General":{"jpxPlusExecution":{"element":"fire"},
            "rules":[$target,{"type":"spellDamage","name":"T2","conditions":[{"key":"pMaxSpellType","value":["fire"]}]}]}}"""
        val configured = BattleRules(JpxImport.parse(json, "Staff", world.name))
        assertEquals(RuleTurn.Submit(BattleAction("magic", 2, 112)), configured.run(school = SpellSchool.Cold))
    }

    @Test fun matchedTierSpellGoesThroughRealClientAndNextPaneStopsTheLoop() = runBlocking {
        val program = program("""{"type":"stop","disabled":true}""", target, """{"type":"spellDamage","name":"T2"}""", attack)
        val turn = program.run() as RuleTurn.Submit
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request(); assertEquals("POST", req.method); assertTrue(req.url.encodedPath.endsWith("/json"))
            val body = JSONObject(Buffer().apply { req.body!!.writeTo(this) }.readUtf8())
            assertEquals(page.token, body.getString("token")); assertEquals("magic", body.getString("mode")); assertEquals(112, body.getInt("skill")); assertEquals(2, body.getInt("target"))
            Response.Builder().request(req).code(200).protocol(Protocol.HTTP_1_1).message("OK").body("""{"pane_completion":"done"}""".toResponseBody()).build()
        }.build()
        val url = HvRoute.Battle.url(world)
        val response = HentaiVerseClient(CookieJar.NO_COOKIES, httpClient = http).submitBattleAction(world, url, page.token, turn.action)
        val updated = BattlePageParser.parseActionResponse(page, response, world, url)!!
        assertSame(page.encounterKey, updated.encounterKey); assertNotSame(page.turnKey, updated.turnKey); assertEquals(RuleTurn.NoAction, program.run(updated))
    }

    @Test fun supportItemsRespectResourceThresholdEnabledAndStopOrder() {
        val cond = """"conditions":[{"key":"pHP","value":[0,0.4]}]"""
        val item = page.items.first { it.enabled }
        val rules = program("""{"type":"item","name":"${item.name}",$cond}""", """{"type":"stop","customMessage":"heal manually",$cond}""", target, attack)
        assertEquals(RuleTurn.Submit(BattleAction("attack", 2)), rules.run())
        val low = page.copy(playerVitals = listOf(BattleVital("Health", "1", 0.4f)))
        assertEquals(RuleTurn.Submit(BattleAction(item.mode, skill = item.skill)), rules.run(low))
        assertEquals(RuleTurn.Stop(2,"heal manually"), rules.run(low.copy(items = emptyList())))
        assertEquals(RuleTurn.Unsupported(1,"pHP"), rules.run(low.copy(playerVitals = emptyList())))
    }

    @Test fun targetSelectionUsesLiveOffsetsOriginalNamesAndLocksFirstMatchingPriority() {
        val select = """{"type":"target","conditions":[{"key":"tName","value":"manbear","offset":[1,1],"matched":[1,1]}]}"""
        assertEquals(RuleTurn.Submit(BattleAction("attack", 1)), program(select, target, attack).run())
        val dead = page.copy(enemies = listOf(page.enemies[0], page.enemies[1].copy(targetable = false)))
        assertEquals(RuleTurn.NoAction, program(select, attack).run(dead))
        assertEquals(RuleTurn.NoAction, program(attack).run()) // Never invent a target or implicit basic attack.
    }

    @Test fun smartDebuffChoosesAdjacentCenterAndHonorsObservedCastLimit() {
        val rule = """{"type":"smartDebuff","name":"Imperil","targetCount":3,"maxAtFirst":1,"conditions":[{"key":"tIgnoredEffects","value":[1,9999,"Imperiled"]}]}"""
        val option = page.spells.first().copy(name = "Imperil", mode = "magic", skill = 501, enabled = true)
        val p = page.copy(spells = listOf(option), enemies = (1..3).map { page.enemies.first().copy(target = it, effects = emptyList()) })
        val rules = program(rule, target, attack)
        assertEquals(RuleTurn.Submit(BattleAction("magic",2,501)), rules.run(p))
        assertEquals(RuleTurn.Submit(BattleAction("attack",3)), rules.run(p.copy(round = p.round.observe(listOf("You cast Imperil.")))))
        assertEquals(RuleTurn.Unsupported(1,"pActionCounts"), rules.run(p.copy(round = BattleRoundFacts())))
    }

    @Test fun unknownScriptsMissingAffinityOrUnknownConditionsNeverFallThroughToAttack() {
        assertEquals(RuleTurn.ScriptError(2,"script failed"), program(target, """{"type":"kamisamaniNattaHi","code":"throw 1"}""", attack).run())
        assertEquals(RuleTurn.Unsupported(2,"pMaxSpellType"), program(target, """{"type":"spellDamage","name":"T1"}""", attack).run(school = null))
        assertEquals(RuleTurn.Unsupported(2,"mystery"), program(target, """{"type":"normalAttack","conditions":[{"key":"mystery"}]}""", attack).run())
        val rules = program("""{"type":"stop","conditions":[{"key":"mystery"},{"key":"world","value":["Isekai"]}]}""", target, attack)
        assertEquals(RuleTurn.Submit(BattleAction("attack",2)), rules.run())
    }

    @Test fun roundFactsAndSpiritToggleUseServerStateNotAssumedCounts() {
        val rules = program("""{"type":"toggle","name":"Spirit","toggled":true,"conditions":[{"key":"roundCurrent","value":[1,1]}]}""", target, attack)
        assertEquals(RuleTurn.Submit(BattleAction("spirit")), rules.run())
        val state = BattlePageParser.parseActionResponse(page, JSONObject().put("pane_action", """<img id="ckey_spirit" src="/spirit_a.png" onclick="battle.lock_action(this,0,'spirit')"><img id="ckey_attack" onclick="battle.lock_action(this,0,'attack')">"""), world, HvRoute.Battle.url(world))!!
        assertEquals(RuleTurn.Submit(BattleAction("attack",2)), rules.run(state))
        assertEquals(1294120, state.round.maxHealth[0]); assertFalse(BattlePageParser.hasCombatPane(JSONObject().put("exp",2)))
    }
}
