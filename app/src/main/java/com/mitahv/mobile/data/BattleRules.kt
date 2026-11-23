package com.mitahv.mobile.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mitahv.mobile.model.*
import kotlin.math.round

internal sealed interface RuleTurn {
    data class Submit(val action: BattleAction) : RuleTurn
    data class Stop(val number: Int, val message: String) : RuleTurn
    data class Unsupported(val number: Int, val field: String) : RuleTurn
    data object NoAction : RuleTurn
}

internal class UnknownRuleField(val field: String) : RuntimeException(null, null, false, false)
internal fun unknown(field: String): Nothing = throw UnknownRuleField(field)
internal val JsonElement?.string: String? get() = this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
internal val JsonElement?.bool: Boolean? get() = this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean

/** Parsed once per published profile. An evaluation never submits, mutates the draft or runs script. */
internal class BattleRules(draft: JpxDraft) {
    private data class Row(val rule: JsonObject, val conditions: List<JsonObject>)
    private val rows = JpxDocument(draft).let { doc ->
        val mode = draft.modes.single()
        doc.groups(mode).filterNot { it.startsWith("kb_") }.flatMap { group -> doc.rules(mode, group).map { element ->
            val rule = element.asJsonObject
            Row(rule, rule.getAsJsonArray("conditions")?.map { it.asJsonObject }.orEmpty())
        } }
    }

    fun next(page: BattlePage, world: String, scene: BattleScene?, school: SpellSchool?): RuleTurn {
        if (page.isComplete || page.token.isBlank() || page.enemies.none { it.targetable }) return RuleTurn.NoAction
        val ctx = RuleContext(page, world, scene, school)
        var target: Int? = null
        var locked = false
        rows.forEachIndexed { index, (rule, conditions) ->
            try {
                if (rule["disabled"]?.let { it.bool ?: unknown("disabled") } == true) return@forEachIndexed
                val type = rule["type"].string ?: unknown("type")
                if (type == "target") {
                    if (!locked) {
                        target = if (ctx.matches(conditions.filterNot { it.targetCondition }, null)) {
                            ctx.selectTarget(rule, conditions.filter { it.targetCondition })
                        } else null
                        locked = target != null
                    }
                    return@forEachIndexed
                }
                if (target == null && type in setOf("skill", "spellDamage", "spellDebuff", "normalAttack")) return@forEachIndexed
                locked = false
                val name = rule["name"].string.orEmpty()
                if (type == "smartDebuff") {
                    val spell = ctx.option(type, name) ?: return@forEachIndexed
                    if (!ctx.matches(conditions.filterNot { it.targetCondition }, null)) return@forEachIndexed
                    val aim = ctx.smartTarget(rule, conditions.filter { it.targetCondition }) ?: return@forEachIndexed
                    return RuleTurn.Submit(BattleAction(spell.mode, page.enemies[aim].target, spell.skill))
                }
                val action = when (type) {
                    "normalAttack" -> if (target != null && ctx.control("attack")?.enabled == true) BattleAction("attack", page.enemies[target].target) else null
                    "toggle" -> ctx.control(name.lowercase())?.takeIf { it.enabled && it.mode in setOf("spirit", "defend", "focus") &&
                        (it.mode != "spirit" || it.selected != (rule["toggled"].bool ?: unknown("toggled"))) }?.let { BattleAction(it.mode) }
                    "skill", "spellDamage", "spellDebuff", "spellSupport", "item", "spellInstant", "spellDuration", "itemInstant", "itemDuration" -> {
                        if (type == "spellDamage" && name in listOf("T1", "T2", "T3") && school == null && !ctx.matches(conditions, target)) return@forEachIndexed
                        val option = ctx.option(type, name) ?: return@forEachIndexed
                        val aim = if (option.targetsEnemy) target?.let { page.enemies[it].target } ?: return@forEachIndexed else 0
                        BattleAction(option.mode, aim, option.skill)
                    }
                    "stop" -> {
                        if (ctx.matches(conditions, target)) return RuleTurn.Stop(index + 1, rule["customMessage"].string.orEmpty().take(160))
                        null
                    }
                    else -> if (ctx.matches(conditions, target)) unknown(type) else null
                }
                if (action != null && ctx.matches(conditions, target)) return RuleTurn.Submit(action)
            } catch (e: UnknownRuleField) { return RuleTurn.Unsupported(index + 1, e.field) }
        }
        return RuleTurn.NoAction
    }
}

internal val JsonObject.targetCondition get() = this["key"].string?.startsWith("t") == true
internal class RuleContext(val page: BattlePage, val world: String, val scene: BattleScene?, val school: SpellSchool?) {
    val enemies = page.enemies
    val alive = enemies.indices.filter { enemies[it].targetable }
    fun control(mode: String) = page.controls.find { it.mode == mode }
    fun spellName(name: String): String = when (name) {
        "T1", "T2", "T3" -> damageSpells.getValue(school ?: unknown("pMaxSpellType"))[name.last().digitToInt() - 1]
        else -> name
    }
    fun option(type: String, raw: String): BattleActionOption? {
        val name = if (type == "spellDamage") spellName(raw) else raw
        val options = when {
            type.startsWith("item") -> page.items
            type == "skill" -> page.skills.ifEmpty { page.quickActions }
            else -> page.spells.ifEmpty { page.quickActions }
        }
        return options.firstOrNull { it.name == name && it.enabled && it.skill > 0 }
    }
    private fun health(i: Int) = enemies[i].takeIf { it.healthMeasured }?.healthFraction ?: unknown("tHP")
    fun selectTarget(rule: JsonObject, conditions: List<JsonObject>): Int? {
        val priority = rule["priorityRule"].string ?: if (rule["bottomUp"].bool == true) "Bottom Up" else "Top Down"
        val order = when (priority) {
            "Top Down" -> alive
            "Bottom Up" -> alive.asReversed()
            "Current HP Percent Low to High" -> alive.sortedBy { health(it) }
            "Current HP Percent High to Low" -> alive.sortedByDescending { health(it) }
            "Current HP Low to High", "Current HP High to Low" -> alive.sortedBy {
                (page.round.maxHealth[it] ?: unknown("priorityRule")) * health(it) * (if (priority.endsWith("High")) 1 else -1)
            }
            else -> unknown("priorityRule")
        }
        return order.firstOrNull { matches(conditions, it) }
    }
}

internal fun ratio(fraction: Float, scale: Int = 1) = round(fraction.toDouble() * scale * 1000) / 1000

private val damageSpells = mapOf(
    SpellSchool.Fire to listOf("Fiery Blast", "Inferno", "Flames of Loki"),
    SpellSchool.Cold to listOf("Freeze", "Blizzard", "Fimbulvetr"),
    SpellSchool.Wind to listOf("Gale", "Downburst", "Storms of Njord"),
    SpellSchool.Elec to listOf("Shockblast", "Chained Lightning", "Wrath of Thor"),
    SpellSchool.Holy to listOf("Smite", "Banishment", "Paradise Lost"),
    SpellSchool.Dark to listOf("Corruption", "Disintegrate", "Ragnarok"),
)
