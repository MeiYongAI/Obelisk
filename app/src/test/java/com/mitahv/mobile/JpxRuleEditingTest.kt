package com.mitahv.mobile

import com.mitahv.mobile.data.JpxFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class JpxRuleEditingTest {
    @Test fun legacyGroupsOfferTheirJpxActionsAndModernGroupsOfferAllActions() {
        assertEquals(listOf("spellSupport", "item", "toggle", "stop"), JpxFields.typesForGroup("supports"))
        assertEquals("target", JpxFields.typesForGroup("attacks").first())
        assertFalse("item" in JpxFields.typesForGroup("attacks"))
        assertEquals(JpxFields.types, JpxFields.typesForGroup("rules"))
        assertEquals(JpxFields.types, JpxFields.typesForGroup("kb_Space"))
    }

    @Test fun smartDebuffCreatedInTheEditorHasUsableCastingLimits() {
        val rule = ruleTemplate("smartDebuff")
        assertEquals(3, rule["targetCount"].asInt)
        assertEquals(0, rule["tailSkip"].asInt)
        assertEquals(10, rule["maxAtFirst"].asInt)
        assertEquals(10, rule["minMonstersLeft"].asInt)
        assertFalse(rule["bottomUp"].asBoolean)
    }

    @Test fun indicesCanSpanFirstToLastWhileCountsAndOffsetsKeepTheirOwnOrdering() {
        assertEquals("[1,-1]", conditionRange("tIndex", "1", "-1").toString())
        assertEquals("[-3,-1]", conditionRange("tIndex", "-3", "-1").toString())
        assertEquals("[0,0.25]", conditionRange("pHP", "0", "0.25").toString())
        assertThrows(IllegalArgumentException::class.java) { conditionRange("pHP", "1", "0") }
        assertThrows(IllegalArgumentException::class.java) { conditionRange("offset", "1", "-1") }
        assertThrows(IllegalArgumentException::class.java) { conditionRange("matched", "0.5", "1") }
    }
}
