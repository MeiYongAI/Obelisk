package com.mitahv.mobile.data

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Only the shared E-Hentai account credentials are bridged to HV. */
internal class HvAuthCookies private constructor(
    val memberId: String,
    private val passHash: String,
) {
    fun forDomain(domain: String): List<Cookie> = listOf(
        cookie("ipb_member_id", memberId, domain),
        cookie("ipb_pass_hash", passHash, domain),
    )

    override fun toString() = "HvAuthCookies(redacted)"

    private fun cookie(name: String, value: String, domain: String) = Cookie.Builder()
        .name(name).value(value).domain(domain).path("/").secure().httpOnly().build()

    companion object {
        val names = setOf("ipb_member_id", "ipb_pass_hash")

        fun fromHeader(header: String): HvAuthCookies? {
            val normalized = header.trim().let {
                if (it.startsWith("Cookie:", ignoreCase = true)) it.substringAfter(':') else it
            }
            val pairs = normalized.split(';').mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) null else part.substring(0, index).trim() to part.substring(index + 1).trim()
            }.filter { it.first in names }
            if (pairs.groupBy { it.first }.any { (_, values) -> values.map { it.second }.distinct().size > 1 }) return null
            return fromValues(pairs.toMap())
        }

        fun fromCookies(cookies: List<Cookie>): HvAuthCookies? {
            val candidates = cookies.filter { it.expiresAt > System.currentTimeMillis() && it.name in names }
            if (candidates.groupBy { it.name }.any { (_, values) -> values.map { it.value }.distinct().size > 1 }) return null
            return fromValues(candidates.associate { it.name to it.value })
        }

        private fun fromValues(values: Map<String, String>): HvAuthCookies? {
            val id = values["ipb_member_id"] ?: return null
            val hash = values["ipb_pass_hash"] ?: return null
            if (id.toLongOrNull()?.let { it > 0 } != true || hash.isBlank() || hash == "0" ||
                hash.any { it.code !in 0x21..0x7e }) return null
            return runCatching {
                HvAuthCookies(id, hash).also { it.forDomain("hentaiverse.org") }
            }.getOrNull()
        }
    }
}

/** A failed sign-in must never replace an existing persistent session. */
internal class LoginCookieJar : CookieJar {
    private val cookies = linkedMapOf<String, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            val key = "${cookie.domain}|${cookie.path}|${cookie.name}"
            if (cookie.expiresAt <= System.currentTimeMillis()) this.cookies.remove(key)
            else this.cookies[key] = cookie
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.values.filter {
        it.expiresAt > System.currentTimeMillis() && it.matches(url)
    }
}
