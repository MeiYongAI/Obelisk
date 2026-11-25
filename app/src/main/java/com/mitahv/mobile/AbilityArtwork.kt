package com.mitahv.mobile

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp

private object AbilityGlyphs {
    val heart = GameGlyph(
        "M16 27C13 24 4 18 4 12C4 5 12 3 16 10C20 3 28 5 28 12C28 18 19 24 16 27Z",
        "M6 16H11L14 11L17 19L20 14H26L25 17H22L17 24L14 17L13 19H9Z")
    val mana = GameGlyph(
        "M16 3C15 9 6 15 6 21C6 26 11 29 16 29C23 29 27 24 26 19C25 14 20 10 16 3Z",
        "M15 13C14 17 10 18 10 22Q10 25 14 26L13 23Q12 21 15 18Z")
    val spirit = GameGlyph(
        "M16 4L21 12L28 15L21 19L16 28L11 20L4 16L11 12Z",
        "M16 9L18 14L23 16L18 17L16 23L15 17L11 16L15 15Z")
    val vitality = GameGlyph(
        "M5 11Q5 5 11 6Q15 6 16 10Q19 4 24 7Q30 11 24 18L16 27L8 20Q5 17 5 11Z",
        "M18 12Q17 15 14 18Q12 22 17 23Q23 23 21 18Z")
    val sword = GameGlyph(
        "M23 5L28 4L26 12L15 23L11 20L8 23L10 25L7 28L4 25L8 21L5 18L7 16L11 18Z",
        "M24 8L23 12L13 21L12 19Z")
    val greatsword = GameGlyph(
        "M22 4L28 4L27 11L18 20L18 22L15 24L12 21L8 26L6 28L3 25L10 19L7 16L10 13L13 14Z",
        "M24 6L24 11L15 18L14 16Z")
    val blades = GameGlyph(
        "M4 4L10 7L20 19L22 17L25 20L22 23L26 26L24 28L19 24L16 25L14 23L16 21L5 10ZM28 4L27 11L9 27L6 26L5 24L10 20L8 17L10 15L13 17L23 7Z",
        "M24 9L23 12L15 19L14 18ZM7 8L10 10L15 16L13 16Z")
    val aim = GameGlyph(
        "M16 4L20 9L16 8L12 9ZM4 16L9 12L8 16L9 20ZM28 16L23 20L24 16L23 12ZM16 28L12 23L16 24L20 23ZM16 10L22 16L16 22L10 16Z",
        "M16 13L19 16L16 19L13 16Z")
    val impact = GameGlyph(
        "M16 4L19 13L26 8L23 16L29 18L21 21L24 27L16 23L9 28L10 21L3 22L9 16L5 9L13 12Z",
        "M18 11L17 16L21 16L13 23L15 18L11 18Z")
    val shield = GameGlyph(
        "M16 4L26 8V16Q26 23 16 28Q6 23 6 16V8Z",
        "M16 7L22 9L16 12L10 9ZM9 12L13 14V22Q10 20 9 16Z")
    val parry = GameGlyph(
        "M5 7L15 4L23 8L20 12L15 9L10 11V16L8 19Q5 17 5 14ZM25 8L28 6L27 13L16 24L13 22L8 27L5 25L11 20L9 17L11 15L15 18Z",
        "M24 11L23 14L16 21L15 20Z")
    val staff = GameGlyph(
        "M7 25L18 12L17 7L21 3L26 4L28 8L25 13L21 14L10 28L6 28Z",
        "M21 6L24 5L26 8L23 11L20 10Z")
    val speed = GameGlyph(
        "M4 19L13 17L23 6L28 5L26 12L13 25L6 27L10 21ZM4 12L15 10L12 13L5 15Z",
        "M12 21L23 11L20 17L13 23Z")
    val crush = GameGlyph(
        "M6 9L14 4L23 12L16 20L12 17L8 23L5 27L3 24L10 15L6 12Z",
        "M9 9L14 6L20 12L17 15Z")
    val pierce = GameGlyph(
        "M27 4L24 16L19 16L17 14L8 25L6 28L3 25L15 12L15 9Z",
        "M24 7L21 13L17 12Z")
    val slash = GameGlyph(
        "M26 5Q23 18 7 27L5 24Q17 16 20 11L10 17Q16 10 26 5Z",
        "M26 13Q22 22 13 26L15 22Z")
    val ward = "M16 4L26 8V15C26 21 20 26 16 28C11 25 6 21 6 15V8Z"
    val fireWard = GameGlyph(ward, "M17 9C18 13 22 13 21 18Q19 22 16 23Q10 21 11 16L14 12V15Q17 13 17 9Z")
    val iceWard = GameGlyph(ward, "M16 9L21 15L16 23L11 15ZM16 12L14 16L16 19Z")
    val shockWard = GameGlyph(ward, "M17 9H21L17 15H21L12 23L15 17H11Z")
    val windWard = GameGlyph(ward, "M10 14Q17 7 23 12Q22 16 14 16L10 18L11 15L18 13ZM13 20Q18 16 21 17Q18 22 13 22Z")
    val spellbook = GameGlyph(
        "M4 7Q10 4 16 9Q22 4 28 7V24Q22 22 16 27Q10 22 4 24Z",
        "M7 10Q11 9 14 12V14Q11 12 7 12ZM18 12Q22 9 25 10V12Q21 12 18 14ZM15 12H17V23H15Z")
    val weave = GameGlyph(
        "M16 3L21 10L16 16L11 10ZM5 14L11 12L15 18L10 24L5 21ZM27 14L27 21L22 24L17 18L21 12ZM16 20L21 26L16 29L11 26Z",
        "M16 6L19 10L16 12ZM7 16L11 14L12 17L9 19Z")
    val crown = GameGlyph(
        "M4 9L12 14L16 5L20 14L28 9L24 23H8ZM9 25H23V28H9Z",
        "M16 13L19 17L16 20L13 17Z")
    val theft = GameGlyph(
        "M20 4L26 10L21 18L16 12ZM3 20L9 17L15 18L20 17Q22 17 21 19L15 22L20 22L26 17Q28 17 27 20L21 26L12 28L6 25Z",
        "M21 7L24 10L21 14L19 11Z")
    val net = GameGlyph(
        "M4 12L12 4L14 6L6 14ZM6 16L16 6L18 8L8 18ZM8 20L20 8L22 10L10 22ZM10 24L24 10L26 12L12 26Z",
        "M9 7L11 5L27 21L25 23ZM5 13L7 11L22 26L20 28Z")
    val spindle = GameGlyph(
        "M16 3L26 11L23 20L16 28L9 20L6 11Z",
        "M16 7L20 12L16 21L12 12Z")
}

private fun abilityArt(name: String, family: String): Pair<GameGlyph, ArtPalette> {
    val spell = when (name) {
        "Conflagration" -> "Fiery Blast"
        "Cryomancy" -> "Freeze"
        "Havoc" -> "Shockblast"
        "Tempest" -> "Gale"
        "Better Spark" -> "Spark of Life"
        "Better Banish" -> "Banishment"
        "Better Paradise" -> "Paradise Lost"
        "Mind Control" -> "Confuse"
        "Stronger Spirit" -> "Spirit Shield"
        "Holy Imperil", "Dark Imperil" -> "Imperil"
        "Soul Fire" -> "Fiery Blast"
        "Ripened Soul" -> "Drain"
        else -> name.removePrefix("Better ").removePrefix("Faster ")
    }
    battleGlyphFor(null, spell)?.let { art ->
        return when (name) {
            "Holy Imperil", "Soul Fire" -> art.first to ArtPalette.Light
            "Dark Imperil", "Ripened Soul" -> art.first to ArtPalette.Shadow
            else -> art
        }
    }
    return when (name) {
        "HP Tank", "Heavy HP" -> AbilityGlyphs.heart to ArtPalette.Life
        "MP Tank", "Cloth MP" -> AbilityGlyphs.mana to ArtPalette.Frost
        "SP Tank" -> AbilityGlyphs.spirit to ArtPalette.Spirit
        "Light HP/MP" -> AbilityGlyphs.vitality to ArtPalette.Life
        "1H Damage" -> AbilityGlyphs.sword to ArtPalette.Steel
        "2H Damage" -> AbilityGlyphs.greatsword to ArtPalette.Steel
        "DW Damage" -> AbilityGlyphs.blades to ArtPalette.Steel
        "1H Accuracy", "2H Accuracy", "DW Accuracy", "Light Acc" -> AbilityGlyphs.aim to ArtPalette.Steel
        "Staff Accuracy", "Cloth Spellacc" -> AbilityGlyphs.aim to ArtPalette.Arcane
        "DW Crit", "Light Crit" -> AbilityGlyphs.impact to ArtPalette.Light
        "Cloth Spellcrit" -> AbilityGlyphs.impact to ArtPalette.Storm
        "1H Block" -> AbilityGlyphs.shield to ArtPalette.Steel
        "2H Parry" -> AbilityGlyphs.parry to ArtPalette.Steel
        "Staff Damage" -> AbilityGlyphs.staff to ArtPalette.Earth
        "Staff Spell Damage" -> AbilityGlyphs.staff to ArtPalette.Arcane
        "Light Speed" -> AbilityGlyphs.speed to ArtPalette.Gale
        "Cloth Castspeed" -> AbilityGlyphs.speed to ArtPalette.Arcane
        "Heavy Crush" -> AbilityGlyphs.crush to ArtPalette.Earth
        "Heavy Prcg" -> AbilityGlyphs.pierce to ArtPalette.Steel
        "Heavy Slsh" -> AbilityGlyphs.slash to ArtPalette.Steel
        "Flame Spike Shield" -> AbilityGlyphs.fireWard to ArtPalette.Ember
        "Frost Spike Shield" -> AbilityGlyphs.iceWard to ArtPalette.Frost
        "Shock Spike Shield" -> AbilityGlyphs.shockWard to ArtPalette.Storm
        "Storm Spike Shield" -> AbilityGlyphs.windWard to ArtPalette.Gale
        "Ether Theft" -> AbilityGlyphs.theft to ArtPalette.Frost
        "Spirit Theft" -> AbilityGlyphs.theft to ArtPalette.Spirit
        "Better MagNet" -> AbilityGlyphs.net to ArtPalette.Arcane
        "Sorcery" -> AbilityGlyphs.spellbook to ArtPalette.Arcane
        "Elementalism" -> AbilityGlyphs.weave to ArtPalette.Storm
        "Archmage" -> AbilityGlyphs.crown to ArtPalette.Light
        else -> when (family) {
            "b" -> AbilityGlyphs.shield to ArtPalette.Steel
            "p" -> AbilityGlyphs.theft to ArtPalette.Shadow
            "g" -> AbilityGlyphs.spellbook to ArtPalette.Spirit
            else -> AbilityGlyphs.spindle to ArtPalette.Arcane
        }
    }
}

@Composable
internal fun AbilityArtwork(name: String, family: String, size: Dp, acquired: Boolean = true) {
    val modifier = Modifier.size(size).alpha(if (acquired) 1f else .62f)
    val potion = when (name) {
        "Better Health Pots" -> "Health Potion"
        "Better Mana Pots" -> "Mana Potion"
        "Better Spirit Pots" -> "Spirit Potion"
        else -> null
    }
    if (potion != null) {
        ItemArtwork(potion, "", modifier)
    } else {
        val (glyph, palette) = remember(name, family) { abilityArt(name, family) }
        GameArt(glyph, palette, modifier)
    }
}
