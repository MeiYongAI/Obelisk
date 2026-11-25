package com.mitahv.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser

/** Two original, filled shapes in a 32-unit square. A glyph is shared by all instances of an item or spell. */
internal class GameGlyph(shape: String, detail: String = "") {
    val silhouette = PathParser().parsePathString(shape).toPath()
    val highlight = detail.takeIf { it.isNotEmpty() }?.let { PathParser().parsePathString(it).toPath() }
}

internal enum class ArtPalette(val ground: Color, val ink: Color, val glint: Color) {
    Ember(Color(0xFF522A27), Color(0xFFFF975E), Color(0xFFFFE5AB)),
    Frost(Color(0xFF173E58), Color(0xFF70CCF3), Color(0xFFDCF8FF)),
    Storm(Color(0xFF3C315B), Color(0xFFBD9DF9), Color(0xFFF8E9FF)),
    Gale(Color(0xFF19474A), Color(0xFF79D9D2), Color(0xFFE0FFF2)),
    Light(Color(0xFF4D3B22), Color(0xFFF2BC62), Color(0xFFFFF0C0)),
    Shadow(Color(0xFF372747), Color(0xFFC398D7), Color(0xFFF8DFF9)),
    Arcane(Color(0xFF303356), Color(0xFFA7AFEF), Color(0xFFE7ECFF)),
    Life(Color(0xFF542E3C), Color(0xFFF7959C), Color(0xFFFFE3D8)),
    Spirit(Color(0xFF1D453A), Color(0xFF7DD6A5), Color(0xFFDFFFF0)),
    Steel(Color(0xFF2E3C4A), Color(0xFFADCAD8), Color(0xFFEEF8FF)),
    Earth(Color(0xFF493629), Color(0xFFDAAA78), Color(0xFFFFE8C0)),
}

@Composable
internal fun GameArt(glyph: GameGlyph, palette: ArtPalette, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 32f
        withTransform({
            translate((size.width - 32 * unit) / 2, (size.height - 32 * unit) / 2)
            scale(unit, unit, pivot = Offset.Zero)
        }) {
            drawRoundRect(palette.ground, size = Size(32f, 32f), cornerRadius = CornerRadius(6f))
            drawRoundRect(palette.ink.copy(alpha = .34f), Offset(.5f, .5f), Size(31f, 31f),
                CornerRadius(5.5f), style = Stroke(1f))
            drawPath(glyph.silhouette, palette.ink)
            glyph.highlight?.let { drawPath(it, palette.glint) }
        }
    }
}
