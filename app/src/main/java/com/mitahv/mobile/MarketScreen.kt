package com.mitahv.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.i18n.LocalMessageTranslator
import com.mitahv.mobile.i18n.messageText
import com.mitahv.mobile.model.*
import kotlinx.coroutines.launch

/** Market navigation and trade composition have separate surfaces on a phone. */
@Composable
internal fun MarketScreen(
    page: BazaarSectionsPage,
    busy: Boolean,
    onAction: (NativeAction) -> Unit,
    onDraftChanged: (Boolean) -> Unit = {},
    responseRevision: Int = 0,
) {
    val savedPages = rememberSaveableStateHolder()
    val pageKey = page.forms.firstOrNull()?.actions?.firstOrNull()?.url?.toString()
        ?: page.marketLinks?.views?.firstOrNull { it.selected }?.action?.url?.toString().orEmpty()
    savedPages.SaveableStateProvider(pageKey) {
        MarketPage(page, busy, onAction, onDraftChanged, responseRevision)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarketPage(
    page: BazaarSectionsPage,
    busy: Boolean,
    onAction: (NativeAction) -> Unit,
    onDraftChanged: (Boolean) -> Unit,
    responseRevision: Int,
) {
    val translator = LocalMessageTranslator.current
    val links = page.marketLinks
    val itemName = links?.currentItem.orEmpty()
    val detail = itemName.isNotBlank()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val initialValues = remember(page) {
        page.forms.flatMap { form -> form.fields.map { field ->
            "${form.key}/${field.key}" to if (field.kind == "checkbox" && !field.checked) "" else field.value
        } }.toMap()
    }
    val snapshot = remember(page, initialValues) { bazaarDraftSnapshot(page, initialValues) }
    var savedDraft by rememberSaveable(snapshot, stateSaver = BazaarDraftSaver) {
        mutableStateOf(BazaarDraft(snapshot, "", initialValues))
    }
    val draft = if (savedDraft.snapshot == snapshot) savedDraft else remember(snapshot) {
        BazaarDraft(snapshot, "", initialValues)
    }
    SideEffect { if (savedDraft !== draft) savedDraft = draft }
    val values = draft.values
    var query by draft.query
    var dialog by rememberSaveable(snapshot, responseRevision) { mutableStateOf<String?>(null) }
    var recordKey by remember(snapshot, responseRevision) { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val changed = values.toMap() != initialValues
    LaunchedEffect(snapshot, changed, responseRevision) { onDraftChanged(changed) }
    val orderForms = page.forms.filter { it.validation in setOf("market_buy", "market_sell") }
    val wallet = page.forms.firstOrNull { it.key == "transfer" }
    val stockFilter = page.forms.firstOrNull { it.key == "stock-filter" }
    val rules = orderForms.flatMap { form -> form.facts.filter { it.first == "交易" }.map { it.second } }.distinct()
    val currentView = links?.views?.firstOrNull { it.selected } ?: links?.views?.firstOrNull()
    val currentCategory = links?.filters?.firstOrNull { it.selected }
    val stockFiltered = stockFilter?.fields?.any { it.checked } == true
    fun entered(form: BazaarSectionsForm) = form.fields.associate { it.key to values["${form.key}/${it.key}"].orEmpty() }
    fun submit(form: BazaarSectionsForm, action: NativeAction) {
        if (busy) return
        dialog = null
        onAction(form.submit(action, entered(form)))
    }
    fun useOffer(entry: BazaarSectionsEntry) {
        if (busy || !entry.enabled) return
        val preset = entry.prefill ?: return
        values.putAll(preset.applyTo(values))
        // The page may only permit matching our own price when the opposing trade is unavailable.
        val targetKey = (preset.minimumValues.keys.firstOrNull() ?: preset.values.keys.firstOrNull())?.substringBefore('/')
        orderForms.firstOrNull { it.key == targetKey }?.let { dialog = it.key }
    }
    val detailTabs = remember(page.sections) { buildList {
        if (page.sections.any { it.isOrderBook() }) add("Orders")
        if (page.sections.any { it.title == "Price History" }) add("Price History")
        if (page.sections.any { it.title == "Recent Trades" }) add("Recent Trades")
    } }
    var detailTab by rememberSaveable { mutableStateOf("Orders") }
    val activeTab = detailTab.takeIf { it in detailTabs } ?: detailTabs.firstOrNull()
    val visibleGroups = page.sections.filter { group -> !detail || when {
        group.isOrderBook() -> activeTab == "Orders"
        group.title in setOf("Price History", "Recent Trades") -> activeTab == group.title
        else -> true
    } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (detail) {
                links?.backToList?.let { back ->
                    IconButton(onClick = { onAction(back.action) }, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.game_ui_market_return_to_list))
                    }
                }
                Text(messageText(itemName), Modifier.weight(1f).padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                orderForms.firstOrNull()?.batchSize?.takeIf { it > 1 }?.let { batch ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
                        Text(stringResource(R.string.game_ui_market_batch_quantity, batch),
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (wallet != null) IconButton(onClick = { dialog = "transfer" }, enabled = !busy) {
                    Icon(Icons.Default.AccountBalanceWallet, stringResource(R.string.game_ui_market_wallet))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.game_ui_more_actions))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        links?.items?.forEach { link ->
                            DropdownMenuItem(
                                text = { Text(stringResource(if (link.label == "Previous item") R.string.game_ui_market_previous_item else R.string.game_ui_market_next_item)) },
                                onClick = { menuOpen = false; onAction(link.action) }, enabled = !busy,
                            )
                        }
                        if (rules.isNotEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.game_ui_market_rules)) },
                            leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = { menuOpen = false; dialog = "rules" })
                    }
                }
            } else {
                Box(Modifier.weight(1f)) {
                    TextButton(onClick = { menuOpen = true }, enabled = !busy && !links?.views.isNullOrEmpty(),
                        contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(marketViewLabel(currentView?.label.orEmpty()), Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.ExpandMore, null, Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        links?.views?.forEach { link ->
                            DropdownMenuItem(text = { Text(marketViewLabel(link.label)) },
                                trailingIcon = { if (link.selected) Icon(Icons.Default.Check, null) },
                                onClick = { menuOpen = false; if (!link.selected) onAction(link.action) }, enabled = !busy)
                        }
                    }
                }
                if (!links?.filters.isNullOrEmpty() || stockFilter != null) FilterChip(
                    selected = stockFiltered, onClick = { dialog = "filters" }, enabled = !busy,
                    modifier = Modifier.widthIn(max = if (query.isBlank()) 152.dp else 116.dp),
                    label = { Text(messageText(currentCategory?.label ?: stringResource(R.string.game_ui_filter)), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.FilterList, null, Modifier.size(18.dp)) },
                )
                if (query.isNotBlank()) InputChip(selected = true, onClick = { dialog = "search" },
                    modifier = Modifier.widthIn(max = 112.dp).padding(start = 4.dp),
                    label = { Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.game_ui_clear_search)) } },
                ) else if (page.sections.any { it.title == "Items" }) IconButton(onClick = { dialog = "search" }) {
                    Icon(Icons.Default.Search, stringResource(R.string.game_ui_search))
                }
                if (wallet != null) IconButton(onClick = { dialog = "transfer" }, enabled = !busy) {
                    Icon(Icons.Default.AccountBalanceWallet, stringResource(R.string.game_ui_market_wallet))
                }
            }
        }
        if (detail && detailTabs.size > 1) PrimaryTabRow(selectedTabIndex = detailTabs.indexOf(activeTab)) {
            detailTabs.forEach { tab ->
                Tab(selected = activeTab == tab, onClick = {
                    scope.launch { listState.scrollToItem(0); detailTab = tab }
                }, text = { Text(marketGroupLabel(tab)) })
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(page.messages) { message ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Text(messageText(message), Modifier.fillMaxWidth().padding(12.dp))
                }
            }
            visibleGroups.forEachIndexed { index, group ->
                val entries = group.entries.filter { detail || query.isBlank() || it.title.contains(query, true) || translator.translate(it.title).contains(query, true) }
                val orderBook = group.isOrderBook()
                item(key = "heading-$index") {
                    if (group.title != "Items" && !(detail && group.title == activeTab) && !(group.entries.isEmpty() && group.emptyText.isBlank())) {
                        Text(marketGroupLabel(group.title), Modifier.padding(top = 12.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (group.facts.isNotEmpty()) BazaarFacts(group.facts)
                    if (entries.isEmpty()) Text(
                        if (query.isNotBlank()) stringResource(R.string.game_ui_no_matching_items)
                        else messageText(group.emptyText.ifBlank { stringResource(R.string.game_ui_no_items) }),
                        Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else if (group.title == "Items") MarketColumns(
                        stringResource(R.string.game_ui_market_items), stringResource(R.string.game_ui_market_bid), stringResource(R.string.game_ui_market_ask), header = true,
                    ) else if (orderBook) MarketColumns(
                        stringResource(R.string.game_ui_market_count), stringResource(R.string.game_ui_market_price), stringResource(R.string.game_ui_market_total), header = true,
                    )
                }
                items(entries, key = { "$index/${it.key}" }) { entry ->
                    when {
                        group.title == "Items" -> MarketItemRow(entry, busy) { entry.action?.let(onAction) }
                        orderBook -> MarketOfferRow(entry, busy) { useOffer(entry) }
                        else -> MarketRecordRow(entry, group.title) { recordKey = entry.key }
                    }
                }
            }
            if (!links?.pages.isNullOrEmpty()) item(key = "page-links") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    links?.pages?.forEach { link ->
                        TextButton(onClick = { onAction(link.action) }, enabled = !busy) { Text(messageText(link.label)) }
                    }
                }
            }
            if (page.actions.isNotEmpty()) item(key = "page-actions") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    page.actions.forEach { action -> TextButton(onClick = { onAction(action) }, enabled = !busy) { Text(messageText(action.label)) } }
                }
            }
        }
        if (detail && orderForms.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                orderForms.sortedBy { it.validation != "market_sell" }.forEach { form ->
                    val buying = form.validation == "market_buy"
                    Button(onClick = { dialog = form.key }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        colors = if (buying) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()) {
                        Text(stringResource(if (buying) R.string.game_ui_market_buy_order else R.string.game_ui_market_sell_order))
                    }
                }
            }
        }
    }
    when (dialog) {
        "search" -> BazaarSearchDialog(query, onDismiss = { dialog = null }, onSearch = { term ->
            scope.launch { listState.scrollToItem(0); query = term; dialog = null }
        })
        "filters" -> MarketFilterDialog(links?.filters.orEmpty(), stockFilter, busy,
            onDismiss = { dialog = null }, onApply = { dialog = null; onAction(it) })
        "rules" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text(stringResource(R.string.game_ui_market_rules)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rules.forEach { Text(messageText(it)) }
            } }, confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.game_ui_close)) } })
        else -> page.forms.firstOrNull { it.key == dialog }?.let { form ->
            MarketFormDialog(form, itemName, entered(form), busy,
                edited = form.fields.any { values["${form.key}/${it.key}"] != initialValues["${form.key}/${it.key}"] },
                onChange = { key, value -> values["${form.key}/$key"] = value },
                onPreset = { preset -> values.putAll(preset.applyTo(values)) },
                onDismiss = { dialog = null }, onSubmit = { submit(form, it) })
        }
    }
    page.sections.flatMap { it.entries }.firstOrNull { it.key == recordKey }?.let { record ->
        val priceHistory = page.sections.any { it.title == "Price History" && record in it.entries }
        AlertDialog(onDismissRequest = { recordKey = null }, title = { Text(if (priceHistory) messageText(record.title) else record.title) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { MarketRecordFacts(record.details) } },
            confirmButton = { TextButton(onClick = { recordKey = null }) { Text(stringResource(R.string.game_ui_close)) } },
            dismissButton = { record.action?.let { action ->
                TextButton(onClick = { recordKey = null; onAction(action) }, enabled = !busy) { Text(stringResource(R.string.game_ui_market_open_record)) }
            } })
    }
}

@Composable
private fun marketViewLabel(label: String): String = when (label) {
    "Browse Items" -> stringResource(R.string.game_ui_market_items)
    "My Buy Orders" -> stringResource(R.string.game_ui_market_my_buy_orders)
    "My Sell Orders" -> stringResource(R.string.game_ui_market_my_sell_orders)
    "Account Log" -> stringResource(R.string.game_ui_market_account_log)
    "Trade Log" -> stringResource(R.string.game_ui_market_trade_log)
    else -> messageText(label)
}

@Composable
private fun marketGroupLabel(label: String): String = when (label) {
    "Orders" -> stringResource(R.string.game_ui_market_orders)
    "Listed Sell Orders" -> stringResource(R.string.game_ui_market_listed_sell_orders)
    "Listed Buy Orders" -> stringResource(R.string.game_ui_market_listed_buy_orders)
    "Price History" -> stringResource(R.string.game_ui_market_price_history)
    "Recent Trades" -> stringResource(R.string.game_ui_market_recent_trades)
    "Account Log" -> stringResource(R.string.game_ui_market_account_log)
    "Trade Log" -> stringResource(R.string.game_ui_market_trade_log)
    "Market History" -> stringResource(R.string.game_ui_market_history)
    "Trades" -> stringResource(R.string.game_ui_market_trades)
    else -> when {
        label.equals("Listed Sell Orders", ignoreCase = true) -> stringResource(R.string.game_ui_market_listed_sell_orders)
        label.equals("Listed Buy Orders", ignoreCase = true) -> stringResource(R.string.game_ui_market_listed_buy_orders)
        else -> messageText(label)
    }
}

private fun BazaarSectionsGroup.isOrderBook(): Boolean =
    title.equals("Listed Sell Orders", ignoreCase = true) || title.equals("Listed Buy Orders", ignoreCase = true)

@Composable
private fun MarketColumns(first: String, second: String, third: String, header: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = if (header) 8.dp else 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
        val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        Text(first, Modifier.weight(1.6f), style = style, color = color)
        Text(second, Modifier.weight(1f), style = style, color = color, textAlign = TextAlign.End)
        Text(third, Modifier.weight(1f), style = style, color = color, textAlign = TextAlign.End)
    }
}

@Composable
private fun MarketItemRow(entry: BazaarSectionsEntry, busy: Boolean, onClick: () -> Unit) {
    val facts = entry.details.toMap()
    Column(Modifier.fillMaxWidth().clickable(enabled = !busy && entry.enabled && entry.action != null, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 12.dp), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1.6f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(messageText(entry.title), style = MaterialTheme.typography.titleSmall)
                facts["Your Stock"]?.let { owned -> Text(stringResource(R.string.game_ui_market_owned, owned),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                facts["Order Count"]?.let { count ->
                    Text("$count × ${facts["Order Price"].orEmpty()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(facts["Market Bid"] ?: "—", Modifier.weight(1f), textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(facts["Market Ask"] ?: "—", textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                facts["Market Stock"]?.let { stock ->
                    val available = stringResource(R.string.game_ui_market_available, stock)
                    Text("× $stock", Modifier.semantics { contentDescription = available }, textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun MarketOfferRow(entry: BazaarSectionsEntry, busy: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = !busy && entry.enabled && entry.prefill != null, onClick = onClick)) {
        MarketColumns(entry.title, entry.details.firstOrNull { it.first == "Price" }?.second.orEmpty(),
            entry.details.firstOrNull { it.first == "Total" }?.second.orEmpty())
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarketRecordRow(entry: BazaarSectionsEntry, group: String, onClick: () -> Unit) {
    val detail = entry.details.toMap()
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Text(if (group == "Price History") messageText(entry.title) else entry.title, Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (detail["Total"] ?: detail["Amount"] ?: detail["Price"] ?: detail["Avg"])?.let {
                Text(if (group == "Price History") "${stringResource(R.string.game_ui_market_average)} $it" else it,
                    style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End)
            }
        }
        if (group == "Price History") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                entry.details.filter { it.first in setOf("Low", "High", "Sold", "Vol") }.forEach { (label, value) ->
                    Text("${marketRecordLabel(label)} ${messageText(value)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (detail["Item"] != null) {
            Text("${messageText(detail.getValue("Item"))} × ${detail["Count"].orEmpty()}", style = MaterialTheme.typography.bodyMedium)
        } else if (detail["Info"] != null) Text(messageText(detail.getValue("Info")), style = MaterialTheme.typography.bodyMedium)
        else {
            val quantity = detail["Sold"] ?: detail["Count"]
            if (quantity != null) Text("${stringResource(R.string.game_ui_market_count)} $quantity", style = MaterialTheme.typography.bodyMedium)
            if (detail["Seller"] != null || detail["Buyer"] != null) Text(
                "${detail["Seller"].orEmpty()} → ${detail["Buyer"].orEmpty()}",
                style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun MarketRecordFacts(facts: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        facts.filterNot { it.first.isBlank() }.forEach { (label, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(marketRecordLabel(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (label in setOf("Seller", "Buyer")) value else messageText(value), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun marketRecordLabel(label: String): String = when (label) {
    "Sold" -> stringResource(R.string.game_ui_market_count)
    "Vol" -> stringResource(R.string.game_ui_market_volume)
    "Avg" -> stringResource(R.string.game_ui_market_average)
    else -> messageText(label)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarketFilterDialog(
    categories: List<BazaarSectionsLink>,
    form: BazaarSectionsForm?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onApply: (NativeAction) -> Unit,
) {
    var selectedCategory by remember { mutableIntStateOf(categories.indexOfFirst { it.selected }.coerceAtLeast(0)) }
    val fields = remember(form) { mutableStateMapOf<String, String>().apply {
        form?.fields?.forEach { put(it.key, if (it.kind == "checkbox" && !it.checked) "" else it.value) }
    } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.game_ui_filter)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEachIndexed { index, link ->
                    FilterChip(selected = index == selectedCategory, onClick = { selectedCategory = index },
                        label = { Text(messageText(link.label)) }, enabled = !busy)
                }
            }
            form?.fields?.forEach { field -> BazaarField(field, fields[field.key].orEmpty(), !busy) { fields[field.key] = it } }
        }
    }, confirmButton = {
        TextButton(onClick = {
            val category = categories.getOrNull(selectedCategory)
            val filtered = form?.actions?.firstOrNull()?.let { form.submit(it, fields) }
            val action = if (category != null && filtered != null) {
                val url = category.action.url.newBuilder()
                form.fields.forEach { field ->
                    url.removeAllQueryParameters(field.name)
                    filtered.url.queryParameterValues(field.name).forEach { url.addQueryParameter(field.name, it) }
                }
                category.action.copy(url = url.build())
            } else filtered ?: category?.action
            if (action != null) onApply(action) else onDismiss()
        }, enabled = !busy) { Text(stringResource(R.string.game_ui_apply)) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_ui_cancel)) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarketFormDialog(
    form: BazaarSectionsForm,
    itemName: String,
    values: Map<String, String>,
    busy: Boolean,
    edited: Boolean,
    onChange: (String, String) -> Unit,
    onPreset: (BazaarSectionsPreset) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (NativeAction) -> Unit,
) {
    val order = form.validation in setOf("market_buy", "market_sell")
    val largeText = LocalDensity.current.fontScale >= 1.3f
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (order) stringResource(if (form.validation == "market_buy") R.string.game_ui_market_buy_order else R.string.game_ui_market_sell_order)
            else stringResource(R.string.game_ui_market_wallet)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (order) {
                    Text(messageText(itemName), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.game_ui_market_batch_quantity, form.batchSize), style = MaterialTheme.typography.labelMedium)
                        if (form.validation == "market_buy") form.balance?.let {
                            Text("${messageText("Market Balance")} $it C", style = MaterialTheme.typography.labelMedium)
                        } else form.stock?.let {
                            Text(stringResource(R.string.game_ui_market_available, it.toString()), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else BazaarFacts(form.facts)
                val inputFields = form.fields.map { field -> if (!order) field else field.copy(label = when {
                    field.name.endsWith("_batchcount") -> stringResource(R.string.game_ui_market_batches)
                    field.name.endsWith("_batchprice") -> stringResource(R.string.game_ui_market_batch_price)
                    else -> field.label
                }) }
                if (order && inputFields.size == 2 && !largeText) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    inputFields.forEach { field -> Box(Modifier.weight(1f)) {
                        BazaarField(field, values[field.key].orEmpty(), !busy) { onChange(field.key, it) }
                    } }
                } else inputFields.forEach { field -> BazaarField(field, values[field.key].orEmpty(), !busy) { onChange(field.key, it) } }
                if (form.presets.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    form.presets.forEach { preset ->
                        TextButton(onClick = { onPreset(preset) }, enabled = !busy) { Text(messageText(preset.label)) }
                    }
                }
                BazaarFacts(form.orderSummary(values))
                val submitActions = form.actions.filterNot { action -> action.fields.any { it.first in setOf("buyorder_cancel", "sellorder_cancel") } }
                val errors = submitActions.map { form.validationError(it, values) }
                if (edited && errors.isNotEmpty() && errors.all { it != null }) {
                    Text(messageText(errors.first().orEmpty()), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                form.actions.forEach { action ->
                    TextButton(onClick = { onSubmit(action) }, enabled = !busy && form.validationError(action, values) == null) {
                        Text(messageText(action.label))
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_ui_close)) } },
    )
}
