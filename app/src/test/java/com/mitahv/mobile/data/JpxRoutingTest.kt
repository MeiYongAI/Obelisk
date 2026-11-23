package com.mitahv.mobile.data

import com.mitahv.mobile.model.*
import org.junit.Assert.*
import org.junit.Test

class JpxRoutingTest {
    private val staff = BattleStyle.Staff
    private val tower = BattleScene.Tower
    private fun profile(id: Long, mode: String, count: Int = 1) = JpxEntry(id, mode, "pack.json", "", count, count, false)
    private val general = profile(1,"Staff_General")
    private val exact = profile(2,"Staff_Tower")
    private val alternate = profile(3,"Staff_Arena")
    private val saved = JpxCatalog(listOf(alternate, exact, general))

    @Test fun directThenGeneralWithDeterministicVariantsAndNoGuessAtStyle() {
        val later = exact.copy(id = 4)
        val config = saved.copy(entries = listOf(later) + saved.entries)
        assertEquals(StrategyMatch(exact, MatchReason.Exact), config.resolve(staff,tower))
        assertEquals(StrategyMatch(general, MatchReason.General), config.resolve(staff,null))
        assertNull(config.resolve(null, tower)); assertNull(config.resolve(BattleStyle.OneMage, tower))
        val empty = exact.copy(rules = 4, publishedRules = 0, changed = true)
        assertEquals(general, saved.copy(entries = listOf(empty, general)).resolve(staff,tower)?.entry)
        assertNull(saved.copy(entries = listOf(empty)).resolve(staff,tower))
    }

    @Test fun assignmentIsSceneAndStyleScopedWithIndependentGeneralAndManualOverride() {
        val config = saved.copy(routes = mapOf(staff.slot(tower) to alternate.id, staff.slot(BattleScene.General) to exact.id))
        assertEquals(StrategyMatch(alternate, MatchReason.Assigned), config.resolve(staff,tower))
        assertEquals(StrategyMatch(exact, MatchReason.AssignedGeneral), config.resolve(staff,BattleScene.Item))
        assertEquals(StrategyMatch(alternate, MatchReason.Exact), config.resolve(staff,BattleScene.Arena))
        assertNull(config.resolve(BattleStyle.TwoHanded,tower))
        assertEquals(StrategyMatch(general, MatchReason.Manual), config.copy(manual = true, manualId = 1).resolve(null,null))
        assertNull(config.copy(manual = true).resolve(staff,tower))
    }

    @Test fun recognizesOnlyInitializationLogsSeparatingArenaFromRingOfBlood() {
        val lines = mapOf("arena challenge #24 (Round 58 / 60)" to BattleScene.Arena,
            "arena challenge #105 (Round 1 / 1)" to BattleScene.Colosseum, "random encounter" to BattleScene.Encounter,
            "Grindfest" to BattleScene.Battle1000, "Item World" to BattleScene.Item, "The Tower (Floor 5)" to tower)
        for ((line, scene) in lines) assertEquals(scene, BattleScene.fromLog(listOf("Player hits The Tower.", "Initializing $line ...")))
        assertNull(BattleScene.fromLog(listOf("The Tower", "Spawned Monster A: Initializing Item World")))
        assertEquals(BattleScene.Colosseum,BattleScene.fromLobby(HvRoute.RingOfBlood)); assertNull(BattleScene.fromLobby(HvRoute.Character))
    }

    @Test fun physicalMageDetectionRequiresCompleteBonusesButStaffAndUnarmedDoNot() {
        val all = SpellSchool.entries.associateWith { 0.0 }
        for ((weapons, styles) in listOf(("Rapier" to "Buckler") to ("OneHanded" to "1H_Mage"),
            ("Estoc" to "") to ("TwoHanded" to "2H_Mage"), ("Rapier" to "Axe") to ("DualWielding" to "DW_Mage"),
            ("Katana" to "Wakizashi") to ("NitenIchiryu" to "NI_Mage"))) {
            val base = BattleLoadout(null,weapons.first,weapons.second,all)
            val threshold = base.family!!.threshold!!.toDouble()
            assertEquals(styles.first, base.copy(bonuses = all + (SpellSchool.Fire to threshold)).style?.key)
            assertEquals(styles.second, base.copy(bonuses = all + (SpellSchool.Dark to threshold+0.1)).style?.key)
            assertNull(base.copy(bonuses = emptyMap()).style)
        }
        assertEquals(staff, BattleLoadout(null,"Oak Staff","", emptyMap()).style)
        assertEquals(BattleStyle.Unarmed, BattleLoadout(null,"","",emptyMap()).style)
    }
}
