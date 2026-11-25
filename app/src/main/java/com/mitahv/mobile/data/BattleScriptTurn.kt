package com.mitahv.mobile.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mitahv.mobile.model.*

internal sealed interface ScriptDecision {
    data class Action(val action: BattleAction) : ScriptDecision
    data class Stop(val message: String, val offset: Int = 0) : ScriptDecision
    data class Error(val reason: String, val offset: Int = 0) : ScriptDecision
    data object Continue : ScriptDecision
}

/** A script receives public battle facts and indices into this turn's legal actions, never a request or token. */
internal class BattleScriptTurn(ctx: RuleContext, loadout: BattleLoadout?) {
    private val page = ctx.page
    private data class Choice(val kind: String, val name: String, val aim: Int?, val action: BattleAction)
    private val choices = buildList {
        val alive = page.enemies.withIndex().filter { it.value.targetable }
        if (ctx.control("attack")?.enabled == true) alive.forEach { (i, e) -> add(Choice("attack", "", i, BattleAction("attack", e.target))) }
        page.controls.filter { it.enabled && it.mode in setOf("spirit", "defend", "focus") }.forEach {
            add(Choice("toggle", it.mode, null, BattleAction(it.mode)))
        }
        for (opt in (page.spells + page.skills + page.items + page.quickActions).filter { it.enabled }.distinctBy { it.mode to it.skill }) {
            if (!opt.enabled || opt.skill <= 0 || opt.mode !in setOf("magic", "items")) continue
            val kind = if (opt.mode == "items") "item" else "spell"
            if (opt.targetsEnemy) alive.forEach { (i, e) -> add(Choice(kind, opt.name, i, BattleAction(opt.mode, e.target, opt.skill))) }
            else add(Choice(kind, opt.name, null, BattleAction(opt.mode, skill = opt.skill)))
        }
    }
    private fun effects(values: List<BattleEffect>) = values.associate { effect ->
        val stack = Regex(""" \(x(\d+)\)$""").find(effect.name)
        effect.name.substringBefore(" (x") to mapOf("turns" to (effect.duration.toIntOrNull() ?: effect.duration),
            "stack" to if (effect.unknownStack) "-" else (stack?.groupValues?.get(1)?.toIntOrNull() ?: 1))
    }
    private fun cooldowns(options: List<BattleActionOption>) = options.groupBy { it.name }.mapValues { (_, items) ->
        if (items.any { it.enabled }) 0 else page.remainingCooldowns[items.first().name] ?: "-"
    }
    private val facts = mapOf(
        "prefix" to "jpx_", "isekaiSuffix" to if (ctx.world == "Isekai") "_isekai" else "", "playerLevel" to ctx.playerLevel,
        "battleStyle" to loadout?.style?.key, "battleType" to ctx.scene?.name, "towerFloor" to page.round.floor,
        "roundInfo" to mapOf("current" to page.round.current, "total" to page.round.total),
        "difficulty" to ctx.difficulty, "effectiveElement" to ctx.school?.name?.lowercase(),
        "totalCounts" to (page.round.priorRoundCasts.keys + page.round.casts.keys).associateWith(page.round::totalCasts),
        "itemSlots" to page.items.filter { it.enabled && it.itemId != null }.map { mapOf("id" to it.itemId, "name" to it.name) },
        "effectIcons" to page.playerEffects.mapNotNull { it.iconKey }, "log" to page.log,
        "vitals" to buildMap<String, Number> {
            for ((name, key) in listOf("Health" to "hp", "Mana" to "mp", "Spirit" to "sp", "Overcharge" to "oc")) {
                val v = page.playerVitals.find { it.name == name && it.measured } ?: continue
                if (key == "oc") put(key, ratio(v.fraction, 10)) else {
                    put(key + "Percentage", ratio(v.fraction)); v.value.replace(",", "").substringBefore('/').trim().toIntOrNull()?.let {
                        put(key + "Current", it); if (v.fraction > 0) put(key + "Max", kotlin.math.round(it * 10.0 / v.fraction) / 10.0)
                    }
                }
            }
        }, "spiritStatus" to ctx.control("spirit")?.selected, "actionCounts" to page.round.casts + ("history" to page.round.history),
        "spellCooldowns" to cooldowns(page.spells + page.skills + page.quickActions.filter { it.mode == "magic" }), "itemCooldowns" to cooldowns(page.items),
        "spellDamageBonus" to loadout?.school?.let { mapOf("maxType" to it.name.lowercase(), "maxValue" to loadout.maximum) },
        "allMonsterInfo" to ctx.monsterInfo.mapKeys { "mkey_${it.key}" },
        "monstersEffects" to page.enemies.associate { it.name to effects(page.monsterEffects[it.name].orEmpty()) },
        "playerEffectsObj" to effects(page.playerEffects), "monsterData" to page.enemies.mapIndexed { i, e ->
            // JPX uses a 100-point relative scale when no spawn record is available.
            mapOf("name" to e.name, "id" to (page.round.monsterIds[e.name] ?: 0), "level" to e.level.substringAfterLast(' ').toIntOrNull(), "maxHP" to (page.round.maxHealth[i] ?: 100))
        },
        "monsters" to page.enemies.mapIndexed { i, e -> mapOf("index" to i, "name" to e.name, "type" to bossType(e.name),
            "level" to e.level.substringAfterLast(' ').toIntOrNull(), "isAlive" to e.targetable, "hpPercentage" to monsterRatio(e.healthFraction),
            "mpPercentage" to monsterRatio(e.magicFraction), "spPercentage" to monsterRatio(e.spiritFraction), "effectObj" to effects(page.effectsOf(e))) },
        "damageTiers" to ctx.school?.let { (1..3).map { tier -> ctx.spellName("T$tier") } },
        "choices" to choices.map { listOf(it.kind, it.name, it.aim) },
    )
    fun source(codes: List<String>, newTurn: Boolean, newRound: Boolean): String =
        "__obeliskEvaluate(${if (newTurn) gson.toJson(facts) else "null"},${gson.toJson(codes)},$newRound)"
    fun decode(raw: String): ScriptDecision {
        val value = JsonParser.parseString(raw).asJsonObject
        return when (value["kind"].string) {
            "continue" -> ScriptDecision.Continue
            "stop" -> ScriptDecision.Stop(value["message"].string.orEmpty().take(160), value["offset"].asInt)
            "error" -> ScriptDecision.Error(value["message"].string.orEmpty().take(160), value["offset"].asInt)
            "action" -> value["id"]?.toString()?.toIntOrNull()?.let(choices::getOrNull)?.let { ScriptDecision.Action(it.action) }
                ?: ScriptDecision.Error("Invalid action")
            else -> ScriptDecision.Error("Invalid script result")
        }
    }
    private companion object { val gson = Gson() }
}
