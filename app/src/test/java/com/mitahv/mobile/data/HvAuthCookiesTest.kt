package com.mitahv.mobile.data

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class HvAuthCookiesTest {
    @Test fun requiresBothAccountCookies() {
        assertNull(HvAuthCookies.fromHeader("session=anything"))
        assertNull(HvAuthCookies.fromHeader("ipb_member_id=123"))
        assertNull(HvAuthCookies.fromHeader("ipb_member_id=0; ipb_pass_hash=test-hash"))
        assertNull(HvAuthCookies.fromHeader("ipb_member_id=123; ipb_pass_hash=0"))
    }

    @Test fun acceptsHeaderPrefixAndBridgesOnlyAccountCookiesToBothWorlds() {
        val auth = requireNotNull(HvAuthCookies.fromHeader(
            "Cookie: cf_clearance=not-shared; ipb_member_id=123; ipb_pass_hash=test-hash; Path=/",
        ))
        val cookies = auth.forDomain("hentaiverse.org")
        assertEquals(HvAuthCookies.names, cookies.map { it.name }.toSet())
        listOf("https://hentaiverse.org/", "https://alt.hentaiverse.org/isekai/").forEach { url ->
            assertTrue(cookies.all { it.matches(url.toHttpUrl()) })
        }
        assertTrue(cookies.none { it.matches("https://example.org/".toHttpUrl()) })
        assertFalse(auth.toString().contains("test-hash"))
    }

    @Test fun rejectsConflictingAccountsAndInvalidCookieCharacters() {
        assertNull(HvAuthCookies.fromHeader("ipb_member_id=1; ipb_member_id=2; ipb_pass_hash=test-hash"))
        assertNull(HvAuthCookies.fromHeader("ipb_member_id=1; ipb_pass_hash=a\nb"))
    }

    @Test fun temporaryJarHonorsScopeAndDeletion() {
        val jar = LoginCookieJar()
        val url = "https://forums.e-hentai.org/".toHttpUrl()
        val cookie = Cookie.Builder().name("ipb_member_id").value("123").domain("e-hentai.org").build()
        jar.saveFromResponse(url, listOf(cookie))
        assertEquals(1, jar.loadForRequest(url).size)
        assertTrue(jar.loadForRequest("https://hentaiverse.org/".toHttpUrl()).isEmpty())
        jar.saveFromResponse(url, listOf(Cookie.Builder().name(cookie.name).value("").domain(cookie.domain).expiresAt(0).build()))
        assertTrue(jar.loadForRequest(url).isEmpty())
    }
}
