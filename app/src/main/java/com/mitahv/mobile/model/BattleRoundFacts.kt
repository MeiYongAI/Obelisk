package com.mitahv.mobile.model

/** Server log facts for this round. A mid-round login does not imply zero previous casts. */
data class BattleRoundFacts(
    val current: Int? = null, val total: Int? = null, val floor: Int? = null,
    val sawStart: Boolean = false, val casts: Map<String, Int> = emptyMap(), val maxHealth: Map<Int, Int> = emptyMap(),
    val history: List<String> = emptyList(),
    val priorRoundCasts: Map<String, Int> = emptyMap(),
    val priorRoundsKnown: Boolean = false,
    val monsterIds: Map<String, Long> = emptyMap(),
) {
    fun totalCasts(name: String): Int = (priorRoundCasts[name] ?: 0) + (casts[name] ?: 0)

    fun after(previous: BattleRoundFacts) = copy(
        priorRoundCasts = (previous.priorRoundCasts.keys + previous.casts.keys).associateWith(previous::totalCasts),
        priorRoundsKnown = sawStart && previous.sawStart && (previous.priorRoundsKnown || previous.current == 1),
    )

    /** One server action per group, newest line first. Multi-hit attacks and procs share one group. */
    fun observe(newestFirst: List<String>): BattleRoundFacts {
        if (newestFirst.isEmpty()) return this
        var facts = this
        val oldestFirst = newestFirst.asReversed()
        for (line in oldestFirst) {
            if (line.startsWith("Initializing ")) {
                val round = roundPattern.find(line)
                // Random encounters have a single round and their start line can omit the counter.
                val singleRound = if (line.startsWith("Initializing random encounter", ignoreCase = true)) 1 else null
                facts = BattleRoundFacts(round?.groupValues?.get(1)?.toIntOrNull() ?: singleRound,
                    round?.groupValues?.get(2)?.toIntOrNull() ?: singleRound,
                    floorPattern.find(line)?.groupValues?.get(1)?.toIntOrNull(), sawStart = true)
            }
            val spawn = spawnPattern.matchEntire(line)
            if (spawn != null) spawn.groupValues[4].toIntOrNull()?.let { hp ->
                val id = spawn.groupValues[2].toLongOrNull()?.takeIf { it > 0 }
                facts = facts.copy(maxHealth = facts.maxHealth + ((spawn.groupValues[1][0] - 'A') to hp),
                    monsterIds = if (id == null) facts.monsterIds else facts.monsterIds + (spawn.groupValues[3] to id))
            }
        }
        if (oldestFirst.any { it.startsWith("Initializing ") || it.startsWith("Spawned Monster ") }) return facts
        // The first line after the separator identifies the player's action. Spirit exhaustion and
        // target effect messages can precede it, and Scan uses a separate formatted log entry.
        val first = oldestFirst.firstOrNull { it != "Spirit Stance Exhausted" && " gains the effect " !in it } ?: return facts
        val name = when {
            first.startsWith("Scanning ") -> "Scan"
            first.contains("Spirit Stance Engaged") -> "Spirit"
            first.contains("Defending.") -> "Defend"
            first.contains("Focusing.") -> "Focus"
            else -> castPattern.matchEntire(first)?.groupValues?.get(1) ?: "Attack"
        }
        val counts = facts.casts.toMutableMap()
        counts[name] = (counts[name] ?: 0) + 1
        if ("You gain the effect Cloak of the Fallen." in oldestFirst)
            counts["Cloak of the Fallen"] = (counts["Cloak of the Fallen"] ?: 0) + 1
        return facts.copy(casts = counts, history = listOf(name) + facts.history)
    }
    companion object {
        private val roundPattern = Regex("""Round (\d+)\s*/\s*(\d+)""")
        private val floorPattern = Regex("""Floor (\d+)""")
        private val spawnPattern = Regex("""Spawned Monster ([A-J]): MID=(\d+) \((.+)\) LV=\d+ HP=(\d+)""")
        private val castPattern = Regex("""You (?:cast|use) ([\w -]+)\.?""")
    }
}
