package com.mitahv.mobile.data

import org.junit.Assert.*
import org.junit.Test

class BattleTooltipParserTest {
    @Test fun decodesQuotedCommasEscapedApostrophesAndNumericDurations() {
        assertEquals(listOf("Mage's Blessing", "Magic, defense and focus.", "5"), BattleTooltipParser.arguments(
            "battle.set_infopane_effect('Mage\\'s Blessing','Magic, defense and focus.',5)", "set_infopane_effect"))
        assertEquals(listOf("Cure", "Heals", "0530", "64", "0", "2"), BattleTooltipParser.arguments(
            "battle.set_infopane_spell('Cure','Heals','0530',64,0,2)", "set_infopane_spell"))
    }

    @Test fun doesNotEvaluateNonliteralTooltipArguments() {
        assertNull(BattleTooltipParser.arguments("battle.set_infopane_effect('Name',window.secret,3)", "set_infopane_effect"))
        assertNull(BattleTooltipParser.arguments("other.set_infopane_effect('Name','Description',3)", "set_infopane_effect"))
    }
}
