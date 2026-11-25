package com.mitahv.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

private fun <T> JSONArray.objects(read: (JSONObject) -> T) = List(length()) { read(getJSONObject(it)) }

internal class StoreCollection(data: JSONObject) {
    val page = data.getString("page")
    val request = data.getInt("request")
    val query = data.getString("query")
    val search = data.getString("search")
    val loading = data.optBoolean("loading")
    val count = data.optString("count")
    val empty = data.optString("empty")
    val emptyAction = data.optString("emptyAction")
    val more = data.optString("more")
    val resetLabel = data.optString("resetLabel")
    val rows = data.getJSONArray("rows").objects(::StoreEntry)
    val groups = data.optJSONArray("groups")?.objects(::StoreFilter).orEmpty()
}

internal class StoreEntry(data: JSONObject) {
    val id = data.getString("id")
    val title = data.getString("title")
    val mark = data.getString("mark")
    val subtitle = data.getString("subtitle")
    val author = data.optString("author")
    val save = data.optString("save")
    val saveLabel = data.optString("saveLabel")
    val saving = data.optBoolean("saving")
}

internal class StoreFilter(data: JSONObject) {
    val key = data.getString("key")
    val label = data.getString("label")
    val selected = data.getString("selected")
    val enabled = data.getBoolean("enabled")
    val options = data.getJSONArray("choices").let { options ->
        List(options.length()) { i -> options.getJSONArray(i).let { it.getString(0) to it.getString(1) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun StoreCollectionContent(model: StoreCollection, notice: Pair<String, Boolean>, state: LazyListState,
    onAction: (String, Any) -> Unit) {
    var query by remember { mutableStateOf(model.query) }
    var filters by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    fun search(text: String) {
        query = text
        state.requestScrollToItem(0)
        onAction("query", text)
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        TextField(query, ::search, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            singleLine = true, placeholder = { Text(model.search, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) IconButton(onClick = { search("") }) {
                    Icon(Icons.Default.Close, stringResource(R.string.game_ui_clear_search))
                }
                if (model.groups.isNotEmpty()) {
                    val active = model.groups.any { it.key != "sort" && it.selected.isNotEmpty() }
                    IconButton(onClick = { focus.clearFocus(); filters = true }) {
                        BadgedBox(badge = { if (active) Badge() }) {
                            Icon(Icons.Default.FilterList, stringResource(R.string.game_ui_filter))
                        }
                    }
                }
            } },
            shape = MaterialTheme.shapes.extraLarge,
            colors = TextFieldDefaults.colors(focusedContainerColor = colors.surfaceContainerHigh,
                unfocusedContainerColor = colors.surfaceContainerHigh, focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }))
        if (model.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
        if (notice.first.isNotBlank()) Text(notice.first, Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }, color = if (notice.second) colors.error else colors.onSurfaceVariant)
        if (model.rows.isEmpty()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (model.empty.isNotEmpty()) Text(model.empty, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                if (model.emptyAction.isNotEmpty()) TextButton(onClick = {
                    if (model.empty.isNotEmpty()) query = ""
                    state.requestScrollToItem(0)
                    onAction("empty", "")
                }) { Text(model.emptyAction) }
            }
        } else LazyColumn(state = state, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
            if (model.count.isNotEmpty()) item(key = "count") {
                Text(model.count, Modifier.padding(start = 8.dp, top = 4.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
            items(model.rows, key = { "entry:" + it.id }) { entry ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clickable { focus.clearFocus(); onAction("open", entry.id) }
                        .heightIn(min = 76.dp).padding(start = 8.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        StoreArtwork(entry.mark)
                        Column {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (entry.author.isNotEmpty()) Text(entry.author, style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (entry.save.isNotEmpty()) IconButton(enabled = !entry.saving, onClick = { onAction("save", entry.id) }) {
                        if (entry.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(when (entry.save) {
                            "saved" -> Icons.Default.Bookmark
                            "updateAvailable" -> Icons.Default.Update
                            else -> Icons.Default.BookmarkBorder
                        }, entry.saveLabel + " · " + entry.title, tint = if (entry.save == "save") colors.onSurfaceVariant else colors.primary)
                    }
                }
                HorizontalDivider(Modifier.padding(start = 60.dp), color = colors.outlineVariant)
            }
            if (model.more.isNotEmpty()) item { TextButton(onClick = { onAction("more", "") },
                modifier = Modifier.fillMaxWidth()) { Text(model.more) } }
        }
    }
    if (filters) ModalBottomSheet(onDismissRequest = { filters = false },
        containerColor = colors.surfaceContainerLow,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.game_ui_filter), Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge)
                if (model.groups.any { it.selected != it.options.first().first }) {
                    TextButton(onClick = { state.requestScrollToItem(0); onAction("resetFilters", "") }) { Text(model.resetLabel) }
                }
                IconButton(onClick = { filters = false }) { Icon(Icons.Default.Close, stringResource(R.string.app_close)) }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
                model.groups.filter { it.enabled }.forEach { group ->
                    Text(group.label, Modifier.padding(top = 12.dp).semantics { heading() }, style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.options.forEach { (value, label) ->
                            FilterChip(selected = group.selected == value, onClick = {
                                state.requestScrollToItem(0)
                                onAction("filter", JSONObject().put("key", group.key).put("value", value))
                            }, label = { Text(label) })
                        }
                    }
                }
            }
        }
    }
}

private val storeStatisticsGlyph = GameGlyph("M5 17H11V27H5ZM13 10H19V27H13ZM21 5H27V27H21Z",
    "M5 17H11V20H5ZM13 10H19V13H13ZM21 5H27V8H21Z")

@Composable
private fun StoreArtwork(mark: String) {
    val (glyph, palette) = when (mark) {
        "sword" -> AbilityGlyphs.sword to ArtPalette.Steel
        "greatsword" -> AbilityGlyphs.greatsword to ArtPalette.Earth
        "blades" -> AbilityGlyphs.blades to ArtPalette.Gale
        "staff" -> AbilityGlyphs.staff to ArtPalette.Arcane
        "impact" -> AbilityGlyphs.impact to ArtPalette.Light
        "stats" -> storeStatisticsGlyph to ArtPalette.Spirit
        else -> AbilityGlyphs.spellbook to ArtPalette.Arcane
    }
    GameArt(glyph, palette, Modifier.size(40.dp))
}
