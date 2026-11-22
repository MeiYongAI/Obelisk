package com.mitahv.mobile.model

/** Public account metadata only; id is the forum member ID, never a cookie value. */
data class AppSettingsAccount(
    val id: String,
    val active: Boolean,
    val usable: Boolean = true,
)

enum class AppThemeMode { System, Light, Dark }
