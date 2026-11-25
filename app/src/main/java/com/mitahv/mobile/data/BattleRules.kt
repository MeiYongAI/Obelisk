package com.mitahv.mobile.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mitahv.mobile.model.*

internal sealed interface RuleTurn {
    data class Submit(val action: BattleAction) : RuleTurn
    data class Stop(val number: Int, val message: String) : RuleTurn
    data class ScriptError(val number: Int, val reason: String) : RuleTurn
    data class Unsupported(val number: Int, val field: String) : RuleTurn
    data object NoAction : RuleTurn
}

internal class UnknownRuleField(val field: String) : RuntimeException(null, null, false, false)
internal fun unknown(field: String): Nothing = throw UnknownRuleField(field)
internal val JsonElement?.string: String? get() = this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
internal val JsonElement?.bool: Boolean? get() = this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean

/** Parsed once per published profile. An evaluation chooses at most one action; only the turn runner can submit it. */
internal class BattleRules(draft: JpxDraft) {
    private data class Row(val rule: JsonObject, val conditions: List<JsonObject>)
    private val elementOverride: SpellSchool?
    private val rows = JpxDocument(draft).let { doc ->
        val mode = draft.modes.single()
        val execution = doc.config.getAsJsonObject(mode)["jpxPlusExecution"]?.takeIf { it.isJsonObject }?.asJsonObject
        val element = execution?.get("element").string
        elementOverride = SpellSchool.entries.find { it.name.lowercase() == element }
        doc.groups(mode).filterNot { it.startsWith("kb_") }.flatMap { group -> doc.rules(mode, group).map { element ->
            val rule = element.asJsonObject
            Row(rule, rule.getAsJsonArray("conditions")?.map { it.asJsonObject }.orEmpty())
        } }
    }

    val needsMonsterInfo = rows.any { (rule, conditions) ->
        rule["disabled"].bool != true && (conditions.any { it["key"].string in monsterFields } ||
            rule["code"].string?.let { code -> code.contains("allMonsterInfo") || monsterFields.any(code::contains) } == true)
    }

    suspend fun next(page: BattlePage, world: String, scene: BattleScene?, school: SpellSchool?, playerLevel: Int? = null,
        nameMatches: (suspend (String, String) -> Boolean)? = null,
        monsterInfo: Map<Int, MonsterRecord> = emptyMap(),
        script: suspend (List<String>, RuleContext) -> ScriptDecision): RuleTurn {
        if (page.isComplete || page.token.isBlank() || page.enemies.none { it.targetable }) return RuleTurn.NoAction
        val ctx = RuleContext(page, world, scene, elementOverride ?: school, playerLevel, nameMatches, monsterInfo)
        var target: Int? = null
        var locked = false
        var nextRow = 0
        rows.forEachIndexed { index, (rule, conditions) ->
            if (index < nextRow) return@forEachIndexed
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
                        if (type == "spellDamage" && name in listOf("T1", "T2", "T3") && ctx.school == null && !ctx.matches(conditions, target)) return@forEachIndexed
                        val option = ctx.option(type, name) ?: return@forEachIndexed
                        val aim = if (option.targetsEnemy) target?.let { page.enemies[it].target } ?: return@forEachIndexed else 0
                        BattleAction(option.mode, aim, option.skill)
                    }
                    "kamisamaniNattaHi" -> {
                        if (!ctx.matches(conditions, target)) return@forEachIndexed
                        val code = rule["code"].string?.takeIf { it.isNotBlank() } ?: return RuleTurn.ScriptError(index + 1, "Empty JavaScript rule")
                        // Consecutive scripts are synchronous in JPX. In particular, a Promise
                        // cleanup queued by one rule must run after the following rules.
                        val codes = mutableListOf(code)
                        nextRow = index + 1
                        while (nextRow < rows.size) {
                            val next = rows[nextRow]
                            if (next.rule["type"].string != type || next.conditions.isNotEmpty() || next.rule.has("disabled")) break
                            codes += next.rule["code"].string?.takeIf { it.isNotBlank() } ?: break
                            nextRow++
                        }
                        when (val decision = script(codes, ctx)) {
                            is ScriptDecision.Action -> return RuleTurn.Submit(decision.action)
                            is ScriptDecision.Stop -> return RuleTurn.Stop(index + decision.offset + 1, decision.message)
                            is ScriptDecision.Error -> return RuleTurn.ScriptError(index + decision.offset + 1, decision.reason)
                            ScriptDecision.Continue -> return@forEachIndexed
                        }
                    }
                    "stop" -> {
                        if (ctx.matches(conditions, target)) return RuleTurn.Stop(index + 1, rule["customMessage"].string.orEmpty().take(160))
                        null
                    }
                    else -> if (ctx.matches(conditions, target)) unknown(type) else null
                }
                if (action != null && ctx.matches(conditions, target)) return RuleTurn.Submit(action)
            } catch (e: UnknownRuleField) { return RuleTurn.Unsupported(index + 1, e.field) }
            catch (e: NameMatchFailure) { return RuleTurn.ScriptError(index + 1, e.reason) }
        }
        return RuleTurn.NoAction
    }
}

private val monsterFields = setOf("tClasses", "tPowerLevel", "tDaysSinceUpdate")
internal val JsonObject.targetCondition get() = this["key"].string?.startsWith("t") == true
internal class NameMatchFailure(val reason: String) : RuntimeException(reason)
internal class RuleContext(val page: BattlePage, val world: String, val scene: BattleScene?, val school: SpellSchool?,
    val playerLevel: Int? = null, val nameMatches: (suspend (String, String) -> Boolean)? = null,
    val monsterInfo: Map<Int, MonsterRecord> = emptyMap()) {
    val enemies = page.enemies
    val alive = enemies.indices.filter { enemies[it].targetable }
    fun control(mode: String) = page.controls.find { it.mode == mode }
    val difficulty: String? get() = when {
        scene == BattleScene.Tower && page.round.floor != null -> when (page.round.floor) {
            in 1..6 -> "Normal"
            in 7..13 -> "Hard"
            in 14..19 -> "Nightmare"
            in 20..26 -> "Hell"
            in 27..33 -> "Nintendo"
            in 34..39 -> "IWBTH"
            else -> "PFUDOR"
        }
        scene == BattleScene.Arena && (page.round.total ?: 0) >= 90 -> "PFUDOR"
        else -> page.difficulty
    }

    fun cooldown(name: String): Int? {
        val slots = page.spells + page.skills + page.items + page.quickActions
        return when {
            slots.any { it.name == name && it.enabled } -> 0
            slots.any { it.name == name } -> page.remainingCooldowns[name] ?: 9999
            else -> null
        }
    }
    fun spellName(name: String): String = when (name) {
        "T1", "T2", "T3" -> damageSpells.getValue(school ?: unknown("pMaxSpellType"))[name.last().digitToInt() - 1]
        else -> name
    }
    fun option(type: String, raw: String): BattleActionOption? {
        val name = if (type == "spellDamage") spellName(raw) else raw
        val options = when {
            type.startsWith("item") -> page.items
            type == "skill" -> page.skills
            else -> page.spells
        }
        fun available(option: BattleActionOption) = option.name == name && option.enabled && option.skill > 0
        return options.firstOrNull(::available) ?: page.quickActions.firstOrNull {
            it.mode == (if (type.startsWith("item")) "items" else "magic") && available(it)
        }
    }
    private fun health(i: Int) = monsterRatio(enemies[i].healthFraction.takeIf { enemies[i].healthMeasured })
    suspend fun selectTarget(rule: JsonObject, conditions: List<JsonObject>): Int? {
        val priority = rule["priorityRule"].string ?: if (rule["bottomUp"].bool == true) "Bottom Up" else "Top Down"
        val order = when (priority) {
            "Top Down" -> alive
            "Bottom Up" -> alive.asReversed()
            "Current HP Percent Low to High" -> alive.sortedBy { health(it) }
            "Current HP Percent High to Low" -> alive.sortedByDescending { health(it) }
            "Current HP Low to High", "Current HP High to Low" -> alive.sortedBy {
                (page.round.maxHealth[it] ?: 100) * health(it) * (if (priority.endsWith("High")) 1 else -1)
            }
            else -> unknown("priorityRule")
        }
        return order.firstOrNull { matches(conditions, it) }
    }
}

internal fun ratio(fraction: Float, scale: Int = 1) = Math.round(fraction.toDouble() * scale * 1000) / 1000.0
// JPX reads whole pixels from a 120px monster bar, then rounds to two decimal places.
internal fun monsterRatio(fraction: Float?) = Math.round(Math.round((fraction ?: 0f) * 120) * 100.0 / 120) / 100.0

private val damageSpells = mapOf(
    SpellSchool.Fire to listOf("Fiery Blast", "Inferno", "Flames of Loki"),
    SpellSchool.Cold to listOf("Freeze", "Blizzard", "Fimbulvetr"),
    SpellSchool.Wind to listOf("Gale", "Downburst", "Storms of Njord"),
    SpellSchool.Elec to listOf("Shockblast", "Chained Lightning", "Wrath of Thor"),
    SpellSchool.Holy to listOf("Smite", "Banishment", "Paradise Lost"),
    SpellSchool.Dark to listOf("Corruption", "Disintegrate", "Ragnarok"),
)
