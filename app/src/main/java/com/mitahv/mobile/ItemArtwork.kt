package com.mitahv.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

private object ItemGlyphs {
    val draught = GameGlyph(
        "M12 4H20V7H19V11L23 15V25Q23 28 20 28H12Q9 28 9 25V15L13 11V7H12Z",
        "M12 5H20V7H12ZM11 16L14 13H17L14 16V21H11Z")
    val potion = GameGlyph(
        "M12 4H20V7H19V11C19 14 26 15 26 21C26 26 22 29 16 29C10 29 6 26 6 21C6 15 13 14 13 11V7H12Z",
        "M12 5H20V7H12ZM9 20Q10 16 14 15L15 17Q12 18 12 21L9 22Z")
    val elixir = GameGlyph(
        "M12 4H20V7H19V11L26 17V22L19 29H13L6 22V17L13 11V7H12Z",
        "M12 5H20V7H12ZM8 18L14 13L12 19L9 20ZM14 21L16 26L20 21Z")
    val infusion = GameGlyph(
        "M12 4H20V7H18V12L25 24Q27 28 22 28H10Q5 28 7 24L14 12V7H12Z",
        "M12 5H20V7H12ZM10 23L14 16L15 18L12 24Z")
    val scroll = GameGlyph(
        "M9 5H24Q28 5 28 10V12H23V23Q23 27 19 27H8Q4 27 4 23V20H9Z",
        "M13 9H20V11H13ZM13 14H19V16H13ZM7 22H18Q18 24 19 25H9Q7 25 7 22Z")
    val drink = GameGlyph("M11 5H22L21 8L23 11V25L21 28H11L9 25V11L11 8Z",
        "M12 6H21V8H12ZM17 11L12 18H16L14 24L21 15H17Z")
    val candy = GameGlyph("M4 10L10 12Q15 5 21 11L23 13L28 10L27 18L23 17Q18 26 11 20L9 18L5 22Z",
        "M12 13Q15 10 18 11L14 19Q12 19 11 17ZM20 13L21 15L18 20H16Z")
    val vase = GameGlyph("M14 14L12 11C7 11 5 7 6 4Q13 3 14 10L16 12L19 9Q18 4 26 4Q27 11 21 12L18 15H21L20 18C26 25 22 28 16 28C10 28 6 25 12 18L11 15H15Z",
        "M12 16H20V18H12ZM11 22L14 20L13 25H11Z")
    val crystal = GameGlyph("M4 18L6 12L11 15L14 7L20 3L24 10L21 18L26 14L28 19L22 26L12 29Z",
        "M15 9L19 6L17 19L12 25ZM22 21L26 17L23 24L20 26Z")
    val token = GameGlyph("M10 5H22L28 11V21L22 27H10L4 21V11Z",
        "M16 8L22 16L16 24L10 16ZM8 9L6 13L8 15L10 11Z")
    val binding = GameGlyph("M8 6L13 4L18 10L14 14L10 10L8 12L12 16L8 20L4 15V11ZM22 12L28 18V22L22 28H18L13 23L17 19L20 23L23 20L19 16Z",
        "M9 20L20 9L23 12L12 23Z")
    val materials = GameGlyph("M5 10L17 5L27 10L15 16ZM5 13L15 18L27 13V17L15 23L5 18ZM5 21L15 26L27 20V24L15 29L5 24Z",
        "M10 10L17 7L21 9L14 12Z")
    val food = GameGlyph("M4 16H28Q27 23 21 25V27H11V25Q5 23 4 16ZM8 14Q8 10 12 10Q14 4 19 7Q23 6 25 14Z",
        "M7 18H25L23 20H9ZM14 10L17 8L19 10L16 12Z")
    val artifact = GameGlyph("M16 3L25 11L22 24L16 29L9 24L6 11Z",
        "M16 6L20 11L16 24L12 11ZM8 12L11 13L13 22Z")
    val ticket = GameGlyph("M4 9H28V14Q23 16 28 19V24H4V19Q9 16 4 14Z",
        "M14 12L15 14L18 15L16 17V20L14 19L11 20L12 17L10 15L13 14ZM21 11H23V14H21ZM21 18H23V21H21Z")
    val pouch = GameGlyph("M10 5L15 7L18 4L22 6L19 12C20 15 27 18 26 24Q25 29 16 28Q7 29 6 24C5 19 11 15 13 12Z",
        "M11 11H21L20 14H12ZM9 23Q10 18 14 17L12 22L12 25Z")
}

private fun itemGlyph(name: String, category: String) = when {
    name.endsWith(" Draught") -> ItemGlyphs.draught
    name.endsWith(" Potion") -> ItemGlyphs.potion
    name.endsWith(" Elixir") -> ItemGlyphs.elixir
    name.startsWith("Infusion of ") || category == "Infusions" -> ItemGlyphs.infusion
    name.startsWith("Scroll of ") || category == "Scrolls" -> ItemGlyphs.scroll
    name == "Energy Drink" -> ItemGlyphs.drink
    name == "Flower Vase" -> ItemGlyphs.vase
    name == "Bubble-Gum" || name.endsWith(" Candy") || name == "Happy Pills" -> ItemGlyphs.candy
    name.startsWith("Crystal of ") || name.endsWith(" Shard") || name.endsWith(" Fragment") -> ItemGlyphs.crystal
    name.startsWith("Token of ") || name.endsWith(" Token") -> ItemGlyphs.token
    name.startsWith("Binding of ") -> ItemGlyphs.binding
    name.startsWith("Scrap ") || "-Grade " in name -> ItemGlyphs.materials
    name.startsWith("Monster ") -> ItemGlyphs.food
    name.endsWith(" Artifact") || name.endsWith(" Core") || name == "Soul Stone" -> ItemGlyphs.artifact
    name.endsWith(" Ticket") -> ItemGlyphs.ticket
    else -> ItemGlyphs.pouch
}

private fun itemPalette(name: String, glyph: GameGlyph) = when {
    name.startsWith("Health ") || name.endsWith(" Blood") || name.endsWith(" Life") -> ArtPalette.Life
    name.startsWith("Mana ") || name.endsWith(" Frost") -> ArtPalette.Frost
    name.startsWith("Spirit ") || name.endsWith(" Gaia") -> ArtPalette.Spirit
    name.endsWith(" Flames") -> ArtPalette.Ember
    name.endsWith(" Lightning") -> ArtPalette.Storm
    name.endsWith(" Storms") || name.endsWith(" Tempest") || name.endsWith(" Swiftness") -> ArtPalette.Gale
    name.endsWith(" Darkness") || name.endsWith(" Shadows") || name.endsWith(" Corruption") -> ArtPalette.Shadow
    name.endsWith(" Divinity") || name == "Last Elixir" -> ArtPalette.Light
    glyph == ItemGlyphs.binding || glyph == ItemGlyphs.materials && "Metal" in name -> ArtPalette.Steel
    glyph == ItemGlyphs.artifact || glyph == ItemGlyphs.crystal -> ArtPalette.Arcane
    glyph == ItemGlyphs.candy -> ArtPalette.Life
    glyph == ItemGlyphs.vase -> ArtPalette.Spirit
    glyph == ItemGlyphs.drink || glyph == ItemGlyphs.ticket || glyph == ItemGlyphs.scroll -> ArtPalette.Light
    else -> ArtPalette.Earth
}

@Composable
internal fun ItemArtwork(name: String, category: String, modifier: Modifier = Modifier) {
    val glyph = remember(name, category) { itemGlyph(name, category) }
    val palette = remember(name, glyph) { itemPalette(name, glyph) }
    GameArt(glyph, palette, modifier)
}
