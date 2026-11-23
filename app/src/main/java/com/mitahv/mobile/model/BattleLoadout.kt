package com.mitahv.mobile.model

/** One equipment overview, never a mix of hands and bonuses from different loadouts. */
data class BattleLoadout(
    val set: String?,
    val main: String?, // null = not identified; empty string = explicitly empty slot
    val off: String?,
    val bonuses: Map<SpellSchool, Double>,
) : java.io.Serializable {
    val family: WeaponFamily? get() = when {
        main == null || off == null -> null
        main.isEmpty() && off.isEmpty() -> WeaponFamily.Unarmed
        katana.containsMatchIn(main) && wakizashi.containsMatchIn(off) -> WeaponFamily.Niten
        one.containsMatchIn(main) && one.containsMatchIn(off) -> WeaponFamily.Dual
        one.containsMatchIn(main) && (off.isEmpty() || shield.containsMatchIn(off)) -> WeaponFamily.One
        off.isEmpty() && two.containsMatchIn(main) -> WeaponFamily.Two
        off.isEmpty() && staff.containsMatchIn(main) -> WeaponFamily.Staff
        else -> null
    }
    val maximum: Double? get() = bonuses.values.maxOrNull()?.takeIf { bonuses.size == SpellSchool.entries.size }
    val school: SpellSchool? get() = maximum?.takeIf { it > 0 }?.let { max -> bonuses.entries.singleOrNull { it.value == max }?.key }
    val isBattlemage: Boolean? get() = family?.threshold?.let { limit -> maximum?.let { it > limit } }

    private companion object {
        fun types(names: String) = Regex("\\b(?:$names)\\b", RegexOption.IGNORE_CASE)
        val katana = types("Katana")
        val wakizashi = types("Wakizashi")
        val one = types("Axe|Club|Rapier|Shortsword|Wakizashi|Dagger")
        val two = types("Longsword|Estoc|Mace|Katana|Scythe|Swordchucks")
        val staff = types("Staff")
        val shield = types("Buckler|Kite Shield|Tower Shield|Force Shield")
    }
}

enum class WeaponFamily(val threshold: Int? = null) { One(70), Two(100), Dual(100), Niten(100), Staff, Unarmed }
enum class SpellSchool { Fire, Cold, Elec, Wind, Holy, Dark }
