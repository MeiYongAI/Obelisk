package com.mitahv.mobile.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mitahv.mobile.model.BattleEffect
import com.mitahv.mobile.model.BattleScene

/** A known false conjunct rules out the action even if another conjunct is unobservable. */
internal suspend fun RuleContext.matches(conditions: List<JsonObject>, target: Int?): Boolean {
    var missing: UnknownRuleField? = null
    for (condition in conditions) try {
        if (!test(condition, target)) return false
    } catch (e: UnknownRuleField) { if (missing == null) missing = e }
    missing?.let { throw it }
    return true
}

private suspend fun RuleContext.test(c: JsonObject, target: Int?): Boolean {
    val key = c["key"].string ?: unknown("key")
    val value = c["value"]
    fun values(v: JsonElement? = value) = v?.takeIf { it.isJsonArray }?.asJsonArray?.toList() ?: unknown(key)
    fun number(v: JsonElement?) = v?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble?.takeIf(Double::isFinite) ?: unknown(key)
    fun range(n: Number?, v: JsonElement? = value): Boolean {
        val a = values(v); val min = number(a.getOrNull(0)); val max = number(a.getOrNull(1))
        return (n?.toDouble() ?: unknown(key)) in min..max
    }
    fun names(skip: Int = 0) = values().drop(skip).map { it.string ?: unknown(key) }
    fun effects(list: List<BattleEffect>): Boolean {
        val ignore = key.endsWith("IgnoredEffects")
        return names(2).all { name ->
            val effect = list.find { it.name.substringBefore(" (x") == name }
            val hit = when {
                effect == null -> range(-1)
                key.endsWith("Stacks") -> effect.unknownStack || range(stack.find(effect.name)?.groupValues?.get(1)?.toIntOrNull() ?: 1)
                effect.duration == "permanent" -> range(9999)
                effect.duration in setOf("autocast", "decaying", "-") -> true // JPX treats nonnumeric duration as in range.
                else -> range(effect.duration.toIntOrNull())
            }; hit != ignore
        }
    }
    if (c.targetCondition) {
        if (target == null) return false
        val offset = c["offset"]?.let { values(it).map { v -> number(v).let { n -> if (n != n.toInt().toDouble()) unknown(key) else n.toInt() } }.also { if (it.size != 2) unknown(key) } } ?: listOf(0, 0)
        val count = alive.count { i ->
            val e = enemies[i]
            i - target in offset[0]..offset[1] && when (key) {
                "tHP" -> range(monsterRatio(e.healthFraction.takeIf { e.healthMeasured }))
                "tMP" -> range(monsterRatio(e.magicFraction))
                "tSP" -> range(monsterRatio(e.spiritFraction))
                "tName" -> {
                    val input = value.string ?: unknown(key)
                    if (input.startsWith('/') && isNameExpression(input))
                        nameMatches?.invoke(input, e.name) ?: unknown(key)
                    else e.name.contains(input, ignoreCase = true)
                }
                "tTypes" -> bossType(e.name) in names()
                "tIndex" -> { val a = values().map { number(it).toInt().let { n -> if (n > 0) n-1 else if (n < 0) enemies.size+n else 0 } }; if (a.size != 2) unknown(key); i in a[0]..a[1] }
                "tEffects", "tIgnoredEffects", "tEffectStacks" -> effects(page.effectsOf(e))
                "tClasses" -> monsterInfo[e.target]?.monsterClass in names()
                "tPowerLevel" -> monsterInfo[e.target]?.let { range(it.plvl) } ?: false
                "tDaysSinceUpdate" -> monsterInfo.isNotEmpty() && range(monsterInfo[e.target]?.daysSince() ?: 9999)
                else -> unknown(key)
            }
        }
        return c["matched"]?.let { range(count, it) } ?: (count == 1)
    }
    return when (key) {
        "world" -> world in names()
        "difficulty" -> (difficulty ?: unknown(key)) in names()
        "battleTypes" -> (scene?.name ?: unknown(key)) in names()
        "pLevel" -> range(playerLevel)
        "pMaxSpellType" -> (school?.name?.lowercase() ?: unknown(key)) in names()
        "pSpiritStatus" -> (control("spirit")?.selected ?: unknown(key)) == (value.bool ?: unknown(key))
        "pHP", "pMP", "pSP", "pOC" -> {
            val vital = when (key) { "pHP" -> "Health"; "pMP" -> "Mana"; "pSP" -> "Spirit"; else -> "Overcharge" }
            range(page.playerVitals.find { it.name == vital && it.measured }?.fraction?.let { ratio(it, if (key == "pOC") 10 else 1) })
        }
        "pEffects", "pIgnoredEffects", "pEffectStacks" -> effects(page.playerEffects)
        "monsters" -> range(enemies.size)
        "activeMonsters" -> range(alive.size)
        "defeatedMonsters" -> range(enemies.size - alive.size)
        "bosses", "activeBosses", "defeatedBosses" -> range(enemies.count { bossType(it.name) != "Normal" &&
            (key == "bosses" || it.targetable == (key == "activeBosses")) })
        "mLevel" -> range(enemies.firstOrNull()?.level?.substringAfterLast(' ')?.toIntOrNull())
        "mWithoutEffects" -> { val effects = names(2); range(alive.count { i -> effects.any { name -> page.effectsOf(enemies[i]).none { it.name.substringBefore(" (x") == name } } }) }
        "roundCurrent" -> range(page.round.current)
        "roundTotal" -> range(page.round.total)
        "roundLeft" -> range((page.round.total ?: unknown(key)) - (page.round.current ?: unknown(key)))
        "floor" -> if (scene == null) unknown(key) else scene != BattleScene.Tower || range(page.round.floor)
        "pActionCooldown" -> names(2).all { raw -> cooldown(spellName(raw))?.let { range(it) } ?: false }
        "pActionCounts" -> names(2).all { range(casts(spellName(it))) }
        "pTotalActionCounts" -> names(2).all {
            if (!page.round.sawStart || (!page.round.priorRoundsKnown && page.round.current != 1)) unknown(key)
            range(page.round.totalCasts(spellName(it)))
        }
        "pActionHistory" -> names(2).all { raw ->
            val name = spellName(raw)
            val known = page.round.history
            when {
                known.indices.any { i -> known[i] == name && range(i + 1) } -> true
                !page.round.sawStart && number(values().getOrNull(1)) >= known.size + 1 -> unknown(key)
                else -> false
            }
        }
        else -> unknown(key)
    }
}

internal fun RuleContext.casts(name: String): Int = if (page.round.sawStart) page.round.casts[name] ?: 0 else unknown("pActionCounts")
private val stack = Regex(""" \(x(\d+)\)$""")
// JPX recognizes /^\/(.*?)\/([dgimsuvy]*)$/ with JavaScript's four line terminators.
private fun isNameExpression(input: String): Boolean {
    val end = input.lastIndexOf('/')
    return end > 0 && (1 until end).none { input[it] in "\n\r\u2028\u2029" } &&
        (end + 1 until input.length).all { input[it] in "dgimsuvy" }
}
private val bossTypes = buildMap {
    fun add(type: String, names: String) = names.split('|').forEach { put(it, type) }
    add("Rare", "Manbearpig|White Bunneh|Mithra|Dalek")
    add("Legendary", "Konata|Mikuru Asahina|Ryouko Asakura|Yuki Nagato")
    add("Ultimate", "Skuld|Urd|Verdandi|Yggdrasil|Real Life|Invisible Pink Unicorn|Flying Spaghetti Monster")
    add("Arena300", "Drogon|Rhaegal|Viserion")
    add("Arena400", "New Game +|Bottomless Dungeon|Recycled Boss Rush|Time Trial Mode|Achievement Grind|Hardcore Mode")
    add("Arena500", "Angel Bunny|Applejack|Fluttershy|Gummy|Pinkie Pie|Rarity|Spike|Rainbow Dash|Twilight Sparkle")
}
internal fun bossType(name: String) = bossTypes[name] ?: "Normal"
