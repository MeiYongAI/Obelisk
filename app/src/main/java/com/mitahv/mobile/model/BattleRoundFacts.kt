package com.mitahv.mobile.model

/** Server log facts for this round. A mid-round login does not imply zero previous casts. */
data class BattleRoundFacts(
    val current: Int? = null, val total: Int? = null, val floor: Int? = null,
    val sawStart: Boolean = false, val casts: Map<String, Int> = emptyMap(), val maxHealth: Map<Int, Int> = emptyMap(),
) {
    fun observe(newestFirst: List<String>): BattleRoundFacts {
        if (newestFirst.isEmpty()) return this
        var facts = this
        for (line in newestFirst.asReversed()) {
            if (line.startsWith("Initializing ")) {
                val round = roundPattern.find(line)
                // Random encounters have a single round and their start line can omit the counter.
                val singleRound = if (line.startsWith("Initializing random encounter", ignoreCase = true)) 1 else null
                facts = BattleRoundFacts(round?.groupValues?.get(1)?.toIntOrNull() ?: singleRound,
                    round?.groupValues?.get(2)?.toIntOrNull() ?: singleRound,
                    floorPattern.find(line)?.groupValues?.get(1)?.toIntOrNull(), sawStart = true)
            }
            val spawn = spawnPattern.matchEntire(line)
            if (spawn != null) spawn.groupValues[2].toIntOrNull()?.let { hp ->
                facts = facts.copy(maxHealth = facts.maxHealth + ((spawn.groupValues[1][0] - 'A') to hp))
            }
            val name = when {
                line.startsWith("Scanning ") -> "Scan"
                else -> castPattern.matchEntire(line)?.groupValues?.get(1) ?: continue
            }
            facts = facts.copy(casts = facts.casts + (name to ((facts.casts[name] ?: 0) + 1)))
        }
        return facts
    }
    companion object {
        private val roundPattern = Regex("""Round (\d+)\s*/\s*(\d+)""")
        private val floorPattern = Regex("""Floor (\d+)""")
        private val spawnPattern = Regex("""Spawned Monster ([A-J]): MID=\d+ \(.+\) LV=\d+ HP=(\d+)""")
        private val castPattern = Regex("""You (?:cast|use) ([\w -]+)\.?""")
    }
}
