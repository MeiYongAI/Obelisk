package com.mitahv.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mitahv.mobile.i18n.messageText
import com.mitahv.mobile.model.*
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl

internal data class EquippedUpgrades(val rows: List<CharacterSectionRow>, val incomplete: Boolean)

/** Tooltips on the slot bar cover all trees. Only a tree's own enabled POST authorizes spending AP. */
internal suspend fun equippedUpgrades(page: CharacterSectionsPage, load: suspend (HttpUrl) -> CharacterSectionsPage): EquippedUpgrades {
    val slots = page.groups.flatMap { it.rows }.mapNotNull { it.abilitySlot }.filter { it.unlocked && it.equippedName.isNotBlank() }
    val remaining = slots.filter { it.mayUpgrade }.map { it.family to it.equippedName }.toMutableSet()
    val equipped = slots.map { it.family to it.equippedName }.toSet()
    val found = linkedMapOf<String, CharacterSectionRow>()
    fun inspect(tree: CharacterSectionsPage) {
        for (row in tree.groups.flatMap { it.rows }) {
            val ability = row.ability ?: continue
            val key = ability.family to row.title
            if (key !in equipped) continue
            remaining.remove(key)
            if (ability.acquired && ability.equippedSlotIds.isNotEmpty() && row.actions.any { it.method == "POST" &&
                    ("unlock_ability" to ability.id) in it.fields }) found[ability.id] = row
        }
    }
    inspect(page)
    for (link in page.navigation) {
        if (remaining.isEmpty()) break
        val url = link.action?.takeIf { !link.selected && it.method == "GET" }?.url ?: continue
        if (link.abilityFamilies.isNotEmpty() && remaining.none { it.first in link.abilityFamilies }) continue
        try {
            val tree = load(url)
            if (tree.navigation.any { it.selected && it.abilityTreeId == link.abilityTreeId }) inspect(tree)
        } catch (e: CancellationException) { throw e } catch (_: java.io.IOException) { continue }
        catch (_: IllegalArgumentException) { continue }
    }
    return EquippedUpgrades(found.values.toList(), remaining.isNotEmpty())
}

@Composable
internal fun EquippedUpgradeDialog(page: CharacterSectionsPage, busy: Boolean, load: suspend (HttpUrl) -> CharacterSectionsPage,
    error: String?, refresh: () -> Unit, onAction: (NativeAction) -> Unit, onClose: () -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    var result by remember(page, busy, retry) { mutableStateOf<EquippedUpgrades?>(null) }
    LaunchedEffect(page, busy, retry) { if (!busy) result = equippedUpgrades(page, load) }
    AbilityPickerDialog(stringResource(R.string.ability_ui_equipped_upgrades), onClose) {
        val data = result
        page.notices.forEach { Text(messageText(it), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall) }
        when {
            error != null -> Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(messageText(error), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = refresh) { Text(stringResource(R.string.app_refresh)) }
            }
            data == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            else -> LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.rows, key = { it.ability!!.id }) { row ->
                    val info = requireNotNull(row.ability)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AbilityArtwork(row.title, info.family, size = 28.dp)
                        Column(Modifier.weight(1f)) {
                            Text(messageText(row.title), style = MaterialTheme.typography.titleSmall)
                            if (info.maxTier > 0) Text(stringResource(R.string.ability_ui_rank, info.currentTier, info.maxTier), style = MaterialTheme.typography.bodySmall)
                        }
                        AbilityAdvanceButton(row, row.actions.first { it.method == "POST" && ("unlock_ability" to info.id) in it.fields }, busy, onAction)
                    }
                }
                if (data.incomplete) item { Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ability_ui_upgrades_failed), Modifier.weight(1f))
                    TextButton(onClick = { retry++ }) { Text(stringResource(R.string.app_retry)) }
                } } else if (data.rows.isEmpty()) item { Text(stringResource(R.string.ability_ui_no_upgrades)) }
            }
        }
    }
}
