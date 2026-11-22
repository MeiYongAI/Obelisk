package com.mitahv.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.i18n.gameText
import com.mitahv.mobile.i18n.messageText
import com.mitahv.mobile.model.*
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun ItemShopScreen(page: BazaarSectionsPage, busy: Boolean, responseRevision: Int,
    onAction: (NativeAction) -> Unit) {
    val filter = page.links.firstOrNull { it.selected } ?: page.links.firstOrNull()
    val filterKey = filter?.action?.url?.toString().orEmpty()
    var buying by rememberSaveable { mutableStateOf(true) }
    var filtersOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by rememberSaveable(filterKey) { mutableStateOf("") }
    var selectedKey by rememberSaveable(responseRevision, filterKey) { mutableStateOf<String?>(null) }
    val listStates = rememberSaveableStateHolder()
    val group = page.sections.firstOrNull { it.title == if (buying) "Item Shop" else "Your Inventory" }
    val entries = group?.entries.orEmpty()
    val visible = entries.filter { query.isBlank() || it.title.contains(query, true) || gameText(it.title).contains(query, true) }

    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = if (buying) 0 else 1) {
            listOf(R.string.game_ui_shop_buy, R.string.game_ui_shop_sell).forEachIndexed { index, label ->
                Tab(selected = buying == (index == 0), onClick = { buying = index == 0 },
                    text = { Text(stringResource(label)) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (filter != null) TextButton(onClick = { filtersOpen = true }, enabled = !busy,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(messageText(filter.label), Modifier.weight(1f), textAlign = TextAlign.Start,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.ExpandMore, null, Modifier.size(20.dp))
            } else Spacer(Modifier.weight(1f))
            page.shopBalance?.let { balance ->
                val description = stringResource(R.string.shop_balance, shopNumber(balance))
                Row(Modifier.padding(horizontal = 8.dp).semantics(mergeDescendants = true) { contentDescription = description },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.AccountBalanceWallet, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${shopNumber(balance)} C", style = MaterialTheme.typography.labelLarge)
                }
            }
            IconButton(onClick = { searchOpen = true }) {
                Icon(Icons.Default.Search, stringResource(R.string.game_ui_search))
            }
        }
        if (query.isNotBlank()) InputChip(selected = true, onClick = { searchOpen = true },
            label = { Text(query) }, modifier = Modifier.padding(horizontal = 16.dp),
            trailingIcon = { IconButton(onClick = { query = "" }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, stringResource(R.string.game_ui_clear_search), Modifier.size(18.dp))
            } })
        listStates.SaveableStateProvider("$filterKey:$buying:$query") {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(page.messages) { message ->
                    Surface(Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                        Text(messageText(message), Modifier.fillMaxWidth().padding(12.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                if (visible.isEmpty()) item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(if (query.isNotBlank()) stringResource(R.string.game_ui_no_matching_items)
                            else group?.emptyText?.takeIf { it.isNotBlank() }?.let { messageText(it) }
                                ?: stringResource(R.string.game_ui_no_items), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(visible, key = { it.key }) { entry ->
                    ItemShopRow(entry, buying, busy) { selectedKey = entry.key }
                }
            }
        }
    }
    if (filtersOpen) MobileChoiceSheet(
        title = stringResource(R.string.shop_category),
        choices = page.links.mapIndexed { index, link -> MobileChoice(index.toString(), link.label, enabled = !busy) },
        selectedValue = page.links.indexOf(filter).toString(), searchable = false,
        onSelect = { index -> page.links[index.toInt()].takeUnless { it.selected }?.let { query = ""; onAction(it.action) } },
        onDismiss = { filtersOpen = false },
    )
    if (searchOpen) BazaarSearchDialog(query, onDismiss = { searchOpen = false },
        onSearch = { query = it; searchOpen = false })
    entries.firstOrNull { it.key == selectedKey }?.let { entry ->
        ItemShopSheet(entry, buying, busy, onDismiss = { selectedKey = null }, onSubmit = { count ->
            // This sheet already reviews the item, quantity and total before the explicit Buy/Sell tap.
            selectedKey = null
            onAction(entry.quantity!!.submit(count).copy(confirmation = null))
        })
    }
}

private fun shopNumber(value: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(value)

@Composable
private fun ItemShopRow(entry: BazaarSectionsEntry, buying: Boolean, busy: Boolean, onClick: () -> Unit) {
    val item = entry.shopItem
    Surface(onClick = onClick, enabled = !busy, color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(gameText(entry.title), style = MaterialTheme.typography.bodyLarge)
                    if (item != null) Text(if (buying) stringResource(R.string.shop_owned, shopNumber(item.owned))
                        else "× ${shopNumber(item.stock)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (item?.stock == 0L) stringResource(R.string.shop_out_of_stock)
                    else entry.quantity?.unitCost?.let { "${shopNumber(it)} C" }.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (entry.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ItemShopSheet(entry: BazaarSectionsEntry, buying: Boolean, busy: Boolean,
    onDismiss: () -> Unit, onSubmit: (Long) -> Unit) {
    val quantity = entry.quantity
    val maximum = quantity?.maximum ?: 0L
    var value by rememberSaveable(entry.key) { mutableStateOf(if (maximum > 0) "1" else "0") }
    val count = value.toLongOrNull()
    val valid = quantity != null && count != null && count in quantity.minimum..maximum
    val title = gameText(entry.title)
    val focusManager = LocalFocusManager.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.semantics { paneTitle = title }) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.game_ui_close)) }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (entry.moreDetails.isNotEmpty()) Text(gameText(entry.moreDetails.joinToString("\n") { it.second }),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    quantity?.unitCost?.let { ShopStat(stringResource(R.string.shop_unit_price), "${shopNumber(it)} C", Modifier.weight(1f)) }
                    entry.shopItem?.let { item ->
                        ShopStat(stringResource(R.string.shop_owned_label), shopNumber(item.owned), Modifier.weight(1f))
                        if (buying) ShopStat(stringResource(R.string.shop_stock), shopNumber(item.stock), Modifier.weight(1f))
                    }
                }
                if (maximum > 0 && quantity != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalIconButton(onClick = { value = ((count ?: quantity.minimum) - 1).coerceAtLeast(quantity.minimum).toString() },
                                enabled = !busy && (count ?: 0) > quantity.minimum, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Remove, stringResource(R.string.game_ui_decrease_quantity))
                            }
                            OutlinedTextField(value, { if (it.all(Char::isDigit)) value = it }, Modifier.weight(1f),
                                label = { Text(stringResource(R.string.game_ui_quantity)) }, singleLine = true,
                                enabled = !busy, isError = value.isNotBlank() && !valid,
                                supportingText = { Text(stringResource(R.string.shop_maximum, shopNumber(maximum))) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center))
                            FilledTonalIconButton(onClick = { value = ((count ?: 0).coerceAtMost(maximum - 1) + 1).toString() },
                                enabled = !busy && (count ?: 0) < maximum, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Add, stringResource(R.string.game_ui_increase_quantity))
                            }
                        }
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1L, 10L, 100L).filter { it < maximum }.forEach { amount ->
                                OutlinedButton(onClick = { value = amount.toString(); focusManager.clearFocus() }, enabled = !busy,
                                    contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(shopNumber(amount)) }
                            }
                            OutlinedButton(onClick = { value = maximum.toString(); focusManager.clearFocus() }, enabled = !busy,
                                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.shop_max)) }
                        }
                    }
                }
            }
            val action = stringResource(if (buying) R.string.game_ui_shop_buy else R.string.game_ui_shop_sell)
            val total = if (valid && quantity?.unitCost != null) shopNumber(count!! * quantity.unitCost) else null
            Button(onClick = { if (!busy && valid) onSubmit(count!!) }, enabled = !busy && entry.enabled && valid,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).heightIn(min = 52.dp)) {
                Text(when {
                    entry.shopItem?.stock == 0L -> stringResource(R.string.shop_out_of_stock)
                    buying && maximum == 0L -> stringResource(R.string.shop_insufficient_credits)
                    total != null -> "$action · $total C"
                    else -> action
                })
            }
        }
    }
}

@Composable
private fun ShopStat(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
