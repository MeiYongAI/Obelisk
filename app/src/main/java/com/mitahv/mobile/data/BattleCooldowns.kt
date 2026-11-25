package com.mitahv.mobile.data

import com.mitahv.mobile.model.BattleActionOption

/** Each .tls separator (or one action-response textlog) delimits one action, not one log line. */
internal object BattleCooldowns {
    private val cast = Regex("""You (?:cast|use) (.+?)\.?""")
    private val zeroTurnItem = Regex("""You use .+ (?:Gem|Draught|Potion|Elixir|Drink|Candy|Infusion|Scroll|Vase|Bubble)\.?""")
    private val attack = Regex("""^(?:You (?:hit|crit|glance|miss|strike)\b|Arcane Blow\b|.+ (?:dodges|parries|evades) your attack\b|You gain the effect (?:Defending|Focusing)\b)""")

    fun observe(previous: Map<String, Int>, oldestFirst: List<String>, options: List<BattleActionOption>): Map<String, Int> {
        val used = oldestFirst.firstNotNullOfOrNull { cast.matchEntire(it)?.groupValues?.get(1) }
            ?: "Scan".takeIf { oldestFirst.any { line -> line.startsWith("Scanning ") } }
        val remaining = when {
            oldestFirst.any { it.startsWith("Initializing ") } -> mutableMapOf()
            oldestFirst.any(zeroTurnItem::matches) -> previous.toMutableMap()
            used != null || oldestFirst.any(attack::containsMatchIn) ->
                previous.filterValues { it > 1 }.mapValues { it.value - 1 }.toMutableMap()
            else -> mutableMapOf() // The duration of this action could not be verified.
        }
        for (line in oldestFirst) {
            if (line.startsWith("Cooldown expired for ")) remaining.remove(line.removePrefix("Cooldown expired for ").removeSuffix("."))
        }
        val option = options.firstOrNull { it.name == used && (it.mode == "items" || (it.cooldown ?: 0) > 0) }
        val base = if (option?.mode == "items") 40 else option?.cooldown
        if (base != null && used != null) remaining[used] = base
        return remaining
    }
}
