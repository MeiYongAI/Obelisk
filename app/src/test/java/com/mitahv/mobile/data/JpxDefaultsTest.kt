package com.mitahv.mobile.data

import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class JpxDefaultsTest {
    private val counts = mapOf(
        "OneHanded_General" to 36, "OneHanded_Tower" to 33, "TwoHanded_General" to 27, "Staff_General" to 41,
        "1H_Mage_General" to 0, "2H_Mage_General" to 0, "DualWielding_General" to 0, "DW_Mage_General" to 0,
        "NitenIchiryu_General" to 0, "NI_Mage_General" to 0, "Unarmed_General" to 0
    )
    private fun battle(edition: JpxEdition) = File("src/main/assets", edition.asset("battle"))

    @Test fun fullDefaultsRetainGlobalSettingsAndAllModesInBothWorlds() {
        for (edition in JpxEdition.entries) for (world in listOf("Persistent", "Isekai")) {
            val defaults = battle(edition).inputStream().use { JpxDefaults(edition, it, world) }
            assertEquals(counts, defaults.ruleCounts)
            assertEquals(137, defaults.full.rules)
            assertEquals(battle(edition).readText(), defaults.full.source)
            assertTrue(JsonParser.parseString(defaults.full.source).asJsonObject.has("ctrlWidgetRows"))
        }
    }

    @Test fun singleModeKeepsTheOriginalRulesInOrderIncludingEmptyModes() {
        for (edition in JpxEdition.entries) {
            val defaults = battle(edition).inputStream().use { JpxDefaults(edition, it, "Isekai") }
            val full = JsonParser.parseString(defaults.full.source).asJsonObject
            for ((key, count) in counts) {
                val mode = defaults.mode(key)
                assertEquals(listOf(key), mode.modes)
                assertEquals(count, mode.rules)
                val payload = JsonParser.parseString(mode.source).asJsonObject.getAsJsonObject("config")
                assertEquals(setOf("battleVersion", key), payload.keySet())
                assertEquals(full["battleVersion"], payload["battleVersion"])
                assertEquals(full[key], payload[key])
            }
            assertThrows(IllegalArgumentException::class.java) { defaults.mode("Staff_Tower") }
        }
    }
}
