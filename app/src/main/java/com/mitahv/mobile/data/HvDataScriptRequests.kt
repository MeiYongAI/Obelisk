package com.mitahv.mobile.data

import com.mitahv.mobile.model.HvSite
import com.mitahv.mobile.model.HvWorld
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Fetch the data dictionaries referenced by this page; their JavaScript is never executed. */
internal object HvDataScriptRequests {
    fun fromPage(html: String, world: HvWorld, site: HvSite, currentUrl: HttpUrl): List<Request> =
        fromPage(Jsoup.parse(html), world, site, currentUrl)

    fun fromPage(document: Document, world: HvWorld, site: HvSite, currentUrl: HttpUrl): List<Request> {
        val referer = world.urlAtSite(currentUrl, site)
        return document.select("script[src]").mapNotNull { script ->
            val source = referer.resolve(script.attr("src")) ?: return@mapNotNull null
            if (source.scheme != "https" || source.port != 443 ||
                source.host !in HvSite.entries.map { it.host } ||
                source.username.isNotEmpty() || source.password.isNotEmpty() ||
                !DATA_SCRIPT.matches(source.encodedPath)) return@mapNotNull null
            source.newBuilder().host(referer.host).fragment(null).build()
        }.distinct().map { target ->
            Request.Builder().url(target).get()
                .header("Referer", referer.toString())
                .header("Accept", "application/javascript,text/javascript")
                .tag(HvRequestScope::class.java, HvRequestScope(world, site, false, target))
                .build()
        }
    }

    private val DATA_SCRIPT = Regex("^/(?:isekai/)?dynjs/(?:[^/]+/)+(?:itemc|equip-[0-9]+)\\.js$")
}
