package com.mitahv.mobile.data

import com.mitahv.mobile.model.HvSite
import com.mitahv.mobile.model.HvWorld
import okhttp3.HttpUrl
import okhttp3.Request

/** Loads the exact image referenced by the server page with the selected site's session. */
internal object HvImageRequests {
    fun build(world: HvWorld, site: HvSite, currentUrl: HttpUrl, imageUrl: HttpUrl): Request {
        require(imageUrl.scheme == "https" && imageUrl.port == 443 &&
            imageUrl.host in HvSite.entries.map { it.host } && imageUrl.username.isEmpty() && imageUrl.password.isEmpty())
        val referer = world.urlAtSite(currentUrl, site)
        val target = imageUrl.newBuilder().host(referer.host).fragment(null).build()
        return Request.Builder().url(target).get().header("Referer", referer.toString())
            .header("Accept", "image/*")
            .tag(HvRequestScope::class.java, HvRequestScope(world, site, false, target)).build()
    }
}
