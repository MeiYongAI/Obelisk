package com.mitahv.mobile

import androidx.compose.ui.res.stringResource
import com.mitahv.mobile.i18n.gameText
import com.mitahv.mobile.i18n.messageText

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mitahv.mobile.model.NativeAction
import com.mitahv.mobile.model.RiddlePage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl

private val RiddleSelectionSaver = listSaver<List<String>, String>(
    save = { it },
    restore = { it.toList() },
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RiddleScreen(
    page: RiddlePage,
    busy: Boolean,
    onAction: (NativeAction) -> Unit,
    loadImage: suspend (HttpUrl) -> ByteArray,
    responseRevision: Int = 0,
    imageBytes: ByteArray? = null,
    needsRefresh: Boolean = false,
) {
    var selected by rememberSaveable(page.image.source, responseRevision, stateSaver = RiddleSelectionSaver) {
        mutableStateOf<List<String>>(page.choices.filter { it.checked && it.enabled }.map { it.value })
    }
    var imageRetry by remember(page.image.source, responseRevision) { mutableIntStateOf(0) }
    var referenceRetry by remember(page.referenceImageUrl, responseRevision) { mutableIntStateOf(0) }
    var expandedImage by rememberSaveable(page.image.source, responseRevision) { mutableStateOf<String?>(null) }
    val picture = rememberRiddleImage(page.image.url, imageBytes, responseRevision, imageRetry, loadImage)
    val remainingSeconds = riddleRemainingSeconds(page, responseRevision)
    val selection = selected.toSet()
    val scroll = rememberScrollState()
    var contentWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val minimumColumnWidthPx = with(density) { (180.dp * fontScale.coerceAtLeast(1f)).roundToPx() }
    val columnGapPx = with(density) { 8.dp.roundToPx() }
    val choiceColumns = if (contentWidthPx >= minimumColumnWidthPx * 2 + columnGapPx) 2 else 1
    LaunchedEffect(page.image.source, responseRevision) { scroll.scrollTo(0) }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(
            Modifier.weight(1f).widthIn(max = 640.dp).fillMaxWidth().verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 12.dp).onSizeChanged { contentWidthPx = it.width },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.game_ui_riddle_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                remainingSeconds?.let {
                    Text(stringResource(R.string.game_ui_seconds, it), style = MaterialTheme.typography.labelLarge,
                        color = if (it in 1..5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (page.instruction.isNotBlank()) Text(gameText(page.instruction), style = MaterialTheme.typography.bodyMedium)
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                RiddleImageContent(
                    state = picture,
                    description = stringResource(R.string.game_ui_riddle_image),
                    modifier = Modifier.fillMaxWidth(),
                    onReload = { imageRetry++ },
                    canReload = page.image.url != null || imageBytes != null,
                    onExpand = { expandedImage = "challenge" },
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.game_ui_riddle_selection_count, selection.size, page.maximumSelections), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (page.referenceImageUrl != null) TextButton(onClick = { expandedImage = "reference" }) {
                    Text(stringResource(R.string.game_ui_pony_reference))
                }
            }
            page.choices.chunked(choiceColumns).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { choice ->
                        val checked = choice.value in selection
                        val enabled = !busy && !needsRefresh && choice.enabled
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.large,
                            color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = if (checked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                    .toggleable(checked, enabled = enabled, role = Role.Checkbox) { value ->
                                        selected = if (value) selected + choice.value else selected - choice.value
                                    }.padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                                Text(gameText(choice.label), Modifier.weight(1f).padding(start = 6.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (enabled || checked) LocalContentColor.current else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                            }
                        }
                    }
                    if (choiceColumns == 2 && pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Surface(tonalElevation = 2.dp) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(
                    onClick = { onAction(page.submit(selection)) },
                    enabled = !busy && !needsRefresh && page.validationError(selection) == null,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(16.dp).heightIn(min = 56.dp),
                ) { Text(when { busy -> stringResource(R.string.game_ui_submitting); needsRefresh -> stringResource(R.string.game_ui_refresh_first); else -> page.submitAction?.label?.let { messageText(it) } ?: stringResource(R.string.game_ui_submit_answer) }) }
            }
        }
    }

    when (expandedImage) {
        "challenge" -> RiddleImageDialog(stringResource(R.string.game_ui_verification_image), picture, { expandedImage = null },
            onReload = { imageRetry++ }, canReload = page.image.url != null || imageBytes != null)
        "reference" -> page.referenceImageUrl?.let { url ->
            val reference = rememberRiddleImage(url, null, responseRevision, referenceRetry, loadImage)
            RiddleImageDialog(stringResource(R.string.game_ui_pony_reference), reference, { expandedImage = null },
                onReload = { referenceRetry++ }, canReload = true)
        }
    }
}

@Composable
private fun riddleRemainingSeconds(page: RiddlePage, revision: Int): Int? {
    val initial = page.displayedSecondsRemaining ?: page.timeLimitSeconds ?: return null
    val startedAt = rememberSaveable(page.image.source, revision) { SystemClock.elapsedRealtime() }
    var elapsedSeconds by remember(page.image.source, revision) { mutableIntStateOf(0) }
    LaunchedEffect(page.image.source, revision, initial, startedAt) {
        do {
            elapsedSeconds = ((SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L) / 1000L)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (elapsedSeconds < initial) delay(250)
        } while (elapsedSeconds < initial)
    }
    return (initial - elapsedSeconds).coerceAtLeast(0)
}

private sealed interface RiddleImageState {
    data object Loading : RiddleImageState
    data class Ready(val bitmap: ImageBitmap) : RiddleImageState
    data class Failed(val messageRes: Int) : RiddleImageState
}

@Composable
private fun rememberRiddleImage(
    url: HttpUrl?, bytes: ByteArray?, revision: Int, retry: Int, loader: suspend (HttpUrl) -> ByteArray,
): RiddleImageState {
    var state by remember(url, bytes, revision, retry) { mutableStateOf<RiddleImageState>(RiddleImageState.Loading) }
    val currentLoader by rememberUpdatedState(loader)
    LaunchedEffect(url, bytes, revision, retry) {
        if (url == null && bytes == null) {
            state = RiddleImageState.Failed(R.string.game_ui_image_unavailable)
            return@LaunchedEffect
        }
        state = try {
            val source = bytes ?: currentLoader(requireNotNull(url))
            val bitmap = withContext(Dispatchers.Default) {
                requireNotNull(BitmapFactory.decodeByteArray(source, 0, source.size)) { "Invalid image" }.asImageBitmap()
            }
            RiddleImageState.Ready(bitmap)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            RiddleImageState.Failed(R.string.game_ui_image_failed)
        }
    }
    return state
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RiddleImageContent(
    state: RiddleImageState,
    description: String,
    modifier: Modifier = Modifier,
    onReload: () -> Unit,
    canReload: Boolean,
    onExpand: (() -> Unit)? = null,
) {
    when (state) {
        is RiddleImageState.Ready -> Image(
            bitmap = state.bitmap, contentDescription = description, contentScale = ContentScale.Fit,
            modifier = modifier.then(if (onExpand != null) Modifier.clickable(onClickLabel = stringResource(R.string.game_ui_enlarge_image), onClick = onExpand) else Modifier)
                .aspectRatio(state.bitmap.width.toFloat() / state.bitmap.height),
        )
        RiddleImageState.Loading -> Box(modifier.heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
            LoadingIndicator(Modifier.size(44.dp))
        }
        is RiddleImageState.Failed -> Column(modifier.heightIn(min = 160.dp).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(stringResource(state.messageRes), color = MaterialTheme.colorScheme.error)
            if (canReload) TextButton(onClick = onReload) { Text(stringResource(R.string.game_ui_reload)) }
        }
    }
}

@Composable
private fun RiddleImageDialog(
    title: String, state: RiddleImageState, onDismiss: () -> Unit, onReload: () -> Unit, canReload: Boolean,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f), shape = MaterialTheme.shapes.extraLarge) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.game_ui_close)) }
                }
                if (state is RiddleImageState.Ready) {
                    ZoomableRiddleImage(state.bitmap, title, Modifier.weight(1f).fillMaxWidth())
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        RiddleImageContent(state, title, Modifier.fillMaxWidth(), onReload, canReload)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableRiddleImage(bitmap: ImageBitmap, description: String, modifier: Modifier) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    fun boundedOffset(value: Offset, zoom: Float): Offset = Offset(
        value.x.coerceIn(-viewport.width * (zoom - 1f) / 2f, viewport.width * (zoom - 1f) / 2f),
        value.y.coerceIn(-viewport.height * (zoom - 1f) / 2f, viewport.height * (zoom - 1f) / 2f),
    )
    fun zoomTo(value: Float) {
        scale = value.coerceIn(1f, 5f)
        offset = boundedOffset(offset, scale)
    }
    val gesture = rememberTransformableState { _, zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = boundedOffset(offset + pan, scale)
    }
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { viewport = it }.transformable(gesture)) {
            Image(bitmap, description, Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }, contentScale = ContentScale.Fit)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = { zoomTo(scale / 1.5f) }, enabled = scale > 1f) {
                Icon(Icons.Default.ZoomOut, contentDescription = stringResource(R.string.game_ui_zoom_out))
            }
            IconButton(onClick = { scale = 1f; offset = Offset.Zero }) {
                Icon(Icons.Default.RestartAlt, contentDescription = stringResource(R.string.game_ui_reset_zoom))
            }
            IconButton(onClick = { zoomTo(scale * 1.5f) }, enabled = scale < 5f) {
                Icon(Icons.Default.ZoomIn, contentDescription = stringResource(R.string.game_ui_zoom_in))
            }
        }
    }
}
