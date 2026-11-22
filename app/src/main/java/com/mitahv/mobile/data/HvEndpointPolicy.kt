package com.mitahv.mobile.data

import com.mitahv.mobile.model.HvSite
import com.mitahv.mobile.model.HvWorld
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.IOException

/** A request keeps its world and selected origin through redirects. */
internal data class HvRequestScope(
    val world: HvWorld,
    val site: HvSite,
    val allowJson: Boolean,
    val resourceUrl: HttpUrl? = null,
) {
    fun accepts(url: HttpUrl): Boolean = if (resourceUrl != null) url == resourceUrl
        else world.containsUrl(url, allowJson) && url.host == world.baseUrl(site).host
}

internal object HvEndpointPolicy {
    fun prepare(request: Request, world: HvWorld, site: HvSite): Request {
        val target = world.urlAtSite(request.url, site, allowJson = true)
        val scope = HvRequestScope(world, site, target.encodedPath.endsWith("/json"))
        return request.newBuilder().url(target).tag(HvRequestScope::class.java, scope).apply {
            request.header("Referer")?.let { value ->
                val referer = requireNotNull(value.toHttpUrlOrNull()) { "Invalid Referer" }
                header("Referer", world.urlAtSite(referer, site).toString())
            }
        }.build()
    }

    fun check(request: Request) {
        val scope = request.tag(HvRequestScope::class.java) ?: throw IOException("Missing HentaiVerse request world")
        if (!scope.accepts(request.url)) throw IOException("Request redirected outside the selected world or site")
    }
}
