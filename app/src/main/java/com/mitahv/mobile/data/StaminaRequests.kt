package com.mitahv.mobile.data

import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvStaminaRecovery
import com.mitahv.mobile.model.HvWorld
import com.mitahv.mobile.model.HvSite
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink

internal object StaminaRequests {
    fun build(
        world: HvWorld,
        route: HvRoute,
        recovery: HvStaminaRecovery,
        currentUrl: HttpUrl = route.url(world),
        site: HvSite = HvSite.Main,
    ): Request {
        require(isValidTarget(world, route, currentUrl)) { "Current page does not match the current world and route" }
        require(isValidTarget(world, route, recovery.actionUrl)) { "Stamina form does not match the current world and page" }
        require(hasRecoveryFields(recovery.fields)) { "Stamina form fields are incomplete" }
        val body = FormBody.Builder().apply {
            recovery.fields.forEach { (name, value) -> add(name, value) }
        }.build()
        return Request.Builder()
            .url(world.urlAtSite(recovery.actionUrl, site))
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Referer", world.urlAtSite(currentUrl, site).toString())
            .post(OneShotFormBody(body))
            .build()
    }

    internal fun isValidTarget(world: HvWorld, route: HvRoute, action: HttpUrl): Boolean {
        if (!route.isAvailableIn(world) || !world.containsUrl(action)) return false
        // The supplied Character homepage posts to the world root without query parameters.
        if (action.querySize == 0) return route == HvRoute.Character
        // Subpage forms preserve tree, screen, filter and equipment selection parameters.
        return action.queryParameterValues("s") == listOf(route.section) &&
            action.queryParameterValues("ss") == listOf(route.subsection)
    }

    internal fun hasRecoveryFields(fields: List<Pair<String, String>>): Boolean =
        fields.isNotEmpty() && fields.all { (name, _) -> name.isNotBlank() } &&
            fields.filter { it.first == "recover" } == listOf("recover" to "stamina")

    private class OneShotFormBody(private val body: FormBody) : RequestBody() {
        override fun contentType() = body.contentType()
        override fun contentLength() = body.contentLength()
        override fun isOneShot() = true
        override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
    }
}
