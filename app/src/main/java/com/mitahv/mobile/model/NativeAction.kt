package com.mitahv.mobile.model

import okhttp3.HttpUrl

/** One user-selected action decoded from the current official page. */
data class NativeAction(
    val url: HttpUrl,
    val method: String = "POST",
    val fields: List<Pair<String, String>> = emptyList(),
    val label: String,
    val confirmation: String? = null,
    val confirmationChecks: List<String> = emptyList(),
)
