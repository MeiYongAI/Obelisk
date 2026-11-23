package com.mitahv.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.mitahv.mobile.data.*

private data class RuleAddress(val group: String, val index: Int)
internal typealias RuleEdit = (MutableList<JsonObject>) -> Unit

@Composable
internal fun JpxConfigDetails(doc: JpxDocument, subtitle: String, busy: Boolean, error: Int, onBack: () -> Unit,
    onApply: () -> Unit, onExport: () -> Unit,
    onEdit: (String, RuleEdit, () -> Unit) -> Unit) {
    val current = doc.draft.modes.single()
    var editing by remember { mutableStateOf<RuleAddress?>(null) }; var deleting by remember { mutableStateOf<RuleAddress?>(null) }
    editing?.let { at ->
        val group=doc.rules(current,at.group)
        val original = if (at.index >= 0) group[at.index].asJsonObject else null
        JpxRuleEditor(original, busy, error, onBack={ editing=null }) { value -> onEdit(at.group,{ rules ->
            if (at.index < 0) rules.add(value) else rules[at.index]=value
        }, { editing=null }) }
        return
    }
    fun back() { if (!busy) onBack() }
    BackHandler { back() }
    Surface(Modifier.fillMaxSize()) { Column(Modifier.safeDrawingPadding().fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack,stringResource(R.string.app_back)) }
            Column(Modifier.weight(1f)) {
                Text(modeLabel(current),style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(subtitle,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            IconButton(enabled=!busy,onClick=onExport) { Icon(Icons.Filled.SaveAlt,stringResource(R.string.jpx_export_mode)) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error!=0) Text(stringResource(error),Modifier.padding(12.dp), color=MaterialTheme.colorScheme.error)
        key(current) { LazyColumn(Modifier.weight(1f).widthIn(max=720.dp).fillMaxWidth().align(Alignment.CenterHorizontally),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            for (group in doc.groups(current)) {
                val rules=doc.rules(current,group)
                item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(if(group.startsWith("kb_")) stringResource(R.string.jpx_keybind,group.removePrefix("kb_")) else fieldLabel(group),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    TextButton(enabled=!busy,onClick={editing=RuleAddress(group,-1)}) { Text(stringResource(R.string.jpx_add_rule)) }
                } }
                if(rules.isEmpty) item { Text(stringResource(R.string.jpx_empty_group),style=MaterialTheme.typography.bodySmall) }
                items(rules.size()) { index -> val rule=rules[index].asJsonObject; val address=RuleAddress(group,index)
                    var menu by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).clickable(enabled=!busy) { editing=address }.heightIn(min=64.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text("${index+1}",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
                            Column {
                                val action=rule.text("type"); val name=rule.text("name"); val cond=rule.getAsJsonArray("conditions")?.size() ?: 0
                                Text(if (name.isEmpty()) fieldLabel(action) else "${fieldLabel(action)} · ${optionLabel(name)}",style=MaterialTheme.typography.titleSmall)
                                val status=if (cond==0) stringResource(R.string.jpx_always) else stringResource(R.string.jpx_cond_count,cond)
                                Text(if(rule.text("disabled")=="true") "${stringResource(R.string.jpx_disabled)} · $status" else status,style=MaterialTheme.typography.bodySmall)
                            }
                        }
                        Box { IconButton(enabled=!busy,onClick={menu=true}) { Icon(Icons.Filled.MoreVert,stringResource(R.string.jpx_options_rule,index+1)) }
                            DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                                for ((offset,label) in listOf(-1 to R.string.jpx_up,1 to R.string.jpx_down)) DropdownMenuItem(text={Text(stringResource(label))},enabled=index+offset in 0 until rules.size(),onClick={menu=false;onEdit(group,{it.add(index+offset,it.removeAt(index))},{})})
                                DropdownMenuItem(text={Text(stringResource(R.string.jpx_remove))},onClick={menu=false;deleting=address})
                            }
                        }
                    }; HorizontalDivider()
                }
            }
        } }
        Button(enabled=!busy,onClick=onApply,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp)) { Text(stringResource(R.string.jpx_apply)) }
    } }
    deleting?.let { at -> AlertDialog(onDismissRequest={deleting=null},title={Text(stringResource(R.string.jpx_delete_rule))},text={Text(stringResource(R.string.jpx_delete_hint))},confirmButton={TextButton(enabled=!busy,onClick={onEdit(at.group,{rows->rows.removeAt(at.index)},{deleting=null})}){Text(stringResource(R.string.jpx_remove))}},dismissButton={TextButton(onClick={deleting=null}){Text(stringResource(R.string.game_ui_cancel))}}) }
}
