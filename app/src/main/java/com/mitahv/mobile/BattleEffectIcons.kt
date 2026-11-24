package com.mitahv.mobile

import android.graphics.BitmapFactory
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.mitahv.mobile.i18n.gameText
import com.mitahv.mobile.model.BattleEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.io.IOException

internal val LocalBattleIconLoader = staticCompositionLocalOf<(suspend (HttpUrl) -> ByteArray)?> { null }
private val downloadedIcons = LruCache<HttpUrl, ImageBitmap>(96)

private fun effectIconResource(key: String?): Int? = when (key) {
    "arcanemeditation" -> R.drawable.hv_effect_arcanemeditation
    "coalescemana" -> R.drawable.hv_effect_coalescemana
    "coldslow" -> R.drawable.hv_effect_coldslow
    "firedot" -> R.drawable.hv_effect_firedot
    "flee" -> R.drawable.hv_effect_flee
    "haste" -> R.drawable.hv_effect_haste
    "healthpot" -> R.drawable.hv_effect_healthpot
    "heartseeker" -> R.drawable.hv_effect_heartseeker
    "imperil" -> R.drawable.hv_effect_imperil
    "manapot" -> R.drawable.hv_effect_manapot
    "protection" -> R.drawable.hv_effect_protection
    "regen" -> R.drawable.hv_effect_regen
    "riddlemaster" -> R.drawable.hv_effect_riddlemaster
    "shadowveil" -> R.drawable.hv_effect_shadowveil
    "sparklife" -> R.drawable.hv_effect_sparklife
    "spiritpot" -> R.drawable.hv_effect_spiritpot
    "spiritshield" -> R.drawable.hv_effect_spiritshield
    "wpn_ap" -> R.drawable.hv_effect_wpn_ap
    else -> null
}

@Composable
private fun BattleEffectImage(effect: BattleEffect, modifier: Modifier = Modifier) {
    effectIconResource(effect.iconKey)?.let {
        Image(painterResource(it), null, modifier)
        return
    }
    val url = effect.iconUrl
    val load = LocalBattleIconLoader.current
    val icon by produceState(url?.let(downloadedIcons::get), url, load) {
        value = url?.let(downloadedIcons::get)
        if (value == null && url != null && load != null) {
            try {
                val bytes = load(url)
                val bitmap = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
                if (bitmap != null) downloadedIcons.put(url, bitmap)
                value = bitmap
            } catch (_: IOException) {
                // The effect and its duration remain visible while its artwork is unavailable.
            }
        }
    }
    val image = icon
    if (image != null) {
        Image(image, null, modifier)
    } else {
        Text(gameText(effect.name).take(2), modifier, style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
        for (effect in preview) key(effect.name, effect.iconUrl) { BattleEffectBadge(effect, compact) }
        if (effects.size > preview.size) Text("+${effects.size - preview.size}", style = MaterialTheme.typography.labelSmall,
            maxLines = 1, softWrap = false)
    }
}
