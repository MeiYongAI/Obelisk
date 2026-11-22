package com.mitahv.mobile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import java.util.Locale

internal object EquipmentIcons {
    fun forSlot(slot: String): ImageVector = when (slot.trim().lowercase(Locale.ROOT)) {
        "main hand" -> sword
        "off hand" -> shield
        "helmet" -> helmet
        "body" -> breastplate
        "hands" -> gauntlet
        "legs" -> greaves
        "feet" -> boot
        else -> Icons.Filled.Inventory2
    }

    private val sword by lazy {
        outline("Equipment.Sword") {
            moveTo(20f, 3f)
            lineTo(20f, 8f)
            lineTo(11f, 17f)
            lineTo(7f, 13f)
            lineTo(16f, 4f)
            close()
            moveTo(5f, 12f)
            lineTo(12f, 19f)
            moveTo(7.5f, 16.5f)
            lineTo(3.5f, 20.5f)
            moveTo(2.5f, 19.5f)
            lineTo(4.5f, 21.5f)
            moveTo(11f, 13f)
            lineTo(17f, 7f)
        }
    }

    private val shield by lazy {
        outline("Equipment.Shield") {
            moveTo(12f, 2.5f)
            lineTo(20f, 6f)
            lineTo(20f, 11.5f)
            curveTo(20f, 16f, 16.5f, 19.5f, 12f, 21.5f)
            curveTo(7.5f, 19.5f, 4f, 16f, 4f, 11.5f)
            lineTo(4f, 6f)
            close()
            moveTo(12f, 6.5f)
            lineTo(12f, 17.5f)
        }
    }

    private val helmet by lazy {
        outline("Equipment.Helmet") {
            moveTo(4f, 20f)
            lineTo(4f, 11f)
            curveTo(4f, 6.5f, 7.5f, 3f, 12f, 3f)
            curveTo(16.5f, 3f, 20f, 6.5f, 20f, 11f)
            lineTo(20f, 20f)
            lineTo(15f, 21f)
            lineTo(15f, 15f)
            lineTo(9f, 15f)
            lineTo(9f, 21f)
            close()
            moveTo(4f, 11f)
            lineTo(20f, 11f)
            moveTo(12f, 3f)
            lineTo(12f, 7f)
        }
    }

    private val breastplate by lazy {
        outline("Equipment.Breastplate") {
            moveTo(8f, 3f)
            curveTo(8f, 7f, 16f, 7f, 16f, 3f)
            lineTo(21f, 6f)
            lineTo(18f, 12f)
            lineTo(19f, 20f)
            curveTo(15f, 22f, 9f, 22f, 5f, 20f)
            lineTo(6f, 12f)
            lineTo(3f, 6f)
            close()
            moveTo(8f, 11f)
            lineTo(12f, 13f)
            lineTo(16f, 11f)
            moveTo(12f, 13f)
            lineTo(12f, 18f)
        }
    }

    private val gauntlet by lazy {
        outline("Equipment.Gauntlet") {
            moveTo(7f, 17f)
            lineTo(3.5f, 11.5f)
            curveTo(2.5f, 9.5f, 4.5f, 8.5f, 5.5f, 10f)
            lineTo(7f, 12f)
            lineTo(7f, 5f)
            curveTo(7f, 3f, 10f, 3f, 10f, 5f)
            lineTo(10f, 10f)
            lineTo(10f, 3.5f)
            curveTo(10f, 1.5f, 13f, 1.5f, 13f, 3.5f)
            lineTo(13f, 10f)
            lineTo(13f, 4.5f)
            curveTo(13f, 2.5f, 16f, 2.5f, 16f, 4.5f)
            lineTo(16f, 11f)
            lineTo(16f, 7f)
            curveTo(16f, 5f, 19f, 5f, 19f, 7f)
            lineTo(19f, 13f)
            curveTo(19f, 15f, 18f, 16f, 17f, 17f)
            lineTo(17f, 21f)
            lineTo(7f, 21f)
            close()
            moveTo(7f, 17f)
            lineTo(17f, 17f)
        }
    }

    private val greaves by lazy {
        outline("Equipment.Greaves") {
            moveTo(6f, 3f)
            lineTo(18f, 3f)
            lineTo(19f, 21f)
            lineTo(14f, 21f)
            lineTo(12f, 11f)
            lineTo(10f, 21f)
            lineTo(5f, 21f)
            close()
            moveTo(6f, 7f)
            lineTo(18f, 7f)
            moveTo(6f, 15f)
            lineTo(10.5f, 15f)
            moveTo(13.5f, 15f)
            lineTo(18f, 15f)
        }
    }

    private val boot by lazy {
        outline("Equipment.Boot") {
            moveTo(6f, 3f)
            lineTo(15f, 3f)
            lineTo(15f, 12f)
            lineTo(20f, 15f)
            curveTo(21f, 15.5f, 21f, 16.5f, 21f, 18f)
            lineTo(21f, 21f)
            lineTo(4f, 21f)
            lineTo(4f, 17f)
            lineTo(6f, 12f)
            close()
            moveTo(6f, 7f)
            lineTo(15f, 7f)
            moveTo(4f, 18f)
            lineTo(21f, 18f)
        }
    }

    private fun outline(name: String, draw: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = draw,
        )
    }.build()
}
