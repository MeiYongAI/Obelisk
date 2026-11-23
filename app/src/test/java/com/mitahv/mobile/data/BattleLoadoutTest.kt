package com.mitahv.mobile.data

import com.mitahv.mobile.model.*
import org.junit.Assert.*
import org.junit.Test

class BattleLoadoutTest {
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/character-sections/$name")).readText()
    private fun overview(html: String, vararg scripts: String) = CharacterSectionsParser.parse(
        html, HvRoute.Equipment, HvWorld.Persistent, supplementalScripts = scripts.toList()).battleLoadout

    private fun loadout(main: String?, off: String?, cold: Double = 147.2) = BattleLoadout("1", main, off,
        SpellSchool.entries.associateWith { if (it == SpellSchool.Cold) cold else 0.0 })

    @Test fun realOverviewUsesEquippedHandsAndSpellBonusNotMitigation() {
        val result = overview(fixture("equipment.html"), fixture("equipment-data.js"))!!
        assertEquals("1", result.set)
        assertEquals(WeaponFamily.Staff, result.family)
        assertEquals("", result.off)
        assertEquals(SpellSchool.Wind, result.school)
        assertEquals(399.8, result.maximum!!, 0.001)
        assertNull(result.isBattlemage)
    }

    @Test fun noMetadataOrSelectionIsNotAnEmptyHandOrCandidateWeapon() {
        val result = overview(fixture("equipment.html"))!!
        assertNull(result.main)
        assertNull(result.family)
        assertNull(overview(fixture("equipment-candidates-mainhand.html"), fixture("equipment-candidates-data.js")))
    }

    @Test fun customItemNameCannotChangeTheDetectedFamily() {
        val metadata = """var dynjs_equip={"303429104":{"n":"Legendary Mace of Slaughter","t":"My Staff"}};"""
        assertEquals(WeaponFamily.Two, overview(fixture("equipment.html"), metadata)!!.family)
    }

    @Test fun mageThresholdsAreStrictAndFamilySpecific() {
        for ((main, off, family) in listOf(Triple("Club", "Kite Shield", WeaponFamily.One),
            Triple("Axe", "Rapier", WeaponFamily.Dual), Triple("Mace", "", WeaponFamily.Two),
            Triple("Katana", "Wakizashi", WeaponFamily.Niten))) {
            val limit = family.threshold!!.toDouble()
            assertEquals(family, loadout(main, off).family)
            assertEquals(false, loadout(main, off, limit).isBattlemage)
            val mage = loadout(main, off, limit + 0.1)
            assertEquals(true, mage.isBattlemage)
            assertEquals(SpellSchool.Cold, mage.school)
        }
    }

    @Test fun emptyUnknownAndUnsupportedCombinationsAreDistinct() {
        assertEquals(WeaponFamily.One, loadout("Rapier", "").family)
        assertEquals(WeaponFamily.Unarmed, loadout("", "").family)
        assertNull(loadout("", null).family)
        assertNull(loadout("Mace", "Force Shield").family)
        assertNull(loadout("NotAStaff", "").family)
    }

    @Test fun tiedOrIncompleteBonusesDoNotGuessASchool() {
        val base = loadout("Mace", "")
        val tie = base.copy(bonuses = base.bonuses + (SpellSchool.Holy to 147.2))
        assertNull(tie.school)
        assertEquals(true, tie.isBattlemage)
        assertNull(loadout("Mace", "", 0.0).school)
        val partial = base.copy(bonuses = base.bonuses - SpellSchool.Fire)
        assertNull(partial.school)
        assertNull(partial.isBattlemage)
    }
}
