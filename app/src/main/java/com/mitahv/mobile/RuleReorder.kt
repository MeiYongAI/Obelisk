package com.mitahv.mobile

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.delay

/** Track a drop slot, not a mutable copy of the rules. Cancelling never changes the draft. */
internal class RuleReorder(val list: LazyListState, val addresses: Map<String, RuleAddress>) {
    var from by mutableStateOf<RuleAddress?>(null)
    var to by mutableStateOf<RuleAddress?>(null)
    var y = 0f
    fun hit(y: Float) = list.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size }
        ?.let { addresses[it.key] }
    fun update() {
        val group = from?.group ?: return
        list.layoutInfo.visibleItemsInfo.filter { addresses[it.key]?.group == group }
            .minByOrNull { abs(it.offset + it.size / 2f - y) }?.let { to = addresses[it.key] }
    }
    fun clear() { from = null; to = null }
}

internal fun Modifier.ruleDrag(drag: RuleReorder, enabled: Boolean, move: (RuleAddress, Int) -> Unit) = composed {
    val drop by rememberUpdatedState(move)
    val edge = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
    LaunchedEffect(drag, drag.from) {
        while (drag.from != null) {
            val height = drag.list.layoutInfo.viewportEndOffset
            val speed = when {
                drag.y < edge -> (drag.y - edge).coerceAtLeast(-edge)
                drag.y > height - edge -> (drag.y - height + edge).coerceAtMost(edge)
                else -> 0f
            }
            // Do not scroll the origin group completely out of reach.
            val boundary = if (speed > 0) drag.list.layoutInfo.visibleItemsInfo.lastOrNull() else drag.list.layoutInfo.visibleItemsInfo.firstOrNull()
            if (speed != 0f && boundary?.let { drag.addresses[it.key]?.group } == drag.from?.group) {
                drag.list.scrollBy(speed / 4); drag.update()
            }
            delay(16)
        }
    }
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    pointerInput(drag, enabled, direction) {
        if (!enabled) return@pointerInput
        try { awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val x = if (direction == LayoutDirection.Ltr) down.position.x else size.width - down.position.x
            if (x > 48.dp.toPx()) return@awaitEachGesture
            val origin = drag.hit(down.position.y) ?: return@awaitEachGesture
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val point = event.changes.singleOrNull()?.takeIf { it.id == down.id && !it.isConsumed } ?: break
                if (!point.pressed) {
                    if (drag.from != null) { point.consume(); drag.to?.let { drop(origin, it.index) } }
                    break
                }
                if (drag.from == null && abs(point.position.y - down.position.y) < viewConfiguration.touchSlop) continue
                point.consume(); drag.from = origin; drag.y = point.position.y; drag.update()
            } while (true)
            drag.clear()
        } } finally { drag.clear() }
    }
}
