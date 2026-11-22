package com.mitahv.mobile.data

import com.mitahv.mobile.model.CharacterAttribute
import com.mitahv.mobile.model.CharacterPage
import java.math.BigInteger
import kotlin.math.pow

internal object AttributeAllocation {
    // The HTML display already includes the page's pending delta. Submitted deltas
    // remain relative to the server's committed attribute value.
    fun currentValue(attribute: CharacterAttribute, deltas: Map<String, Int>): Int =
        attribute.value + (deltas[attribute.key] ?: attribute.pendingDelta) - attribute.pendingDelta

    fun remainingExperience(page: CharacterPage, deltas: Map<String, Int>): Long {
        val remaining = page.attributes.fold(BigInteger.valueOf(page.availableExperience)) { budget, attribute ->
            budget + BigInteger.valueOf(totalExperienceCost(attribute.value)) -
                BigInteger.valueOf(totalExperienceCost(currentValue(attribute, deltas)))
        }
        return remaining.coerceIn(LONG_MIN, LONG_MAX).toLong()
    }

    fun canIncrease(page: CharacterPage, attribute: CharacterAttribute, deltas: Map<String, Int>): Boolean {
        val nextCost = nextExperienceCost(currentValue(attribute, deltas))
        return nextCost != Long.MAX_VALUE && nextCost <= remainingExperience(page, deltas)
    }

    fun canDecrease(attribute: CharacterAttribute, deltas: Map<String, Int>): Boolean =
        currentValue(attribute, deltas) > 0

    fun adjust(
        page: CharacterPage,
        attribute: CharacterAttribute,
        deltas: Map<String, Int>,
        direction: Int,
    ): Map<String, Int> = adjustBatch(page, attribute, deltas, direction, 1)

    fun adjustBatch(
        page: CharacterPage,
        attribute: CharacterAttribute,
        deltas: Map<String, Int>,
        direction: Int,
        steps: Int,
    ): Map<String, Int> {
        if (direction == 0 || steps <= 0) return deltas
        var updated = deltas
        repeat(steps) {
            if (direction > 0 && !canIncrease(page, attribute, updated)) return updated
            if (direction < 0 && !canDecrease(attribute, updated)) return updated
            val currentDelta = updated[attribute.key] ?: attribute.pendingDelta
            updated = updated + (attribute.key to currentDelta + direction.sign)
        }
        return updated
    }

    fun nextExperienceCost(level: Int): Long {
        if (level == Int.MAX_VALUE) return Long.MAX_VALUE
        val nextTotal = totalExperienceCost(level + 1)
        if (nextTotal == Long.MAX_VALUE) return Long.MAX_VALUE
        return nextTotal - totalExperienceCost(level)
    }

    fun totalExperienceCost(level: Int): Long {
        if (level <= 0) return 0L
        val exponent = 2.5475566751265.pow(1.0 + level / 950.0)
        val cost = (level + 1.0).pow(exponent)
        return Math.round(cost)
    }

    private val Int.sign: Int get() = if (this > 0) 1 else -1
    private val LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE)
    private val LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE)
}
