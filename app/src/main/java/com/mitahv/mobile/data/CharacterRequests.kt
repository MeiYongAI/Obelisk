package com.mitahv.mobile.data

import com.mitahv.mobile.model.HvWorld
import com.mitahv.mobile.model.HvSite
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink

internal object CharacterRequests {
    fun persona(
        world: HvWorld,
        personaId: String,
        rename: String? = null,
        create: String? = null,
        site: HvSite = HvSite.Main,
    ): Request {
        require(personaId.toLongOrNull()?.let { it > 0 } == true) { "Invalid persona ID" }
        require(rename == null || rename.isNotBlank()) { "Persona name is empty" }
        require(create == null || create in setOf("clone", "blank")) { "Invalid persona creation mode" }
        require(rename == null || create == null) { "Choose one persona operation" }

        return formRequest(
            world,
            FormBody.Builder()
                .add("persona_set", personaId)
                .add("persona_rename", rename.orEmpty())
                .add("persona_create", create.orEmpty())
                .build(),
            site,
        )
    }

    fun attributes(world: HvWorld, deltas: Map<String, Int>, site: HvSite = HvSite.Main): Request {
        require(deltas.keys.all { it in ATTRIBUTE_KEYS }) { "Unknown attribute" }
        return formRequest(
            world,
            FormBody.Builder()
                .add("attr_apply", "1")
                .apply {
                    ATTRIBUTE_KEYS.forEach { key -> add("${key}_delta", (deltas[key] ?: 0).toString()) }
                }
                .build(),
            site,
        )
    }

    private fun formRequest(world: HvWorld, body: FormBody, site: HvSite): Request = Request.Builder()
        .url(world.baseUrl(site))
        .header("Accept", "text/html,application/xhtml+xml")
        .header("Referer", world.baseUrl(site).toString())
        .post(OneShotFormBody(body))
        .build()

    // These forms modify game state. A retry must follow a fresh server read, not a replayed POST.
    private class OneShotFormBody(private val body: FormBody) : RequestBody() {
        override fun contentType() = body.contentType()
        override fun contentLength() = body.contentLength()
        override fun isOneShot() = true
        override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
    }

    private val ATTRIBUTE_KEYS = listOf("str", "dex", "agi", "end", "int", "wis")
}
