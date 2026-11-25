package com.mitahv.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.gson.*
import com.mitahv.mobile.data.JpxFields
import com.mitahv.mobile.i18n.gameActionText

@Composable
internal fun fieldLabel(key: String): String {
    val keys = stringArrayResource(R.array.jpx_field_keys)
    return stringArrayResource(R.array.jpx_field_labels).getOrNull(keys.indexOf(key)) ?: key
}

@Composable
internal fun optionLabel(value: String): String {
    val keys = stringArrayResource(R.array.jpx_option_keys)
    return stringArrayResource(R.array.jpx_option_labels).getOrNull(keys.indexOf(value)) ?: gameActionText(value)
}

@Composable
private fun Choice(label: String, value: String, options: List<String>, display: @Composable (String) -> String, change: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { open = true }) { Text(display(value)) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                for (option in options) DropdownMenuItem(text = { Text(display(option)) }, onClick = { change(option); open = false })
            }
        }
    }
}

internal fun JsonObject.text(key: String): String = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
internal fun ruleTemplate(type: String) = JsonObject().apply {
    addProperty("type", type)
    JpxFields.choices(type).firstOrNull()?.let { addProperty("name", it) }
    for (key in JpxFields.extras(type)) when (key) {
        "priorityRule" -> addProperty(key, JpxFields.choices(key).first())
        "toggled", "bottomUp" -> addProperty(key, false)
        "targetCount" -> addProperty(key, 3)
        "tailSkip" -> addProperty(key, 0)
        "maxAtFirst", "minMonstersLeft" -> addProperty(key, 10)
        else -> addProperty(key, "")
    }
    add("conditions", JsonArray())
}

@Composable
internal fun JpxRuleEditor(original: JsonObject?, types: List<String>, defaultType: String, busy: Boolean, error: Int,
    onBack: () -> Unit, onSave: (JsonObject) -> Unit) {
    val initial = remember(original, defaultType) { original ?: ruleTemplate(defaultType) }
    var rule by remember(initial) { mutableStateOf(initial.deepCopy()) }
    var bad by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var condition by remember { mutableStateOf<Int?>(null) }
    fun mutate(block: JsonObject.() -> Unit) { rule = rule.deepCopy().apply(block); bad = false }
    fun leave() { if (busy) return; if (rule != initial) discard = true else onBack() }
    BackHandler { leave() }
    val type = rule.text("type")
    val conditions = rule.getAsJsonArray("conditions") ?: JsonArray()
    Surface(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.app_back)) }
            Text(stringResource(if (original == null) R.string.jpx_add_rule else R.string.jpx_edit_rule), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).widthIn(max = 720.dp).fillMaxWidth().align(Alignment.CenterHorizontally).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Choice(stringResource(R.string.jpx_action), type, (types + type).distinct(), { fieldLabel(it) }) { next ->
                if (next != type) {
                    val old = rule
                    rule = ruleTemplate(next).apply {
                        for ((k,v) in old.entrySet()) if (k !in JpxFields.extras(type) && k !in setOf("type", "name", "bottomUp", "code")) add(k,v.deepCopy())
                    }; bad = false
                }
            }
            val options = JpxFields.choices(type)
            if (options.isNotEmpty()) Choice(stringResource(R.string.jpx_name), rule.text("name"), (options + rule.text("name")).filter { it.isNotEmpty() }.distinct(), { optionLabel(it) }) { mutate { addProperty("name", it) } }
            else if (rule.has("name")) OutlinedTextField(rule.text("name"), { mutate { addProperty("name", it) } }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.jpx_name)) })
            for (key in (JpxFields.extras(type) + listOf("code", "bottomUp").filter { rule.has(it) && !(type == "target" && it == "bottomUp") }).distinct()) {
                when (key) {
                    "priorityRule" -> Choice(fieldLabel(key), rule.text(key).ifBlank { if (rule.text("bottomUp") == "true") "Bottom Up" else "Top Down" },
                        JpxFields.choices(key), { optionLabel(it) }) { mutate { addProperty(key,it); remove("bottomUp") } }
                    "targetCount" -> Choice(fieldLabel(key), rule.text(key).ifBlank { "3" }, listOf("3", "2", "1"), { it }) {
                        mutate { addProperty(key, it.toInt()) }
                    }
                    "bottomUp", "toggled" -> BoolRow(fieldLabel(key), rule.text(key) == "true") { mutate { addProperty(key,it) } }
                    "code" -> OutlinedTextField(rule.text(key), { mutate { addProperty(key, it) } },
                        Modifier.fillMaxWidth(), minLines = 5, maxLines = 14,
                        label = { Text(stringResource(R.string.jpx_code)) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false))
                    else -> {
                        val integer = key in setOf("tailSkip", "minMonstersLeft", "maxAtFirst")
                        OutlinedTextField(rule.text(key), { raw -> mutate {
                            if (integer && raw.toIntOrNull() != null) addProperty(key, raw.toInt()) else addProperty(key, raw)
                        } }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(fieldLabel(key)) },
                            keyboardOptions = if (integer && key != "tailSkip") KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default)
                    }
                }
            }
            BoolRow(stringResource(R.string.jpx_enabled), rule.text("disabled") != "true") { mutate { addProperty("disabled", !it) } }
            if (type != "kamisamaniNattaHi" || conditions.size() > 0) {
                HorizontalDivider()
                Text(stringResource(R.string.jpx_conds), style = MaterialTheme.typography.titleMedium)
            }
            conditions.forEachIndexed { index, element ->
                val cond = element.asJsonObject
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { condition = index }.padding(vertical = 8.dp)) {
                        Text(fieldLabel(cond.text("key")), style = MaterialTheme.typography.titleSmall)
                        val value = cond["value"]
                        val summary = when {
                            value == null -> ""
                            value.isJsonArray -> value.asJsonArray.map { v ->
                                if (v.isJsonPrimitive && v.asJsonPrimitive.isString) optionLabel(v.asString)
                                else v.toString()
                            }.joinToString(" · ")
                            value.isJsonPrimitive -> value.asString // Free text (e.g. monster names) stays verbatim.
                            else -> value.toString()
                        }
                        Text(summary, style = MaterialTheme.typography.bodySmall)
                    }
                    if (conditions.size() == 1) {
                        IconButton(onClick = { mutate { getAsJsonArray("conditions").remove(index) } }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.jpx_remove))
                        }
                    } else {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Filled.MoreVert, stringResource(R.string.jpx_options, fieldLabel(cond.text("key"))))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                for ((direction, label) in listOf(-1 to R.string.jpx_up, 1 to R.string.jpx_down)) {
                                    val adjacent = index + direction
                                    DropdownMenuItem(text = { Text(stringResource(label)) }, enabled = adjacent in 0 until conditions.size(),
                                        onClick = {
                                            menu = false
                                            mutate {
                                                val items = getAsJsonArray("conditions")
                                                val moving = items[index]
                                                items.set(index, items[adjacent])
                                                items.set(adjacent, moving)
                                            }
                                        })
                                }
                                DropdownMenuItem(text = { Text(stringResource(R.string.jpx_remove)) }, onClick = {
                                    menu = false
                                    mutate { getAsJsonArray("conditions").remove(index) }
                                })
                            }
                        }
                    }
                }
            }
            if (type != "kamisamaniNattaHi") {
                OutlinedButton(onClick = { condition = conditions.size() }) { Text(stringResource(R.string.jpx_add_cond)) }
            }
        }
        if (bad || error != 0) Text(stringResource(if (bad) R.string.jpx_invalid else error), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
        Button(enabled = !busy, onClick = {
            bad = (type in JpxFields.withNames && rule.text("name").isBlank()) ||
                (type == "kamisamaniNattaHi" && rule.text("code").isBlank()) ||
                (type == "smartDebuff" && (rule.has("targetCount") && rule.text("targetCount").toIntOrNull() !in 1..3 ||
                    listOf("tailSkip", "maxAtFirst", "minMonstersLeft").any { rule.has(it) && rule.text(it).toIntOrNull() == null }))
            if (!bad) onSave(rule)
        }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { Text(stringResource(R.string.jpx_save_edit)) }
    } }
    condition?.let { index -> ConditionDialog(conditions.getOrNull(index)?.asJsonObject, onClose = { condition = null }) { result ->
        mutate { val array = getAsJsonArray("conditions") ?: JsonArray().also { add("conditions", it) }; if (index == array.size()) array.add(result) else array.set(index, result) }; condition = null
    } }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(stringResource(R.string.jpx_discard)) }, confirmButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.jpx_discard_yes)) } }, dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.game_ui_cancel)) } })
}

@Composable
private fun BoolRow(label: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(value, onCheckedChange = change) }
}

private fun JsonArray.getOrNull(index: Int) = if (index in 0 until size()) get(index) else null
private fun parts(raw: JsonElement?) = raw?.takeIf { it.isJsonArray }?.asJsonArray?.map { if (it.isJsonPrimitive) it.asString else it.toString() }.orEmpty()

internal fun conditionRange(field: String, low: String, high: String): JsonArray {
    val first = low.trim().toBigDecimal()
    val last = high.trim().toBigDecimal()
    require(first.toDouble().isFinite() && last.toDouble().isFinite())
    if (field in setOf("tIndex", "offset", "matched")) {
        require(first.stripTrailingZeros().scale() <= 0 && last.stripTrailingZeros().scale() <= 0)
    }
    // Negative monster positions count from the end. [1, -1] covers the entire roster.
    require(first <= last || field == "tIndex" && first.signum() >= 0 && last.signum() < 0)
    return JsonArray().apply { add(first); add(last) }
}

@Composable
private fun RangeFields(label: String, lo: String, hi: String, onLow: (String) -> Unit, onHigh: (String) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(lo,onLow,Modifier.weight(1f),singleLine = true,label = { Text(stringResource(R.string.jpx_min)) })
        OutlinedTextField(hi,onHigh,Modifier.weight(1f),singleLine = true,label = { Text(stringResource(R.string.jpx_max)) })
    }
}

@Composable
private fun ConditionDialog(original: JsonObject?, onClose: () -> Unit, onSave: (JsonObject) -> Unit) {
    var name by remember { mutableStateOf(original?.text("key") ?: "pHP") }
    var commit by remember { mutableStateOf<(() -> Unit)?>(null) }
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(if (original == null) R.string.jpx_add_cond else R.string.jpx_edit_cond)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice(stringResource(R.string.jpx_condition), name, (JpxFields.conditions + name).distinct(), { fieldLabel(it) }) { name = it }
            key(name) {
                val seed = original?.takeIf { it.text("key") == name }
                val raw = seed?.get("value"); val values = parts(raw)
                val range = name in JpxFields.ranges || name in JpxFields.rangeLists
                val list = name in JpxFields.rangeLists || (name in JpxFields.conditions && !range && name !in setOf("pSpiritStatus", "tName"))
                val defaultBound = if (name == "pActionHistory") "1" else "0"
                var lo by remember { mutableStateOf(values.getOrNull(0) ?: defaultBound) }
                var hi by remember { mutableStateOf(values.getOrNull(1) ?: defaultBound) }
                var names by remember { mutableStateOf(if (range) values.drop(2) else values) }
                var text by remember { mutableStateOf(seed?.text("value").orEmpty()) }; var flag by remember { mutableStateOf(text == "true") }
                var invalid by remember { mutableStateOf(false) }
                if (range) RangeFields(fieldLabel(name),lo,hi,{ lo=it },{ hi=it })
                if (name in setOf("pHP","pMP","pSP","tHP","tMP","tSP")) Text(stringResource(R.string.jpx_fraction), style=MaterialTheme.typography.bodySmall)
                if (list) {
                    Text(if (names.isEmpty()) stringResource(R.string.jpx_add_value) else names.map { optionLabel(it) }.joinToString(" · "), style=MaterialTheme.typography.bodySmall)
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick={expanded=true}) { Text(stringResource(R.string.jpx_pick_values)) }
                        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}, modifier=Modifier.heightIn(max=320.dp)) {
                            for (v in (JpxFields.conditionOptions(name)+names).distinct()) DropdownMenuItem(text={ Text(optionLabel(v)) },
                                leadingIcon={ Checkbox(checked=v in names,onCheckedChange=null) },onClick={names=if(v in names) names-v else names+v})
                        }
                    }
                }
                if (name == "pSpiritStatus") BoolRow(fieldLabel(name),flag) { flag=it }
                if (name == "tName" || (name !in JpxFields.conditions && raw?.isJsonPrimitive == true && raw.asJsonPrimitive.isString)) OutlinedTextField(text,{text=it},label={Text(stringResource(R.string.jpx_value))})
                val extras = remember { mutableStateMapOf<String,String>() }
                if (name.startsWith("t") || seed?.has("offset") == true || seed?.has("matched") == true) for (field in listOf("offset","matched")) {
                    val pair = parts(seed?.get(field))
                    val default = if (field == "offset") "0" else "1"
                    RangeFields(fieldLabel(field),extras["$field.0"] ?: pair.getOrNull(0) ?: default,
                        extras["$field.1"] ?: pair.getOrNull(1) ?: default,{extras["$field.0"]=it},{extras["$field.1"]=it})
                }
                if (invalid) Text(stringResource(R.string.jpx_invalid), color=MaterialTheme.colorScheme.error)
                // Keep the confirmation button outside the scrollable fields, including with the IME open.
                val submit: () -> Unit = {
                    try {
                        val obj=original?.deepCopy() ?: JsonObject(); obj.addProperty("key",name)
                        if (seed == null) { obj.remove("value"); obj.remove("matched"); obj.remove("offset") }
                        when {
                            range || list -> {
                                val arr=if(range) conditionRange(name,lo,hi) else JsonArray()
                                if(list) names.forEach { arr.add(it) }
                                obj.add("value",arr)
                            }
                            name=="pSpiritStatus" -> obj.addProperty("value",flag)
                            name=="tName" || raw?.isJsonPrimitive == true && raw.asJsonPrimitive.isString -> obj.addProperty("value",text)
                        }
                        for (field in listOf("offset","matched")) if (extras.containsKey("$field.0") || extras.containsKey("$field.1")) {
                            val old=parts(seed?.get(field))
                            val default=if(field=="offset") "0" else "1"
                            val a=extras["$field.0"] ?: old.getOrNull(0) ?: default
                            val b=extras["$field.1"] ?: old.getOrNull(1) ?: default
                            if(a.isBlank() && b.isBlank()) obj.remove(field) else obj.add(field,conditionRange(field,a,b))
                        }
                        onSave(obj)
                    } catch (_: IllegalArgumentException) { invalid=true }
                }
                val latest = rememberUpdatedState(submit)
                DisposableEffect(name) { commit = { latest.value() }; onDispose { } }
            }
        }
    }, confirmButton = { TextButton(onClick = { commit?.invoke() }) { Text(stringResource(R.string.jpx_save_edit)) } }, dismissButton = { TextButton(onClick=onClose) { Text(stringResource(R.string.game_ui_cancel)) } })
}
