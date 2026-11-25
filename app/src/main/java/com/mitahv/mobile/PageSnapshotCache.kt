package com.mitahv.mobile

import com.mitahv.mobile.model.HvNavigation
import com.mitahv.mobile.model.HvRoute
import okhttp3.HttpUrl
import okio.ByteString

internal data class PageSnapshot(
    val route: HvRoute,
    val url: HttpUrl,
    val navigation: HvNavigation?,
    val state: RequestState,
    val playerLevel: Int? = null,
    val bodyDigest: ByteString? = null,
)

/** Session-local previews. A complete model can also serve a matching, freshly received response. */
internal class PageSnapshotCache {
    // Keep enough recent subpages to cover normal movement between the many
    // Character and Bazaar screens without retaining parsed battle/riddle data.
    // Entries contain parsed UI models only, not the original HTML response.
    private companion object {
        const val MAX_ENTRIES = 32
    }

    private val pages = object : LinkedHashMap<HttpUrl, PageSnapshot>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<HttpUrl, PageSnapshot>?): Boolean =
            size > MAX_ENTRIES
    }

    operator fun get(url: HttpUrl): PageSnapshot? = pages[url]

    fun put(snapshot: PageSnapshot) {
        when (snapshot.state) {
            is RequestState.CharacterReady,
            is RequestState.ArenaReady,
            is RequestState.CharacterSectionReady,
            is RequestState.BazaarSectionReady,
            is RequestState.BattleModeReady -> pages[snapshot.url] = snapshot
            else -> Unit
        }
    }

    fun clear() = pages.clear()
}
