package com.mitahv.mobile.model

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

enum class HvSite(val label: String, val host: String) {
    Main("主站", "hentaiverse.org"),
    Alt("备用站", "alt.hentaiverse.org"),
}

enum class HvWorld(val baseUrl: HttpUrl, val label: String) {
    Persistent("https://hentaiverse.org/".toHttpUrl(), "Persistent"),
    Isekai("https://alt.hentaiverse.org/isekai/".toHttpUrl(), "Isekai");

    fun baseUrl(site: HvSite): HttpUrl = when (this) {
        Persistent -> baseUrl.newBuilder().host(site.host).build()
        Isekai -> baseUrl
    }

    /** Persistent has two official origins; Isekai is a distinct path on alt. */
    fun containsUrl(url: HttpUrl, allowJson: Boolean = false): Boolean =
        url.scheme == "https" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
            url.fragment == null && when (this) {
                Persistent -> url.host in HvSite.entries.map { it.host }
                Isekai -> url.host == HvSite.Alt.host
            } && (url.encodedPath == baseUrl.encodedPath ||
                allowJson && url.encodedPath == "${baseUrl.encodedPath}json")

    /** Change only an official origin, preserving the world's path and the full query. */
    fun urlAtSite(url: HttpUrl, site: HvSite, allowJson: Boolean = false): HttpUrl {
        require(containsUrl(url, allowJson)) { "URL is outside the selected world" }
        return url.newBuilder().host(baseUrl(site).host).build()
    }
}

enum class HvSection(val label: String) {
    Character("Character"), Bazaar("Bazaar"), Battle("Battle");

    val defaultRoute: HvRoute
        get() = when (this) {
            Character -> HvRoute.Character
            Bazaar -> HvRoute.Bazaar
            Battle -> HvRoute.Battle
        }
}

enum class HvRoute(
    val category: HvSection,
    val subsection: String,
    val label: String,
) {
    Character(HvSection.Character, "ch", "Character"),
    Equipment(HvSection.Character, "eq", "Equipment"),
    Abilities(HvSection.Character, "ab", "Abilities"),
    Training(HvSection.Character, "tr", "Training"),
    ItemInventory(HvSection.Character, "it", "Item Inventory"),
    Settings(HvSection.Character, "se", "Settings"),
    Bazaar(HvSection.Bazaar, "is", "Item Shop"),
    Shrine(HvSection.Bazaar, "ss", "The Shrine"),
    Market(HvSection.Bazaar, "mk", "The Market"),
    Armory(HvSection.Bazaar, "am", "The Armory"),
    MonsterLab(HvSection.Bazaar, "ml", "Monster Lab"),
    MoogleMail(HvSection.Bazaar, "mm", "MoogleMail"),
    WeaponLottery(HvSection.Bazaar, "lt", "Weapon Lottery"),
    ArmorLottery(HvSection.Bazaar, "la", "Armor Lottery"),
    Battle(HvSection.Battle, "ar", "The Arena"),
    Tower(HvSection.Battle, "tw", "The Tower"),
    RingOfBlood(HvSection.Battle, "rb", "Ring of Blood"),
    GrindFest(HvSection.Battle, "gr", "GrindFest"),
    ItemWorld(HvSection.Battle, "iw", "Item World");

    val section: String get() = category.name

    fun isAvailableIn(world: HvWorld): Boolean = when (this) {
        Training, MonsterLab, WeaponLottery, ArmorLottery -> world == HvWorld.Persistent
        Tower -> world == HvWorld.Isekai
        else -> true
    }

    fun url(world: HvWorld, site: HvSite = HvSite.Main): HttpUrl = world.baseUrl(site).newBuilder()
        .addQueryParameter("s", section)
        .addQueryParameter("ss", subsection)
        .build()

    companion object {
        /** Only routes evidenced by the supplied official menus are accepted. */
        fun fromUrl(value: String, world: HvWorld): HvRoute? {
            val url = world.baseUrl.resolve(value) ?: return null
            if (!world.containsUrl(url) ||
                url.queryParameterNames != setOf("s", "ss") ||
                url.queryParameterValues("s").size != 1 || url.queryParameterValues("ss").size != 1) return null
            return entries.firstOrNull {
                it.section == url.queryParameter("s") && it.subsection == url.queryParameter("ss") &&
                    it.isAvailableIn(world)
            }
        }

        fun pages(section: HvSection, world: HvWorld): List<HvRoute> =
            entries.filter { it.category == section && it.isAvailableIn(world) }
    }
}
