package com.mitahv.mobile.data

import com.google.gson.JsonParser
import com.mitahv.mobile.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class BattleMonsterFactsTest {
    @Test fun spawnIdsMissingDataAndUtcAgeFollowJpxRules() = runBlocking {
        val html = requireNotNull(javaClass.getResource("/battle-active/ring-of-blood.html")).readText()
        val page = BattlePageParser.parseBattle(html)
        assertEquals(20L, page.round.monsterIds["Konata"])
        val date = LocalDate.now(ZoneOffset.UTC).minusDays(5).toString()
        val first = MonsterRecord(20,"Konata","Legendary","Cold",1500,date)
        val ctx = RuleContext(page,"Persistent",page.scene,null,monsterInfo = mapOf(page.enemies[0].target to first))
        suspend fun match(key: String, value: String, target: Int = 0) =
            ctx.matches(listOf(JsonParser.parseString("""{"key":"$key","value":$value}""").asJsonObject),target)
        assertTrue(match("tClasses","""["Legendary"]""")); assertTrue(match("tPowerLevel","[1500,1500]"))
        assertTrue(match("tDaysSinceUpdate","[5,5]")); assertTrue(match("tDaysSinceUpdate","[9999,9999]",1))
        assertFalse(match("tPowerLevel","[0,9999]",1))
    }

    @Test fun sixthEffectPreservesHiddenEffectsFromTheLogUntilTheyExpire() {
        val old = (1..5).map { BattleEffect("Effect $it","","8") }
        val target = BattleEnemy(1,"Dragon","300",1f,effects=old,targetable=true)
        val saved = monsterEffectHistory(emptyMap(),listOf(target),emptyList())
        val six = target.copy(effects = old.drop(1).map { it.copy(duration="6") } + listOf(BattleEffect("Slow","","5"),BattleEffect("Weaken","","5")))
        val added = monsterEffectHistory(saved,listOf(six),listOf("Dragon gains the effect Asleep."))
        assertEquals("6",added["Dragon"]!!.first { it.name=="Effect 1" }.duration)
        assertTrue(added["Dragon"]!!.first { it.name=="Asleep" }.unknownStack)
        val expired = monsterEffectHistory(added,listOf(six),listOf("The effect Effect 1 on Dragon has expired.","Dragon has been roused from its sleep."))
        assertEquals(six.effects.toSet(),expired["Dragon"]!!.toSet())
        assertTrue(monsterEffectHistory(expired,listOf(target.copy(effects=emptyList())),emptyList()).isEmpty())
    }
}
