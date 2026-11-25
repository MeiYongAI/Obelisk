package com.mitahv.mobile.data

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Separate dispatcher, no game cookies, and no redirects for GitHub's device flow/API. */
internal class StoreTransport {
    private companion object {
        // Every store WebView can reuse established connections. Requests still have separate headers and cancellation.
        val client by lazy {
            OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(false).callTimeout(25, TimeUnit.SECONDS).build()
        }
    }

    fun call(args: JSONObject): Call {
        val url = args.getString("url").toHttpUrl()
        val method = args.getString("method").uppercase()
        require(url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty())
        val repo = "/repos/MeiYongAI/JPX-PLUS-Configs"
        val path = url.encodedPath
        require(when (url.host) {
            "github.com" -> method == "POST" && path in setOf("/login/device/code", "/login/oauth/access_token")
            "api.github.com" -> when {
                path == "/user" || path == "$repo/contents/catalog/index.json" -> method == "GET"
                path == "$repo/issues" || path.startsWith("$repo/issues/") -> method in setOf("GET","POST","PATCH","DELETE")
                else -> false
            }
            "raw.githubusercontent.com" -> method == "GET" && path.startsWith("/MeiYongAI/JPX-PLUS-Configs/main/catalog/")
            "jpx-plus-downloads.lernsc.workers.dev" -> path == "/v1/downloads" && method in setOf("GET","POST")
            else -> false
        })
        val request = Request.Builder().url(url)
        val headers = args.optJSONObject("headers") ?: JSONObject()
        for (name in headers.keys()) {
            require(name.lowercase() in setOf("accept", "x-github-api-version", "cache-control", "content-type", "authorization"))
            require(!name.equals("Authorization", true) || url.host == "api.github.com")
            request.header(name, headers.getString(name))
        }
        val body = if (args.isNull("body")) null else args.getString("body")
            .also { require(it.toByteArray().size <= 128_000) }.toRequestBody("application/json".toMediaType())
        return client.newCall(request.method(method, body).build())
    }
}
