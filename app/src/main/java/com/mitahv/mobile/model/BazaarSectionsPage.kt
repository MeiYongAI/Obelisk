package com.mitahv.mobile.model

data class BazaarSectionsPage(
    val route: HvRoute,
    val links: List<BazaarSectionsLink> = emptyList(),
    val sections: List<BazaarSectionsGroup> = emptyList(),
    val forms: List<BazaarSectionsForm> = emptyList(),
    val actions: List<NativeAction> = emptyList(),
    val messages: List<String> = emptyList(),
    val marketLinks: BazaarMarketLinks? = null,
    val shopBalance: Long? = null,
    val mail: MailPageInfo? = null,
    val armory: ArmoryPageInfo? = null,
)

data class BazaarSectionsLink(val label: String, val action: NativeAction, val selected: Boolean = false)
data class BazaarMarketLinks(
    val views: List<BazaarSectionsLink> = emptyList(),
    val filters: List<BazaarSectionsLink> = emptyList(),
    val items: List<BazaarSectionsLink> = emptyList(),
    val pages: List<BazaarSectionsLink> = emptyList(),
    val backToList: BazaarSectionsLink? = null,
    val currentItem: String? = null,
)
data class BazaarSectionsGroup(
    val title: String,
    val entries: List<BazaarSectionsEntry> = emptyList(),
    val facts: List<Pair<String, String>> = emptyList(),
    val emptyText: String = "",
    val originalEquipmentName: String? = null,
)
data class BazaarSectionsEntry(
    val key: String,
    val title: String,
    val details: List<Pair<String, String>> = emptyList(),
    val action: NativeAction? = null,
    val quantity: BazaarSectionsQuantity? = null,
    val choices: List<NativeAction> = emptyList(),
    val enabled: Boolean = true,
    val prefill: BazaarSectionsPreset? = null,
    val moreDetails: List<Pair<String, String>> = emptyList(),
    val category: String = "",
    val originalEquipmentName: String? = null,
    val shopItem: BazaarShopItem? = null,
)

data class BazaarShopItem(val id: String, val stock: Long, val owned: Long)

/** Field paths use form-key/field-key; order-book clicks only edit the local draft. */
data class BazaarSectionsPreset(
    val label: String,
    val values: Map<String, String>,
    val minimumValues: Map<String, Long> = emptyMap(),
)

fun BazaarSectionsPreset.applyTo(current: Map<String, String>): Map<String, String> = current + values +
    minimumValues.mapValues { (key, minimum) -> maxOf(current[key]?.toLongOrNull() ?: 0, minimum).toString() }
data class BazaarSectionsQuantity(
    val action: NativeAction,
    val field: String,
    val maximum: Long,
    val unitCost: Long? = null,
    val currency: String = "C",
    val initial: Long = 1,
    val minimum: Long = 1,
)
data class BazaarSectionsForm(
    val key: String,
    val title: String,
    val fields: List<BazaarSectionsField>,
    val actions: List<NativeAction>,
    val facts: List<Pair<String, String>> = emptyList(),
    val attachments: List<BazaarSectionsEntry> = emptyList(),
    val validation: String = "",
    val balance: Long? = null,
    val stock: Long? = null,
    val batchSize: Long = 1,
    val equipmentMode: String = "",
    val singleEquipment: Boolean = false,
    val equipmentCosts: Map<String, BazaarSectionsEquipmentCost> = emptyMap(),
    val materials: Map<String, BazaarSectionsMaterial> = emptyMap(),
    val presets: List<BazaarSectionsPreset> = emptyList(),
    val armoryCharm: ArmoryCharmConfig? = null,
    val armoryStatFusion: ArmoryStatFusionConfig? = null,
    val armoryRepair: ArmoryRepairConfig? = null,
)
data class BazaarSectionsEquipmentCost(val credits: Long? = null, val items: Map<String, Long>? = null)
data class BazaarSectionsMaterial(val name: String, val owned: Long)
data class BazaarSectionsField(
    val name: String,
    val label: String,
    val value: String = "",
    val kind: String = "text",
    val options: List<Pair<String, String>> = emptyList(),
    val required: Boolean = false,
    val maxLength: Int? = null,
    val checked: Boolean = false,
    val minimum: Long? = null,
    val maximum: Long? = null,
    val key: String = name,
    val details: List<Pair<String, String>> = emptyList(),
    val selectionConfirmation: String? = null,
    val originalEquipmentName: String? = null,
)

/** Mirrors the successful controls of the supplied form; submit buttons remain per action. */
fun BazaarSectionsForm.submit(action: NativeAction, values: Map<String, String>): NativeAction {
    val names = fields.map { it.name }.toSet()
    val entered = fields.mapNotNull { field ->
        val value = values[field.key] ?: if (field.kind == "checkbox" && !field.checked) "" else field.value
        if (field.kind == "checkbox" && value.isEmpty()) null else field.name to value
    }
    val summaries = fields.filter { it.kind != "textarea" }.mapNotNull { field ->
        val value = values[field.key] ?: field.value
        if (value.isBlank() || field.kind == "checkbox" && values[field.key].isNullOrBlank()) null
        else if (field.kind == "checkbox") field.label
        else "${field.label}：${field.options.firstOrNull { it.first == value }?.second ?: value}"
    } + (equipmentSummary(values) + orderSummary(values)).map { (label, value) -> "$label：$value" } +
        listOfNotNull(armoryCharm?.confirmation(values), armoryStatFusion?.confirmation(values), armoryRepair?.confirmation(values))
    val submitted = action.fields.filterNot { it.first in names } + entered
    return if (action.method == "GET") {
        val query = action.url.newBuilder().query(null)
        submitted.forEach { (name, value) -> query.addQueryParameter(name, value) }
        action.copy(url = query.build(), fields = emptyList())
    } else action.copy(fields = submitted,
        confirmation = action.confirmation?.let {
            if (validation.startsWith("market_") && action.fields.any { field -> field.first in setOf("buyorder_cancel", "sellorder_cancel") }) it
            else (listOf(it) + summaries).joinToString("\n")
        })
}

fun BazaarSectionsForm.validationError(action: NativeAction, values: Map<String, String>): String? {
    // Delete is an independent submit control; the original page does not validate the order draft.
    if (validation.startsWith("market_") && action.fields.any { it.first in setOf("buyorder_cancel", "sellorder_cancel") }) return null
    armoryCharm?.validationError(values)?.let { return it }
    armoryStatFusion?.validationError(values)?.let { return it }
    armoryRepair?.validationError(values)?.let { return it }
    fun value(field: BazaarSectionsField) = values[field.key] ?: field.value
    if (validation == "equipment" && fields.none { it.name == "eqids[]" && !values[it.key].isNullOrBlank() }) return "请选择装备"
    if (validation == "equipment") {
        val selected = selectedEquipment(values)
        if (singleEquipment && selected.size > 1) return "此操作只能选择一件装备"
        if (equipmentMode in setOf("purchase", "sell", "repair", "soulbind", "salvage")) {
            if (selected.any { it !in equipmentCosts }) return "未取得所选装备的费用，请重新加载"
            if (equipmentMode in setOf("purchase", "sell") && selected.any { equipmentCosts[it]?.credits == null }) return "未取得装备价格，请重新加载"
            if (equipmentMode == "purchase") {
                val total = selected.sumOf { equipmentCosts[it]?.credits ?: 0 }
                if (balance != null && total > balance) return "Credits 不足"
            }
            if (equipmentMode in setOf("repair", "soulbind")) {
                if (selected.any { equipmentCosts[it]?.items == null }) return "未取得所需材料，请重新加载"
                for ((id, needed) in equipmentMaterialTotals(values)) {
                    val material = materials[id] ?: return "未取得材料库存，请重新加载"
                    if (needed > material.owned) return "${material.name} 不足（需要 $needed，持有 ${material.owned}）"
                }
            }
        }
    }
    for (field in fields) {
        val v = value(field)
        if (field.required && v.isBlank()) return "请填写${field.label}"
        if (field.maxLength != null && v.length > field.maxLength) return "${field.label}最多 ${field.maxLength} 字"
        if (field.kind == "number") {
            val n = v.toLongOrNull() ?: return "${field.label}请输入整数"
            if (field.minimum != null && n < field.minimum) return "${field.label}不能小于 ${field.minimum}"
            if (field.maximum != null && n > field.maximum) return "${field.label}不能大于 ${field.maximum}"
        }
    }
    if (validation.startsWith("market_")) {
        val prefix = if (validation == "market_buy") "buyorder" else "sellorder"
        val count = values["${prefix}_batchcount"]?.toLongOrNull() ?: 0
        val price = values["${prefix}_batchprice"]?.toLongOrNull() ?: 0
        if (count < 1) return "批数必须大于 0"
        if (price < 10) return "每批价格最低为 10 C"
        if (price > 100_000_000) return "每批价格最高为 100,000,000 C"
        var scaled = price
        var step = 1L
        while (scaled >= 1000) { scaled /= 10; step *= 10 }
        if (scaled >= 500) step *= 5 else if (scaled >= 200) step *= 2
        if (price % step != 0L) return "此价格区间须以 $step C 为步长"
        if (validation == "market_buy" && balance != null && count > balance / price) return "Market Balance 不足"
        if (validation == "market_sell" && stock != null && count > stock / batchSize.coerceAtLeast(1)) return "库存不足"
    }
    if (validation == "transfer") {
        val amount = values["account_amount"]?.toLongOrNull() ?: 0
        if (amount < 1) return "金额必须大于 0"
        val limit = if (action.fields.any { it.first == "account_deposit" }) balance else stock
        if (limit != null && amount > limit) return "余额不足"
    }
    if (validation == "mail" && action.fields.any { it.first == "action" && it.second == "send" } && values["message_to_name"].isNullOrBlank()) return "请填写收件人"
    return null
}

private fun BazaarSectionsForm.selectedEquipment(values: Map<String, String>): List<String> = fields
    .filter { it.name == "eqids[]" && !values[it.key].isNullOrBlank() }.map { it.value }

private fun BazaarSectionsForm.equipmentMaterialTotals(values: Map<String, String>): Map<String, Long> {
    val replaceCharms = fields.firstOrNull { it.name == "replace_charms" }?.let { !values[it.key].isNullOrBlank() } ?: true
    val totals = mutableMapOf<String, Long>()
    selectedEquipment(values).forEach { id -> equipmentCosts[id]?.items?.forEach { (item, count) ->
        if (equipmentMode != "repair" || replaceCharms || item.toIntOrNull()?.let { it in 61900..64999 } != true) {
            totals[item] = (totals[item] ?: 0) + count
        }
    } }
    return totals
}

fun BazaarSectionsForm.equipmentSummary(values: Map<String, String>): List<Pair<String, String>> {
    val selected = selectedEquipment(values)
    if (selected.isEmpty() || equipmentMode.isBlank()) return emptyList()
    val lines = mutableListOf<Pair<String, String>>()
    val sellSalvage = fields.firstOrNull { it.name == "sell_salvage" }?.let { !values[it.key].isNullOrBlank() } ?: false
    if (equipmentMode in setOf("purchase", "sell") || equipmentMode == "salvage" && sellSalvage) {
        if (selected.all { equipmentCosts[it]?.credits != null }) lines += (if (equipmentMode == "purchase") "总价" else "获得 Credits") to "${selected.sumOf { equipmentCosts[it]?.credits ?: 0 }} C"
    }
    if (equipmentMode in setOf("repair", "soulbind", "salvage")) equipmentMaterialTotals(values).forEach { (id, count) ->
        materials[id]?.let { lines += (if (equipmentMode == "salvage") "获得 ${it.name}" else it.name) to "$count（持有 ${it.owned}）" }
    }
    return lines
}

fun BazaarSectionsForm.orderSummary(values: Map<String, String>): List<Pair<String, String>> {
    if (!validation.startsWith("market_")) return emptyList()
    val prefix = if (validation == "market_buy") "buyorder" else "sellorder"
    val count = values["${prefix}_batchcount"]?.toLongOrNull() ?: return emptyList()
    val price = values["${prefix}_batchprice"]?.toLongOrNull() ?: return emptyList()
    if (count < 1 || price < 1) return emptyList()
    val total = runCatching { Math.multiplyExact(count, price) }.getOrNull() ?: return emptyList()
    val units = runCatching { Math.multiplyExact(count, batchSize) }.getOrNull() ?: return emptyList()
    return listOf("数量" to units.toString(), "订单总额" to "$total C")
}

fun BazaarSectionsQuantity.submit(count: Long): NativeAction {
    require(count in minimum..maximum)
    val cost = unitCost?.let { Math.multiplyExact(count, it) }
    return action.copy(
        fields = action.fields.filterNot { it.first == field } + (field to count.toString()),
        confirmation = listOfNotNull(action.confirmation, "数量：$count", cost?.let { "合计：$it $currency" }).joinToString("\n"),
    )
}
