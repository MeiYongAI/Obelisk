package com.mitahv.mobile.data

import com.mitahv.mobile.model.HvWorld
import com.mitahv.mobile.model.HvSite
import com.mitahv.mobile.model.NativeAction
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink

internal object NativeRequests {
    fun build(world: HvWorld, action: NativeAction, site: HvSite = HvSite.Main): Request {
        val target = world.urlAtSite(action.url, site)
        require(action.method in setOf("GET", "POST")) { "Unsupported action method" }
        val request = Request.Builder().url(target).header("Accept", "text/html,application/xhtml+xml")
            .header("Referer", target.toString())
        return if (action.method == "GET") {
            require(action.fields.isEmpty()) { "GET actions must use URL parameters" }
            request.get().build()
        } else {
            val body = FormBody.Builder().apply { action.fields.forEach { (name, value) -> add(name, value) } }.build()
            request.post(object : RequestBody() {
                override fun contentType() = body.contentType()
                override fun contentLength() = body.contentLength()
                override fun isOneShot() = true
                override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
            }).build()
        }
    }
}
