package com.mitahv.mobile.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mitahv.mobile.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BattleScriptSandboxTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val cleave = BattleActionOption("cleave", "Great Cleave", "magic", 123, true)
    private val page = BattlePage("fixture-only", listOf(BattleVital("Overcharge", "3", .3f)) +
        listOf("Health", "Mana", "Spirit").map { BattleVital(it, "100", 1f) }, listOf(
        BattleEnemy(7, "Fixture", "250", .8f, targetable = true), BattleEnemy(8, "Yggdrasil", "280", 1f, targetable = true),
        BattleEnemy(9, "Defeated", "1", 0f)), emptyList(), listOf(cleave), emptyList(), emptyList(), emptyList(), "", 0f,
        controls = listOf(BattleControl("attack", true)), round = BattleRoundFacts(1, 2, sawStart = true, maxHealth = mapOf(0 to 200, 1 to 500)))

    private fun script(code: String) = JsonObject().apply { addProperty("type", "kamisamaniNattaHi"); addProperty("code", code) }
    private suspend fun run(sandbox: BattleScriptSandbox, vararg rows: JsonObject, turn: BattlePage = page): RuleTurn {
        val config = JsonObject().apply {
            addProperty("version", 1); add("Staff_General", JsonObject().apply { add("rules", JsonArray().apply { rows.forEach(::add) }) })
        }
        val rules = BattleRules(JpxImport.read(config.toString().byteInputStream(), "test", "Isekai"))
        return rules.next(turn, "Isekai", BattleScene.Item, null, 280) { code, ctx -> sandbox.evaluate(code, ctx, null) }
    }
    @Test fun selectsOnlyAvailableActionsOnLivingTargets() = runBlocking {
        BattleScriptSandbox(context).use { engine ->
            val target = "jpx.monstersObj.activeMonsters[1]"
            assertEquals(RuleTurn.Submit(BattleAction("attack", 8)), run(engine, script("return jpx.doAttackGoNext($target)")))
            assertEquals(RuleTurn.Submit(BattleAction("magic", 8, 123)), run(engine, script("return jpx.doSpellGoNext('Great Cleave', $target)")))
            assertEquals(RuleTurn.NoAction, run(engine, script("return jpx.doSpellGoNext('Missing', $target)")))
            assertEquals(RuleTurn.NoAction, run(engine, script("return jpx.doAttackGoNext(jpx.monstersObj.monsters[2])")))
        }
    }
    @Test fun filtersBeforeExecutingAndDoesNotFallThroughOnErrors() = runBlocking {
        BattleScriptSandbox(context).use { engine ->
            val fail = script("throw Error('fixture failure')")
            val attack = script("return jpx.doAttackGoNext(jpx.monstersObj.activeMonsters[0])")
            val notHere = fail.deepCopy().apply { add("conditions", com.google.gson.JsonParser.parseString("""[{"key":"world","value":["Persistent"]}]""")) }
            val tooHigh = fail.deepCopy().apply { add("conditions", com.google.gson.JsonParser.parseString("""[{"key":"pLevel","value":[300,999]}]""")) }
            val noGlobals = script("if ([typeof document, typeof fetch, typeof XMLHttpRequest, typeof window, typeof globalThis.localStorage].some(x => x !== 'undefined')) throw Error('unexpected browser global'); return false")
            assertEquals(RuleTurn.Submit(BattleAction("attack", 7)), run(engine, fail.deepCopy().apply { addProperty("disabled", true) }, notHere, tooHigh, noGlobals, attack))
            val error = run(engine, script("return false"), fail, attack)
            assertTrue(error.toString(), error is RuleTurn.ScriptError && error.number == 2 && error.reason.contains("fixture failure"))
            assertEquals(RuleTurn.Stop(1, ""), run(engine, script("return true"), attack))
            val timeout = run(engine, script("while(true) {}"), attack)
            assertTrue(timeout.toString(), timeout is RuleTurn.ScriptError && timeout.reason.contains("timed out"))
            assertEquals(RuleTurn.Submit(BattleAction("attack", 7)), run(engine, attack))
        }
    }
    // Optional integration fixture supplied to instrumentation, never packaged with Obelisk.
    @Test fun importedDefaultCastsThenFallsBackToAttackWhenTheSkillIsUnavailable() = runBlocking {
        val file = InstrumentationRegistry.getArguments().getString("jpxFixture")
        assumeNotNull(file)
        val draft = context.openFileInput(requireNotNull(file)).use { JpxImport.read(it, "battle.json", "Isekai") }
        val mode = JpxDocument(draft).export("TwoHanded_General")
        val rules = BattleRules(JpxImport.read(mode.byteInputStream(), "two-handed.json", "Isekai"))
        BattleScriptSandbox(context).use { engine ->
            suspend fun decide(turn: BattlePage): RuleTurn {
                var scripts = 0
                val result = rules.next(turn, "Isekai", BattleScene.Item, null, 280) { code, ctx ->
                    scripts++
                    engine.evaluate(code, ctx, null)
                }
                assertEquals("The imported profile must reach its custom rule: $result", 1, scripts)
                return result
            }
            assertEquals(RuleTurn.Submit(BattleAction("magic", 8, 123)), decide(page))
            assertEquals(RuleTurn.Submit(BattleAction("attack", 8)), decide(page.copy(skills = listOf(cleave.copy(enabled = false)))))
        }
    }
}
