package com.mitahv.mobile.model

import okhttp3.HttpUrl

enum class ArmoryViewMode { List, Detail, Fusion }

data class ArmoryPageInfo(
    val mode: ArmoryViewMode,
    val currentUrl: HttpUrl,
    val screen: String,
    val filter: String,
    val operations: List<BazaarSectionsLink> = emptyList(),
    val filters: List<BazaarSectionsLink> = emptyList(),
    val backAction: NativeAction? = null,
    val summary: List<Pair<String, String>> = emptyList(),
    val balance: Long? = null,
)
