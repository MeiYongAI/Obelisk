package com.mitahv.mobile

import androidx.compose.ui.res.stringResource
import com.mitahv.mobile.i18n.gameText

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvSection
import com.mitahv.mobile.model.HvWorld
import kotlinx.coroutines.launch

@Composable
internal fun SectionPageTitle(
    route: HvRoute,
    enabled: Boolean,
    expanded: Boolean = false,
    onClick: () -> Unit,
) {
    val pageLabel = gameText(route.label)
    val menuLabel = stringResource(R.string.game_ui_select_section, gameText(route.category.label))
    val menuState = stringResource(
        if (expanded) R.string.game_ui_menu_expanded else R.string.game_ui_menu_collapsed,
    )
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.textButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.heightIn(min = 48.dp).semantics {
            contentDescription = "$pageLabel, $menuLabel"
            stateDescription = menuState
        },
    ) {
        Text(
            pageLabel,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            Icons.Filled.ExpandMore,
            null,
            Modifier.padding(start = 4.dp).size(24.dp).rotate(if (expanded) 180f else 0f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SectionNavigationSheet(
    section: HvSection,
    routes: List<HvRoute>,
    selectedRoute: HvRoute,
    onRouteSelected: (HvRoute) -> Unit,
    onDismiss: () -> Unit,
) {
    val sectionLabel = gameText(section.label)
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    fun closeWith(action: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            sheetState.hide()
            if (!sheetState.isVisible) action() else closing = false
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.semantics { paneTitle = sectionLabel },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    sectionLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = { closeWith(onDismiss) }, enabled = !closing) {
                    Icon(Icons.Filled.Close, stringResource(R.string.game_ui_close_menu))
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                    .selectableGroup().semantics { collectionInfo = CollectionInfo(routes.size, 1) },
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(routes, key = { it.name }) { route ->
                    val selected = route == selectedRoute
                    Surface(
                        selected = selected,
                        onClick = { closeWith { onRouteSelected(route) } },
                        enabled = !closing,
                        shape = MaterialTheme.shapes.large,
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics {
                            collectionItemInfo = CollectionItemInfo(routes.indexOf(route), 1, 0, 1)
                        },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(routeIcon(route), null, Modifier.size(24.dp))
                            Text(gameText(route.label), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            if (selected) Icon(Icons.Filled.Check, null, Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompactWorldButton(
    world: HvWorld,
    enabled: Boolean,
    onWorldSelected: (HvWorld) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Text(
                gameText(world.label),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Filled.ExpandMore, stringResource(R.string.game_ui_switch_world), Modifier.padding(start = 4.dp).size(20.dp))
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            HvWorld.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(gameText(item.label)) },
                    trailingIcon = { if (item == world) Icon(Icons.Filled.Check, null) },
                    modifier = Modifier.semantics { selected = item == world },
                    onClick = {
                        expanded = false
                        if (item != world) onWorldSelected(item)
                    },
                )
            }
        }
    }
}

@Composable
internal fun StaminaButton(value: String, enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.game_ui_stamina_value, value)
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = description },
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Icon(Icons.Filled.Bolt, null, Modifier.size(20.dp))
        Text(value, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge)
        Icon(Icons.Filled.ExpandMore, null, Modifier.padding(start = 4.dp).size(20.dp))
    }
}

private fun routeIcon(route: HvRoute): ImageVector = when (route.subsection) {
    "ch" -> Icons.Filled.Person
    "eq" -> Icons.Filled.Shield
    "ab" -> Icons.Filled.AutoAwesome
    "tr" -> Icons.Filled.FitnessCenter
    "it" -> Icons.Filled.Inventory2
    "se" -> Icons.Filled.Settings
    "is" -> Icons.Filled.Storefront
    "ss" -> Icons.Filled.Star
    "mk" -> Icons.Filled.Gavel
    "am" -> Icons.Filled.Shield
    "ml" -> Icons.Filled.Pets
    "mm" -> Icons.Filled.Mail
    "lt" -> Icons.Filled.Casino
    "la" -> Icons.Filled.ConfirmationNumber
    "ar" -> Icons.Filled.EmojiEvents
    "rb" -> Icons.Filled.Whatshot
    "gr" -> Icons.Filled.AllInclusive
    "iw" -> Icons.Filled.Public
    "tw" -> Icons.Filled.Apartment
    else -> Icons.Filled.Public
}
