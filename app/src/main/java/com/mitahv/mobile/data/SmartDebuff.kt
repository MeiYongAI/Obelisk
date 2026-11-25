package com.mitahv.mobile.data

import com.google.gson.JsonObject

/** JPX first tries contiguous triples, then pairs, gapped triples, finally a single target. */
internal suspend fun RuleContext.smartTarget(rule: JsonObject, conditions: List<JsonObject>): Int? {
    fun int(key: String, default: Int): Int = rule[key]?.let { v ->
        v.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull() ?: unknown(key)
    } ?: default
    val width = int("targetCount", 3).also { if (it !in 1..3) unknown("targetCount") }
    val reverse = rule["bottomUp"]?.let { it.bool ?: unknown("bottomUp") } ?: false
    val step = if (reverse) -1 else 1
    val skip = int("tailSkip", 0)
    val order = if (reverse) alive.last() downTo 0 else alive.first()..enemies.lastIndex
    // JPX counts physical slots, including defeated foes, when a negative tailSkip selects a window.
    val count = if (skip < 0) (-skip.toLong()).coerceAtMost(enemies.size.toLong()).toInt()
        else (alive.last() - alive.first() + 1 - skip).coerceAtLeast(0)
    val slots = order.take(count)
    val cache = mutableMapOf<Int, Boolean>()
    suspend fun hit(i: Int) = cache.getOrPut(i) { enemies.getOrNull(i)?.targetable == true && matches(conditions, i) }
    fun center(i: Int): Int = when {
        (width == 3 || width == 2 && !reverse) && i == slots.first() && enemies.getOrNull(i + step)?.targetable == true -> i + step
        (width == 3 || width == 2 && reverse) && i == slots.last() && enemies.getOrNull(i - step)?.targetable == true -> i - step
        else -> i
    }
    val limit = when {
        enemies.size - alive.size <= 1 -> int("maxAtFirst", Int.MAX_VALUE)
        alive.size <= int("minMonstersLeft", 10) -> Int.MAX_VALUE
        else -> 0
    }
    if (limit <= 0) return null
    val inner = slots.drop(1).dropLast(1)
    val target = (if (width == 3) inner.firstOrNull { hit(it - 1) && hit(it) && hit(it + 1) } else null)
        ?: (if (width >= 2) slots.firstOrNull { hit(it - 1) && hit(it) }?.let(::center) else null)
        ?: (if (width == 3) inner.firstOrNull { hit(it - 1) && enemies[it].targetable && hit(it + 1) } else null)
        ?: slots.firstOrNull { hit(it) }?.let(::center) ?: return null
    return target.takeIf { limit == Int.MAX_VALUE || casts(rule["name"].string ?: unknown("name")) < limit }
}
