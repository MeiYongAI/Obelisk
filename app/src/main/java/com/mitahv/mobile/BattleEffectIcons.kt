package com.mitahv.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitahv.mobile.i18n.gameText
import com.mitahv.mobile.model.BattleEffect

private val effectSpellKeys = mapOf(
    "arcanemeditation" to "m4020", "coldslow" to "0730", "firedot" to "0130", "flee" to "sflee",
    "haste" to "0533", "heartseeker" to "m4021", "imperil" to "m_imperil", "protection" to "0532",
    "regen" to "1030", "shadowveil" to "0633", "sparklife" to "1133", "spiritshield" to "m4040",
)
private val effectSpellsByName = mapOf(
    "Weakened" to "Weaken", "Slowed" to "Slow", "Magically Snared" to "MagNet", "Silenced" to "Silence",
    "Imperiled" to "Imperil", "Asleep" to "Sleep", "Blinded" to "Blind", "Confused" to "Confuse",
    "Immobilized" to "Immobilize", "Hastened" to "Haste", "Fleeing" to "Flee",
    "Freezing Limbs" to "Freeze", "Searing Skin" to "Fiery Blast", "Turbulent Air" to "Gale",
    "Deep Burns" to "Shockblast", "Breached Defense" to "Smite", "Blunted Attack" to "Corruption",
    "Burning Soul" to "Drain", "Ripened Soul" to "Drain",
)

// Effects without a corresponding action in the spellbook.
private val manaMotes = GameGlyph("M16 4L22 14L16 24L10 14ZM6 16L10 22L6 27L3 22ZM24 6L28 11L24 16L21 11Z",
    "M16 8L18 14L14 17L13 14Z")
private val brokenArmor = GameGlyph("M5 8L15 4L18 6L12 15L16 17L11 25Q5 20 5 13ZM22 6L27 8V14Q26 22 17 28L14 26L21 16L17 14Z",
    "M7 10L13 7L10 11L7 13ZM23 10L25 9V14L22 18Z")
private val wound = GameGlyph("M6 20L19 5L12 18L9 25ZM13 21L25 8L19 20L14 27ZM21 22L28 14L24 25Z",
    "M7 19L14 11L10 18ZM15 20L20 14L17 20Z")
private val stagger = GameGlyph("M5 13L12 12L11 5L17 10L23 5L22 12L28 14L22 18L25 24L18 21L14 28L12 21L5 22L9 17Z",
    "M16 12L19 14L18 18L14 19L13 15Z")
private val riddle = GameGlyph("M16 3L27 12V21L16 29L5 21V12Z",
    "M11 12Q11 8 16 8Q21 8 21 12Q21 15 18 17L17 19H14V16Q18 13 18 12Q18 10 16 10Q14 10 14 12ZM14 21H17V24H14Z")
private val seal = GameGlyph("M16 3L27 10V22L16 29L5 22V10ZM8 12V20L16 25L24 20V12L16 7Z",
    "M16 10L21 16L16 22L11 16Z")
private fun effectArt(effect: BattleEffect): Pair<GameGlyph, ArtPalette> {
    battleGlyphFor(effectSpellKeys[effect.iconKey], effectSpellsByName[effect.name] ?: effect.name)?.let { return it }
    return when {
        effect.iconKey == "coalescemana" || effect.name in manaEffects -> manaMotes to ArtPalette.Frost
        effect.iconKey == "riddlemaster" || effect.name == "Blessing of the RiddleMaster" -> riddle to ArtPalette.Light
        effect.iconKey == "wpn_ap" || effect.name == "Penetrated Armor" -> brokenArmor to ArtPalette.Steel
        effect.name == "Bleeding Wound" || effect.name == "Vital Theft" -> wound to ArtPalette.Life
        effect.name == "Stunned" -> stagger to ArtPalette.Light
        effect.name == "Defending" -> stanceGlyph("defend")
        effect.name == "Focusing" -> stanceGlyph("focus")
        else -> seal to ArtPalette.Arcane
    }
}

private val manaEffects = setOf("Coalesced Mana", "Ether Tap", "Ether Theft", "Channeling")

@Composable
private fun BattleEffectImage(effect: BattleEffect, modifier: Modifier) {
    val potion = when (effect.iconKey) {
        "healthpot" -> "Health Draught"
        "manapot" -> "Mana Draught"
        "spiritpot" -> "Spirit Draught"
        else -> null
    }
    if (potion != null) {
        ItemArtwork(potion, "", modifier)
    } else {
        val (glyph, palette) = remember(effect.iconKey, effect.name) { effectArt(effect) }
        GameArt(glyph, palette, modifier)
    }
}

@Composable
internal fun BattleEffectBadge(effect: BattleEffect, compact: Boolean = false) {
    val turns = effect.duration.toIntOrNull()
    val name = gameText(effect.name)
    val description = if (turns == null) name else name + " · " + stringResource(R.string.game_ui_effect_duration, turns)
    val size = if (compact) Modifier.size(width = 21.dp, height = 22.dp) else Modifier.size(width = 30.dp, height = 32.dp)
    Box(size.clipToBounds()
        .clearAndSetSemantics { contentDescription = description }) {
        BattleEffectImage(effect, Modifier.matchParentSize())
        if (turns != null) {
            Text(turns.toString(),
                Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp)
                    .background(Color.Black.copy(alpha = .87f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 2.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 12.sp,
                    letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White, maxLines = 1, softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 11.sp, stepSize = 1.sp))
        }
    }
}

@Composable
internal fun BattleEffectStrip(effects: List<BattleEffect>, limit: Int, modifier: Modifier = Modifier, compact: Boolean = false) {
    // A short-lived effect is more useful in the preview than a permanent enchantment.
    val preview = effects.sortedBy { it.duration.toIntOrNull() ?: Int.MAX_VALUE }.take(limit)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (effect in preview) key(effect.name, effect.iconKey) { BattleEffectBadge(effect, compact) }
        if (effects.size > preview.size) Text("+${effects.size - preview.size}", style = MaterialTheme.typography.labelSmall,
            maxLines = 1, softWrap = false)
    }
}
