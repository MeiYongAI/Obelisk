package com.mitahv.mobile.model

/** Server-provided fusion rolls and costs; no stat or price formula is inferred locally. */
data class ArmoryStatFusionConfig(
    val targetName: String,
    val balance: Long?,
    val candidates: List<ArmoryStatFusionCandidate>,
) {
    private fun selected(values: Map<String, String>) = candidates.filter {
        values["eqids[]:${it.id}"] == it.id
    }

    fun validationError(values: Map<String, String>): String? {
        val chosen = selected(values)
        if (chosen.isEmpty()) return "请选择要牺牲的装备"
        if (chosen.size != 1) return "每次只能牺牲一件装备"
        val candidate = chosen.single()
        if (balance == null) return "未取得 Credits 余额，请重新加载"
        if (candidate.credits > balance) return "Credits 不足（需要 ${candidate.credits} C，持有 $balance C）"
        for (stat in candidate.stats) {
            val material = candidate.materials.firstOrNull { it.id == stat.bindingId }
                ?: return "未取得 ${stat.label} 的融合材料，请重新加载"
            if (material.owned < material.required) {
                return "${material.name} 不足（需要 ${material.required}，持有 ${material.owned}）"
            }
        }
        return null
    }

    fun summary(values: Map<String, String>): List<Pair<String, String>> {
        val candidate = selected(values).singleOrNull() ?: return emptyList()
        return listOf("融合至" to targetName, "Base Stat Rolls" to "+${candidate.stats.sumOf { it.boost }}") +
            candidate.stats.map { it.label to "${it.value} → ${it.value + it.boost} (+${it.boost})" } +
            candidate.materials.map { it.name to "${it.required}x · 持有 ${it.owned}" } +
            listOf("Credits" to "${candidate.credits} C · 持有 ${balance ?: "—"}")
    }

    fun confirmation(values: Map<String, String>): String {
        val candidate = selected(values).singleOrNull() ?: return ""
        return (listOf("牺牲装备：${candidate.name}", "该装备将永久销毁，无法恢复。") +
            summary(values).map { (label, value) -> "$label：$value" }).joinToString("\n")
    }
}

data class ArmoryStatFusionCandidate(
    val id: String,
    val name: String,
    val credits: Long,
    val stats: List<ArmoryStatFusionStat>,
    val materials: List<ArmoryStatFusionMaterial>,
)

data class ArmoryStatFusionStat(val label: String, val value: Int, val boost: Int, val bindingId: String)
data class ArmoryStatFusionMaterial(val id: String, val name: String, val required: Long, val owned: Long)
