package com.mitahv.mobile.data

import java.io.IOException
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SessionStoreTest {
    private var disk: String? = null
    private var writable = true
    private fun open() = SessionStore(disk) { value ->
        if (writable) disk = value
        writable
    }
    private fun auth(id: String, hash: String = "fixture-$id-hash"): HvAuthCookies =
        requireNotNull(HvAuthCookies.fromHeader("ipb_member_id=$id; ipb_pass_hash=$hash"))
    private fun cookie(name: String, value: String, domain: String = "hentaiverse.org", expires: Long? = null, path: String = "/", hostOnly: Boolean = false): Cookie =
        Cookie.Builder().name(name).value(value).path(path).secure().httpOnly().apply {
            if (hostOnly) hostOnlyDomain(domain) else domain(domain)
            if (expires != null) expiresAt(expires)
        }.build()
    private val persistent = "https://hentaiverse.org/".toHttpUrl()
    private val isekai = "https://alt.hentaiverse.org/isekai/".toHttpUrl()

    @Test fun verifiedLoginIsTheOnlySessionOnDiskAndOnReopen() {
        val store = open()
        assertNull(store.memberId())
        store.replaceHvSession(auth("101"), listOf(cookie("old_hint", "old")))
        store.replaceHvSession(auth("202"), listOf(cookie("alt", "new", "alt.hentaiverse.org", path = "/isekai/"),
            cookie("forum_only", "ignore", "forums.e-hentai.org")))
        val reopened = open()
        assertEquals("202", reopened.memberId()); assertFalse(disk!!.contains("fixture-101-hash"))
        assertTrue(reopened.loadForRequest(isekai).any { it.name == "alt" })
        assertFalse(reopened.loadForRequest(persistent).any { it.name == "alt" || it.name == "old_hint" })
        assertFalse(disk!!.contains("forum_only")); assertTrue(reopened.loadForRequest("https://forums.e-hentai.org/".toHttpUrl()).isEmpty())
    }
    @Test fun logoutRemovesCredentialsAndLateResponsesCannotSignBackIn() {
        val store = open(); store.replaceHvSession(auth("101"), emptyList())
        assertTrue(store.logout()); assertNull(disk)
        store.saveFromResponse(persistent, auth("101").forDomain("hentaiverse.org"))
        assertNull(store.memberId()); assertTrue(open().loadForRequest(persistent).isEmpty())
    }
    @Test fun failedWritesLeaveTheExistingSessionUntouched() {
        val store = open(); store.replaceHvSession(auth("101"), emptyList()); val before = disk
        writable = false
        assertFalse(store.logout()); assertThrows(IOException::class.java) { store.replaceHvSession(auth("202"), emptyList()) }
        assertThrows(IOException::class.java) { store.saveFromResponse(persistent, listOf(cookie("hint", "new"))) }
        assertEquals(before, disk); assertEquals("101", store.memberId()); assertFalse(store.loadForRequest(persistent).any { it.name == "hint" })
    }
    @Test fun gameResponsesCanRefreshOrExpireButCannotChangeIdentity() {
        val store = open(); store.replaceHvSession(auth("101"), emptyList())
        store.saveFromResponse(persistent, auth("202").forDomain("hentaiverse.org")); assertEquals("101", store.memberId())
        store.saveFromResponse(persistent, listOf(cookie("hint", "fresh"))); assertTrue(open().loadForRequest(persistent).any { it.name == "hint" })
        store.saveFromResponse(persistent, listOf(cookie("ipb_pass_hash", "", expires = 1L)))
        assertNull(store.memberId()); assertNull(disk)
    }
    @Test fun cookieCodecPreservesSessionHostPathFlagsAndSkipsInvalidRows() {
        val original = cookie("state", "fixture", "alt.hentaiverse.org", path = "/isekai/", hostOnly = true)
        val array = SessionCookies.encode(listOf(original, cookie("expired", "fixture", expires = 1L)))
        array.put(JSONObject().put("name", "invalid-no-domain"))
        val decoded = SessionCookies.decode(array, System.currentTimeMillis())
        assertEquals(1, decoded.size)
        assertFalse(decoded.single().persistent)
        assertTrue(decoded.single().hostOnly)
        assertTrue(decoded.single().secure)
        assertTrue(decoded.single().httpOnly)
        assertEquals("/isekai/", decoded.single().path)
    }
}
