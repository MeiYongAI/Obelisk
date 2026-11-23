package com.mitahv.mobile.data

import com.mitahv.mobile.model.BattleScene
import com.mitahv.mobile.model.BattleStyle

internal data class JpxEntry(
    val id: Long, val mode: String, val origin: String, val nickname: String,
    val rules: Int, val publishedRules: Int, val changed: Boolean,
)

internal enum class MatchReason { Manual, Assigned, Exact, General, AssignedGeneral }
internal data class StrategyMatch(val entry: JpxEntry, val reason: MatchReason)

internal data class JpxCatalog(
    val entries: List<JpxEntry> = emptyList(),
    val manual: Boolean = false,
    val manualId: Long? = null,
    val routes: Map<String, Long> = emptyMap(),
    val readFailed: Boolean = false,
) {
    fun resolve(style: BattleStyle?, scene: BattleScene?): StrategyMatch? {
        if (manual) return entries.find { it.id == manualId }?.let { StrategyMatch(it, MatchReason.Manual) }
        if (style == null) return null
        fun match(target: BattleScene): StrategyMatch? {
            val key = style.slot(target)
            val assigned = entries.find { it.id == routes[key] && it.publishedRules > 0 }
            val general = target == BattleScene.General
            if (assigned != null) return StrategyMatch(assigned, if (general) MatchReason.AssignedGeneral else MatchReason.Assigned)
            // First imported nonempty variant wins until the user assigns a different one.
            return entries.asSequence().filter { it.mode == key && it.publishedRules > 0 }.minByOrNull { it.id }
                ?.let { StrategyMatch(it, if (general) MatchReason.General else MatchReason.Exact) }
        }
        return scene?.takeUnless { it == BattleScene.General }?.let(::match) ?: match(BattleScene.General)
    }

    fun variant(entry: JpxEntry): String = entry.nickname.ifEmpty {
        if (entries.none { it.id != entry.id && it.mode == entry.mode }) ""
        else if (entries.any { it.id != entry.id && it.mode == entry.mode && it.origin == entry.origin }) "${entry.origin} #${entry.id}"
        else entry.origin
    }
}

internal data class BattleStrategy(
    val catalog: JpxCatalog?, val style: BattleStyle?, val scene: BattleScene?, val inBattle: Boolean,
    val match: StrategyMatch? = null, val published: JpxDraft? = null,
) {
    fun detected(entry: JpxEntry) = when {
        style == null -> false
        scene == null -> entry.mode.substringBeforeLast('_') == style.key
        else -> entry.mode == style.slot(scene)
    }
}
