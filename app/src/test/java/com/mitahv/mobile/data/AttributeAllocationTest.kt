package com.mitahv.mobile.data

import com.mitahv.mobile.model.CharacterAttribute
import com.mitahv.mobile.model.CharacterPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttributeAllocationTest {
    @Test
    fun matchesNextLevelCostsFromSuppliedPersistentAndIsekaiPages() {
        // Values from the saved pages' *_left cells, not a second copy of the formula.
        assertEquals(50_575_892L, AttributeAllocation.nextExperienceCost(354))
        assertEquals(52_190_842L, AttributeAllocation.nextExperienceCost(355))
        assertEquals(256_912_513L, AttributeAllocation.nextExperienceCost(405))
        assertEquals(1_329_873_651L, AttributeAllocation.nextExperienceCost(455))
        assertEquals(66_218_002_795L, AttributeAllocation.nextExperienceCost(567))
        assertEquals(68_677_671_952L, AttributeAllocation.nextExperienceCost(568))
        assertEquals(0L, AttributeAllocation.totalExperienceCost(0))
        assertEquals(6L, AttributeAllocation.totalExperienceCost(1))
    }

    @Test
    fun preservesLargeBudgetAndChecksUnaffordablePersistentAttribute() {
        val strength = attribute("str", 405)
        val wisdom = attribute("wis", 568)
        val page = page(61_496_539_988L, strength, wisdom)

        assertEquals(61_496_539_988L, AttributeAllocation.remainingExperience(page, emptyMap()))
        assertTrue(AttributeAllocation.canIncrease(page, strength, emptyMap()))
        assertFalse(AttributeAllocation.canIncrease(page, wisdom, emptyMap()))
        val changed = AttributeAllocation.adjust(page, strength, emptyMap(), 1)
        assertEquals(406, AttributeAllocation.currentValue(strength, changed))
        assertEquals(61_239_627_475L, AttributeAllocation.remainingExperience(page, changed))
    }

    @Test
    fun doesNotAddSnapshotPendingDeltaTwice() {
        // The original server value was 100, but the saved DOM already displays 103.
        val strength = attribute("str", 103, pendingDelta = 3)
        val page = page(100_000L, strength)

        assertEquals(103, AttributeAllocation.currentValue(strength, emptyMap()))
        assertEquals(100_000L, AttributeAllocation.remainingExperience(page, emptyMap()))
        val increased = AttributeAllocation.adjust(page, strength, emptyMap(), 1)
        assertEquals(4, increased["str"])
        assertEquals(104, AttributeAllocation.currentValue(strength, increased))
        assertEquals(80_207L, AttributeAllocation.remainingExperience(page, increased))

        val decreased = AttributeAllocation.adjust(page, strength, emptyMap(), -1)
        assertEquals(2, decreased["str"])
        assertEquals(102, AttributeAllocation.currentValue(strength, decreased))
        assertEquals(119_110L, AttributeAllocation.remainingExperience(page, decreased))
    }

    @Test
    fun canRemoveCommittedPointsWithNegativeDeltasButStopsAtZero() {
        val strength = attribute("str", 2)
        val page = page(0L, strength)

        val changed = AttributeAllocation.adjustBatch(page, strength, emptyMap(), -1, 100)

        assertEquals(-2, changed["str"])
        assertEquals(0, AttributeAllocation.currentValue(strength, changed))
        assertEquals(17L, AttributeAllocation.remainingExperience(page, changed))
        assertFalse(AttributeAllocation.canDecrease(strength, changed))
        assertEquals(changed, AttributeAllocation.adjust(page, strength, changed, -1))
    }

    @Test
    fun negativeSnapshotDeltaKeepsTheOriginalSubmitBaseline() {
        val strength = attribute("str", 2, pendingDelta = -3)
        val page = page(0L, strength)

        val changed = AttributeAllocation.adjustBatch(page, strength, emptyMap(), -1, 10)

        assertEquals(-5, changed["str"])
        assertEquals(0, AttributeAllocation.currentValue(strength, changed))
        assertEquals(17L, AttributeAllocation.remainingExperience(page, changed))
    }

    @Test
    fun batchUsesOnlyAffordableStepsAndAllowsExactBudget() {
        val strength = attribute("str", 0)
        val page = page(17L, strength)

        val one = AttributeAllocation.adjustBatch(page, strength, emptyMap(), 1, 1)
        assertEquals(1, one["str"])
        assertEquals(11L, AttributeAllocation.remainingExperience(page, one))
        assertTrue(AttributeAllocation.canIncrease(page, strength, one))

        for (steps in listOf(10, 100)) {
            val changed = AttributeAllocation.adjustBatch(page, strength, emptyMap(), 1, steps)
            assertEquals(2, changed["str"])
            assertEquals(0L, AttributeAllocation.remainingExperience(page, changed))
            assertFalse(AttributeAllocation.canIncrease(page, strength, changed))
        }
    }

    @Test
    fun attributesShareBudgetAndUndoRestoresIt() {
        val strength = attribute("str", 0)
        val dexterity = attribute("dex", 0)
        val page = page(17L, strength, dexterity)
        val first = AttributeAllocation.adjust(page, strength, emptyMap(), 1)
        val second = AttributeAllocation.adjust(page, dexterity, first, 1)

        assertEquals(mapOf("str" to 1), first)
        assertEquals(5L, AttributeAllocation.remainingExperience(page, second))
        assertFalse(AttributeAllocation.canIncrease(page, strength, second))
        val undo = AttributeAllocation.adjust(page, dexterity, second, -1)
        assertEquals(11L, AttributeAllocation.remainingExperience(page, undo))
        assertTrue(AttributeAllocation.canIncrease(page, strength, undo))
    }

    @Test
    fun overflowingCostsCannotProduceFreeUpgrades() {
        val strength = attribute("str", 950)
        val page = page(Long.MAX_VALUE, strength)

        assertEquals(Long.MAX_VALUE, AttributeAllocation.totalExperienceCost(950))
        assertEquals(Long.MAX_VALUE, AttributeAllocation.nextExperienceCost(950))
        assertFalse(AttributeAllocation.canIncrease(page, strength, emptyMap()))
        assertEquals(emptyMap<String, Int>(), AttributeAllocation.adjust(page, strength, emptyMap(), 1))
    }

    @Test
    fun refundsDoNotWrapLongBudgetNegative() {
        val strength = attribute("str", 100)
        val dexterity = attribute("dex", 100)
        val page = page(Long.MAX_VALUE - 1L, strength, dexterity)

        assertEquals(
            Long.MAX_VALUE,
            AttributeAllocation.remainingExperience(page, mapOf("str" to -1, "dex" to -1)),
        )
    }

    private fun attribute(key: String, value: Int, pendingDelta: Int = 0) = CharacterAttribute(
        key = key,
        name = key,
        value = value,
        equipmentBonus = "",
        secondaryBonus = "",
        pendingDelta = pendingDelta,
        nextExperience = 0L,
    )

    private fun page(availableExperience: Long, vararg attributes: CharacterAttribute) = CharacterPage(
        worldName = "Persistent",
        level = "",
        stamina = "",
        personaId = "1",
        personaName = "Test",
        personaOptions = emptyList(),
        usedPersonaSlots = "",
        usedPersonaCount = 1,
        personaSlotCapacity = 3,
        canClonePersona = true,
        canCreateBlankPersona = true,
        availableExperience = availableExperience,
        attributes = attributes.toList(),
        proficiencyGroups = emptyList(),
        statGroups = emptyList(),
    )
}
