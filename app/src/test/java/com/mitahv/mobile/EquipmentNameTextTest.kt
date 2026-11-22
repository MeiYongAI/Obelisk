package com.mitahv.mobile

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.mitahv.mobile.i18n.HvTextTranslator
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class EquipmentNameTextTest {
    private val translate by lazy {
        val file = sequenceOf(File("src/main/assets/hv_zh.json"), File("app/src/main/assets/hv_zh.json"))
            .first { it.isFile }
        HvTextTranslator(file.readText()).translatorFor("https://hentaiverse.org/?s=Character&ss=eq")
    }

    @Test fun qualityAndAffixesKeepTheirMeaningInBothLanguages() {
        val name = "Legendary Ethereal Rapier of Slaughter"
        val english = equipmentNameText(name, dark = false, translate = { it })
        assertEquals(name, english.text)
        val chinese = equipmentNameText(name, dark = false, translate = translate)
        assertEquals("传奇 虚空(无负重干涉) 西洋剑(单) 杀戮(攻击+)", chinese.text)
        val coloredWords = chinese.spanStyles.filter { it.item.color != Color.Unspecified }
            .map { chinese.text.substring(it.start, it.end) }
        assertTrue(coloredWords.containsAll(listOf("传奇", "虚空(无负重干涉)", "杀戮(攻击+)")))
        assertEquals(EquipmentNameTone.Wind, equipmentNameParts("Average Chain Helmet of the Wind-waker")!!.last().tone)
        assertEquals(EquipmentNameTone.Peerless, equipmentNameParts("Peerless Radiant Phase Robe of Surtr")!!.first().tone)
        assertNotNull(equipmentNameParts("Magnificent Arctic Katalox Staff of Niflheim"))
    }

    @Test fun customNamesAndUnrecognizedTextAreNeverFragmentTranslated() {
        val original = "Peerless Fiery Rapier of Slaughter"
        listOf("Fire A N/A 🔥", "Legendary Leather Boots of the Wind-waker").forEach { custom ->
            val result = equipmentNameText(custom, originalName = original, dark = false, translate = { error("Custom name translated") })
            assertEquals(custom, result.text)
            assertEquals(EquipmentNameTone.Peerless.color(false), result.spanStyles.single().item.color)
        }
        listOf("Legendary player", "Average N/A", "Peerless Rapier of My Own Name", "Gift: Legendary Rapier of Slaughter").forEach { text ->
            assertNull(equipmentNameParts(text))
            assertEquals(text, equipmentNameText(text, dark = false, translate = { error("Unknown text translated") }).text)
        }
    }

    @Test fun colorsRemainReadableOnLightAndDarkEquipmentSurfaces() {
        listOf(false to lightColorScheme(), true to darkColorScheme()).forEach { (dark, scheme) ->
            val backgrounds = listOf(scheme.surface, scheme.surfaceContainerLow, scheme.surfaceContainerHigh, scheme.secondaryContainer)
            EquipmentNameTone.entries.forEach { tone -> backgrounds.forEach { background ->
                val foregroundLuminance = tone.color(dark).luminance()
                val backgroundLuminance = background.luminance()
                val ratio = (maxOf(foregroundLuminance, backgroundLuminance) + 0.05) /
                    (minOf(foregroundLuminance, backgroundLuminance) + 0.05)
                assertTrue("$tone dark=$dark contrast=$ratio", ratio >= 4.5)
            } }
        }
    }
}
