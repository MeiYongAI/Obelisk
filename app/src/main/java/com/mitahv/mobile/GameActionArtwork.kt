package com.mitahv.mobile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mitahv.mobile.model.BattleActionOption

@Composable
internal fun BattleActionArtwork(option: BattleActionOption, modifier: Modifier = Modifier) {
    if (option.mode == "items" || option.group in itemGroups) {
        ItemArtwork(option.name, option.group, modifier)
        return
    }
    val (glyph, palette) = battleGlyphFor(option.iconKey, option.name) ?: defaultBattleGlyph(option.group)
    GameArt(glyph, palette, modifier)
}

private val itemGroups = setOf("Items", "Scrolls", "Infusions")
