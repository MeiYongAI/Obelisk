package com.mitahv.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.i18n.LocalGameTranslator

/** Only equipment-name call sites use this; player names and prose never enter the affix parser. */
@Composable
internal fun EquipmentNameText(
    name: String,
    modifier: Modifier = Modifier,
    originalName: String? = null,
    preserveName: Boolean = false,
    showOriginal: Boolean = false,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val translator = LocalGameTranslator.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val text = remember(name, originalName, preserveName, translator, dark) {
        equipmentNameText(name, originalName, preserveName, dark, translator::translate)
    }
    if (showOriginal && originalName != null && originalName != name) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text, style = style, color = color)
            EquipmentNameText(originalName, style = MaterialTheme.typography.bodyMedium)
        }
    } else Text(text, modifier, style = style, color = color)
}

internal fun equipmentNameText(
    name: String,
    originalName: String? = null,
    preserveName: Boolean = false,
    dark: Boolean,
    translate: (String) -> String,
): AnnotatedString {
    // dynjs.t is a custom display name when dynjs.n (the original name) is present.
    if (preserveName || originalName != null && originalName != name) {
        val quality = originalName?.let(::equipmentNameParts)?.firstOrNull()?.tone
        return buildAnnotatedString {
            withStyle(SpanStyle(color = quality?.color(dark) ?: Color.Unspecified)) { append(name) }
        }
    }
    val parts = equipmentNameParts(name) ?: return AnnotatedString(name)
    return buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index > 0) append(' ')
            withStyle(SpanStyle(color = part.tone?.color(dark) ?: Color.Unspecified)) {
                append(translate(part.text).trim())
            }
        }
    }
}

internal data class EquipmentNamePart(val text: String, val tone: EquipmentNameTone? = null)

// JPX-PLUS/HentaiVerse-Chinese.user.js equipment-name palette, adjusted for native
// foreground text on light/dark Material surfaces. The original words remain visible.
internal enum class EquipmentNameTone(private val light: Long, private val dark: Long) {
    Common(0xFF55565F, 0xFFC8C8D0),
    Fine(0xFF28672C, 0xFFB9FFB9),
    Exquisite(0xFF4B6216, 0xFFD7E698),
    Magnificent(0xFF1A6085, 0xFFA6DAF6),
    Legendary(0xFF873D89, 0xFFFFBBFF),
    Peerless(0xFF765700, 0xFFFFD760),
    Ultimate(0xFF963E14, 0xFFFFB38D),
    Fire(0xFFA32836, 0xFFFFB3B7),
    Ice(0xFF225B97, 0xFFA9CCFF),
    Lightning(0xFF665E00, 0xFFF4F375),
    Wind(0xFF28672C, 0xFFB1F9B1),
    Holy(0xFF65593F, 0xFFEFE2B5),
    Dark(0xFF44384D, 0xFFDDCEE9),
    Ethereal(0xFF53616C, 0xFFD9E2EB),
    Frost(0xFF00666B, 0xFFA0F4F4),
    Destruction(0xFF7D22A5, 0xFFE3AEFF),
    Slaughter(0xFFA32323, 0xFFFFB4AB);

    fun color(darkTheme: Boolean) = Color(if (darkTheme) dark else light)
}

private val equipmentGrades = mapOf(
    "Flimsy" to EquipmentNameTone.Common, "Crude" to EquipmentNameTone.Common,
    "Fair" to EquipmentNameTone.Common, "Average" to EquipmentNameTone.Common,
    "Superior" to EquipmentNameTone.Common, "Fine" to EquipmentNameTone.Fine,
    "Exquisite" to EquipmentNameTone.Exquisite, "Magnificent" to EquipmentNameTone.Magnificent,
    "Legendary" to EquipmentNameTone.Legendary, "Peerless" to EquipmentNameTone.Peerless,
    "Ultimate" to EquipmentNameTone.Ultimate,
)
private val equipmentPrefixes = setOf(
    "Fiery", "Arctic", "Shocking", "Tempestuous", "Hallowed", "Demonic", "Ethereal",
    "Reinforced", "Radiant", "Mystic", "Charged", "Frugal", "Mithril", "Agile", "Savage", "Shielding",
    "Amber", "Zircon", "Jade", "Cobalt", "Ruby", "Onyx",
)
private val equipmentBases = buildSet {
    addAll(listOf("Axe", "Club", "Rapier", "Shortsword", "Wakizashi", "Dagger", "Estoc", "Longsword",
        "Katana", "Scythe", "Mace", "Great Mace", "Swordchucks", "Staff", "Buckler", "Kite Shield", "Tower Shield", "Force Shield"))
    listOf("Oak", "Redwood", "Willow", "Katalox", "Ebony").forEach { add("$it Staff") }
    val materials = listOf("Silk", "Gossamer", "Cotton", "Ironsilk", "Drakehide", "Kevlar", "Leather",
        "Chain", "Plate", "Phase", "Shade", "Power", "Reactive")
    val pieces = listOf("Cap", "Helmet", "Robe", "Breastplate", "Cuirass", "Armor", "Gloves", "Gauntlets",
        "Pants", "Leggings", "Greaves", "Shoes", "Boots", "Sabatons")
    materials.forEach { material -> pieces.forEach { piece -> add("$material $piece") } }
}
private val equipmentSuffixes = setOf(
    "Slaughter", "Balance", "Swiftness", "the Vampire", "the Illithid", "the Banshee", "the Nimble",
    "the Battlecaster", "Destruction", "Focus", "Surtr", "Niflheim", "Mjolnir", "Freyr", "Heimdall", "Fenrir",
    "the Elementalist", "the Heaven-sent", "the Demon-fiend", "the Earth-walker", "the Curse-weaver",
    "the Barrier", "Warding", "Protection", "Dampening", "Stoneskin", "Deflection", "the Shadowdancer",
    "the Arcanist", "the Fleet", "Negation", "the Stone-skinned", "the Fire-eater", "the Frost-born",
    "the Thunder-child", "the Wind-waker", "the Thrice-blessed", "the Spirit-ward", "the Ox", "the Raccoon",
    "the Cheetah", "the Turtle", "Turtle", "the Fox", "the Owl", "Priestess", "the Hulk",
)

private fun equipmentAffixTone(affix: String): EquipmentNameTone? = when (affix) {
    "Fiery", "Ruby", "of Surtr", "of the Fire-eater" -> EquipmentNameTone.Fire
    "Arctic", "of Niflheim" -> EquipmentNameTone.Ice
    "Shocking", "Amber", "of Mjolnir", "of the Thunder-child" -> EquipmentNameTone.Lightning
    "Tempestuous", "Jade", "of Freyr", "of the Wind-waker" -> EquipmentNameTone.Wind
    "Hallowed", "Radiant", "Zircon", "of Heimdall", "of the Thrice-blessed" -> EquipmentNameTone.Holy
    "Demonic", "Onyx", "of Fenrir", "of the Spirit-ward" -> EquipmentNameTone.Dark
    "Ethereal" -> EquipmentNameTone.Ethereal
    "Cobalt", "of the Frost-born" -> EquipmentNameTone.Frost
    "of Destruction" -> EquipmentNameTone.Destruction
    "Savage", "of Slaughter" -> EquipmentNameTone.Slaughter
    else -> null
}

/** Parse complete original names, never isolated quality words in custom names or messages. */
internal fun equipmentNameParts(name: String): List<EquipmentNamePart>? {
    val source = name.trim()
    val grade = source.substringBefore(' ')
    val gradeTone = equipmentGrades[grade] ?: return null
    var remainder = source.removePrefix("$grade ")
    val prefix = remainder.substringBefore(' ').takeIf { it in equipmentPrefixes }
    if (prefix != null) remainder = remainder.removePrefix("$prefix ")
    val base = remainder.substringBefore(" of ")
    if (base !in equipmentBases) return null
    val suffix = remainder.substringAfter(" of ", "")
    if (" of " in remainder && suffix !in equipmentSuffixes) return null
    return buildList {
        add(EquipmentNamePart(grade, gradeTone))
        prefix?.let { add(EquipmentNamePart(it, equipmentAffixTone(it))) }
        add(EquipmentNamePart(base))
        if (suffix.isNotEmpty()) add(EquipmentNamePart("of $suffix", equipmentAffixTone("of $suffix")))
    }
}
