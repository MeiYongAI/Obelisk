package com.mitahv.mobile.model

import okhttp3.HttpUrl

data class HvNavigation(
    val routes: List<HvRoute>,
    val stamina: HvStamina?,
    val messages: List<String> = emptyList(),
)

data class HvStamina(
    val value: String,
    val description: String,
    val recovery: HvStaminaRecovery?,
)

data class HvStaminaRecovery(
    val actionUrl: HttpUrl,
    val fields: List<Pair<String, String>>,
)
