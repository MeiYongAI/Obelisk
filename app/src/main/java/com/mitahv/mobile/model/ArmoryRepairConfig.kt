package com.mitahv.mobile.model

/** The three repair flags and charm-material rows emitted by armory_repair/armory_update_repair. */
data class ArmoryRepairConfig(
    val needsBaseRepair: Boolean,
    val missingBaseMaterials: Boolean,
    val missingCharmMaterials: Boolean,
    val materials: List<ArmoryRepairMaterial>,
    val replacementField: String? = null,
    val replaceCharmsByDefault: Boolean = true,
) {
    private fun replaceCharms(values: Map<String, String>) = replacementField?.let { name ->
        values[name]?.isNotBlank() ?: replaceCharmsByDefault
    } ?: replaceCharmsByDefault

    fun validationError(values: Map<String, String>): String? {
        val replace = replaceCharms(values)
        val missing = when {
            missingBaseMaterials -> materials.firstOrNull { !it.charmItem && it.owned < it.required }
            replace && missingCharmMaterials -> materials.firstOrNull { it.charmItem && it.owned < it.required }
            else -> null
        }
        if (missing != null) return "${missing.name} 不足（需要 ${missing.required}，持有 ${missing.owned}）"
        if (missingBaseMaterials || replace && missingCharmMaterials) return "修理材料不足"
        if (!needsBaseRepair && !replace) return "装备无需修理，也未选择更换护符和护符袋"
        return null
    }

    fun summary(values: Map<String, String>): List<Pair<String, String>> = materials
        .filter { !it.charmItem || replaceCharms(values) }
        .map { it.name to "${it.required}x · 持有 ${it.owned}" }

    fun confirmation(values: Map<String, String>): String = summary(values)
        .joinToString("\n") { (name, count) -> "$name：$count" }
}

data class ArmoryRepairMaterial(val name: String, val required: Long, val owned: Long, val charmItem: Boolean)
