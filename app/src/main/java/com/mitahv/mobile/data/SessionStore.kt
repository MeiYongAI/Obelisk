package com.mitahv.mobile.data

import java.io.IOException
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject

/** The device holds one verified HV session. Only an explicit login establishes its identity. */
internal class SessionStore(initial: String?, private val write: (String?) -> Boolean) {
    private var cookies = initial?.let {
        runCatching { SessionCookies.decode(JSONArray(it), System.currentTimeMillis()) }.getOrNull()
    }.orEmpty().filter { it.isHvCookie() }

    @Synchronized
    fun memberId(): String? = auth(cookies)?.memberId

    @Synchronized
    fun loadForRequest(url: HttpUrl): List<Cookie> =
        if (!url.isHvUrl()) emptyList() else cookies.filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }

    @Synchronized
    fun replaceHvSession(auth: HvAuthCookies, verified: List<Cookie>) {
        val session = verified.filter { it.isHvCookie() && it.name !in HvAuthCookies.names && it.expiresAt > System.currentTimeMillis() }
        if (!persist(session + auth.forDomain("hentaiverse.org"))) throw IOException("无法保存登录会话，请重试。")
    }

    @Synchronized
    fun logout(): Boolean = persist(emptyList())

    @Synchronized
    fun saveFromResponse(url: HttpUrl, incoming: List<Cookie>) {
        if (!url.isHvUrl()) return
        val id = memberId() ?: return // Ignore responses after logout.
        val now = System.currentTimeMillis()
        val merged = cookies.filter { it.expiresAt > now }.associateByTo(linkedMapOf()) { it.key() }
        val foreignId = incoming.any { it.name == "ipb_member_id" && it.expiresAt > now && it.value != id }
        for (cookie in incoming) {
            if (!cookie.isHvCookie() || (foreignId && cookie.name in HvAuthCookies.names)) continue
            if (cookie.expiresAt <= now) merged.remove(cookie.key()) else merged[cookie.key()] = cookie
        }
        val next = merged.values.toList().takeIf { auth(it)?.memberId == id }.orEmpty()
        if (next != cookies && !persist(next)) throw IOException("无法保存登录会话，请重试。")
    }

    private fun persist(next: List<Cookie>): Boolean {
        if (!write(if (next.isEmpty()) null else SessionCookies.encode(next).toString())) return false
        cookies = next
        return true
    }

    private fun auth(cookies: List<Cookie>) = HvAuthCookies.fromCookies(cookies.filter { it.matches(HV_URL) })
    private fun HttpUrl.isHvUrl() = scheme == "https" && (host == "hentaiverse.org" || host.endsWith(".hentaiverse.org"))
    private fun Cookie.isHvCookie() = domain == "hentaiverse.org" || domain.endsWith(".hentaiverse.org")
    private fun Cookie.key() = "$domain|$path|$name"
    private companion object { val HV_URL = "https://hentaiverse.org/".toHttpUrl() }
}

internal object SessionCookies {
    fun encode(cookies: List<Cookie>): JSONArray = JSONArray().apply { cookies.forEach { cookie ->
        put(JSONObject().put("name", cookie.name).put("value", cookie.value).put("domain", cookie.domain)
            .put("path", cookie.path).put("expiresAt", cookie.expiresAt).put("persistent", cookie.persistent)
            .put("hostOnly", cookie.hostOnly).put("secure", cookie.secure).put("httpOnly", cookie.httpOnly))
    } }

    fun decode(array: JSONArray, now: Long): List<Cookie> = buildList {
        repeat(array.length()) { index ->
            val item = array.optJSONObject(index) ?: return@repeat
            val cookie = runCatching {
                val persistent = item.optBoolean("persistent")
                val expiresAt = item.optLong("expiresAt", Long.MAX_VALUE)
                if (persistent && expiresAt <= now) return@runCatching null
                val builder = Cookie.Builder().name(item.getString("name")).value(item.getString("value"))
                    .path(item.optString("path", "/"))
                val domain = item.getString("domain")
                if (item.optBoolean("hostOnly")) builder.hostOnlyDomain(domain) else builder.domain(domain)
                if (persistent) builder.expiresAt(expiresAt)
                if (item.optBoolean("secure")) builder.secure()
                if (item.optBoolean("httpOnly")) builder.httpOnly()
                builder.build()
            }.getOrNull()
            if (cookie != null) add(cookie)
        }
    }
}
