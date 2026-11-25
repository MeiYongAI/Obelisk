package com.mitahv.mobile.model

private val elementalEffects = setOf("Searing Skin", "Freezing Limbs", "Turbulent Air", "Deep Burns", "Breached Defense", "Blunted Attack")

/** Mirrors JPX's saved-effects table when HV fills all six visible slots. */
internal fun monsterEffectHistory(previous: Map<String, List<BattleEffect>>, enemies: List<BattleEnemy>,
                                 additions: List<String>): Map<String, List<BattleEffect>> {
    if (previous.isEmpty() && enemies.none { it.targetable && it.effects.size >= 5 }) return emptyMap()
    val next = previous.toMutableMap()
    for (enemy in enemies.filter { it.targetable }) {
        val visible = enemy.effects.associateBy { it.name.substringBefore(" (x") }
        when (visible.size) {
            in 0..4 -> next.remove(enemy.name)
            5 -> next[enemy.name] = visible.values.toList()
            6 -> {
                val saved = next[enemy.name].orEmpty().associateBy { it.name.substringBefore(" (x") }.toMutableMap()
                val delta = visible.maxOf { (key, effect) ->
                    val before = saved[key]?.duration?.toIntOrNull()
                    val now = effect.duration.toIntOrNull()
                    if (before == null || now == null) 0 else (before - now).coerceAtLeast(0)
                }
                saved.putAll(visible)
                val gained = "${enemy.name} gains the effect "
                val expired = " on ${enemy.name} has expired."
                val worn = " on ${enemy.name} has worn off."
                for (line in additions.asReversed()) {
                    val remove = when {
                        line.startsWith(gained) && line.endsWith('.') -> {
                            val name = line.removePrefix(gained).dropLast(1)
                            if (name !in visible) saved[name] = BattleEffect(name, "", "-", unknownStack = true)
                            null
                        }
                        line.startsWith("The effect ") && line.endsWith(expired) -> line.removePrefix("The effect ").removeSuffix(expired)
                        line.startsWith("The effect ") && line.endsWith(worn) -> line.removePrefix("The effect ").removeSuffix(worn)
                        line == "${enemy.name} has been roused from its sleep." -> "Asleep"
                        line == "${enemy.name} got knocked out of confuse." -> "Confused"
                        else -> null
                    }
                    if (remove != null && remove !in visible) saved.remove(remove)
                }
                val elements = visible.keys.count(elementalEffects::contains)
                for (key in saved.keys - visible.keys) {
                    val effect = saved.getValue(key)
                    val remaining = effect.duration.toIntOrNull()?.minus(delta)
                    if (key == "Coalesced Mana" || key in elementalEffects && (elements < 3 || remaining != null && remaining < 0)) {
                        saved.remove(key)
                    } else if (delta > 0 && remaining != null) {
                        saved[key] = effect.copy(duration = remaining.coerceAtLeast(0).toString())
                    }
                }
                next[enemy.name] = saved.values.toList()
            }
        }
    }
    return next
}
