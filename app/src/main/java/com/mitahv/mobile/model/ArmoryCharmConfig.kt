package com.mitahv.mobile.model

/** Values and replacement rules exposed by the supplied Charm Slot page and hveqc.js. */
data class ArmoryCharmConfig(
    val slot: Int,
    val maximumPoints: Int,
    val otherPoints: Int,
    val currentCharm: String,
    val currentPouch: String,
    val charmWorn: Boolean,
    val charmBroken: Boolean,
    val pouchBroken: Boolean,
    val charms: List<ArmoryCharmOption>,
    val pouches: List<ArmoryCharmOption>,
) {
    private fun charm(values: Map<String, String>) = charms.firstOrNull { it.id == (values["charmtype"] ?: currentCharm) }
    private fun pouch(values: Map<String, String>) = pouches.firstOrNull { it.id == (values["pouchtype"] ?: currentPouch) }
    private fun replaceCharm(charm: ArmoryCharmOption) = charmWorn || charmBroken || currentCharm != charm.id
    private fun replacePouch(pouch: ArmoryCharmOption) =
        (!charmBroken || pouch.id != "0") && (pouchBroken || currentPouch != pouch.id)

    fun validationError(values: Map<String, String>): String? {
        val charm = charm(values) ?: return "请选择护符"
        val pouch = pouch(values) ?: return "请选择护符袋"
        if (charm.id != "0") {
            charm.disabledReason?.let { return it }
            pouch.disabledReason?.let { return it }
            if (otherPoints + charm.points + pouch.points > maximumPoints) return "Charm Points 不足"
            if (replaceCharm(charm) && charm.materials == null || replacePouch(pouch) && pouch.materials == null) {
                return "未取得所需材料，请重新加载"
            }
        }
        val changed = charmWorn || charmBroken || pouchBroken || currentCharm != charm.id ||
            (charm.id != "0" && currentPouch != pouch.id)
        return if (changed) null else "未更改护符或护符袋"
    }

    fun summary(values: Map<String, String>): List<Pair<String, String>> {
        val charm = charm(values) ?: return emptyList()
        val pouch = pouch(values) ?: return emptyList()
        val materials = if (charm.id == "0") emptyList() else
            (if (replaceCharm(charm)) charm.materials.orEmpty() else emptyList()) +
                (if (replacePouch(pouch)) pouch.materials.orEmpty() else emptyList())
        return listOf("Charm Points" to "${otherPoints + charm.points + pouch.points} / $maximumPoints") +
            materials.map { (name, count) -> name to "${count}x" }
    }

    fun details(values: Map<String, String>): List<Pair<String, String>> = charm(values)?.details.orEmpty()

    fun label(values: Map<String, String>): String {
        val charm = charm(values) ?: return "Attach Charm"
        val pouch = pouch(values) ?: return "Attach Charm"
        return when {
            charm.id == "0" && currentCharm != "0" -> "Destroy Charm"
            charm.id != "0" && charm.id == currentCharm -> when {
                charmWorn || charmBroken -> "Replace Charm"
                currentPouch != pouch.id -> "Replace Pouch"
                pouchBroken -> "Repair Pouch"
                else -> "Attach Charm"
            }
            else -> "Attach Charm"
        }
    }

    fun confirmation(values: Map<String, String>): String {
        val charm = charm(values) ?: return ""
        val pouch = pouch(values) ?: return ""
        val lines = mutableListOf("${label(values)} · Charm Slot $slot")
        if (charm.id == "0") lines += "此槽位现有的护符和护符袋将被销毁。"
        else {
            lines += "${charm.name} · ${pouch.name}"
            lines += summary(values).map { (name, value) -> "$name：$value" }
            if (currentCharm != "0" && !charmWorn && !charmBroken && currentCharm != charm.id) lines += "现有护符将被销毁。"
            if (!pouchBroken && currentPouch != pouch.id && currentCharm != "0") lines += "现有护符袋将被销毁。"
        }
        return lines.joinToString("\n")
    }
}

data class ArmoryCharmOption(
    val id: String,
    val name: String,
    val points: Int,
    val disabledReason: String? = null,
    val materials: List<Pair<String, Long>>? = null,
    val details: List<Pair<String, String>> = emptyList(),
)
