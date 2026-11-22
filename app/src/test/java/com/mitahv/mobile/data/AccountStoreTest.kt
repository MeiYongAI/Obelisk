package com.mitahv.mobile.data

import java.io.IOException
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AccountStoreTest {
    private class MemoryPersistence : AccountPersistence {
        var state: String? = null
        var legacy: String? = null
        var canWrite = true
        var writes = 0
        override fun readState(): String? = state
        override fun readLegacyCookies(): String? = legacy
        override fun writeState(value: String): Boolean {
            writes++
            if (!canWrite) return false
            state = value
            legacy = null
            return true
        }
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
    private fun assertActive(store: AccountStore, expected: String) {
        assertEquals(expected, store.activeAccountId())
        assertEquals(expected, HvAuthCookies.fromCookies(store.loadForRequest(persistent))?.memberId)
    }

    @Test fun migratesLegacyActiveSessionWithoutPersonaOrForumCookies() {
        val storage = MemoryPersistence().apply {
            legacy = AccountCookieCodec.encode(auth("101").forDomain("hentaiverse.org") +
                cookie("session_hint", "fixture-hv") + cookie("forum_only", "fixture-forum", "forums.e-hentai.org")).toString()
        }
        val store = AccountStore(storage)
        assertActive(store, "101")
        assertEquals("", store.accounts().single().label)
        assertEquals("账号 101", store.accounts().single().displayName)
        assertTrue(store.accounts().single().isActive)
        assertTrue(store.accounts().single().hasUsableSession)
        assertNotNull(storage.state)
        assertNull(storage.legacy)
        assertTrue(store.savedSession("101")!!.cookies.none { it.domain.contains("e-hentai") })
        assertTrue(store.loadForRequest("https://forums.e-hentai.org/".toHttpUrl()).isEmpty())
        val reopened = AccountStore(storage)
        assertActive(reopened, "101")
    }

    @Test fun failedMigrationKeepsLegacySessionUsableAndRetryable() {
        val storage = MemoryPersistence().apply {
            legacy = AccountCookieCodec.encode(auth("101").forDomain("hentaiverse.org")).toString()
            canWrite = false
        }
        assertActive(AccountStore(storage), "101")
        assertNull(storage.state)
        assertNotNull(storage.legacy)
        storage.canWrite = true
        assertActive(AccountStore(storage), "101")
        assertNotNull(storage.state)
        assertNull(storage.legacy)
    }

    @Test fun addsTwoAccountsSwitchesBothWorldsAndPreservesLocalLabels() {
        val storage = MemoryPersistence()
        val store = AccountStore(storage)
        store.replaceHvSession(auth("101"), emptyList())
        store.saveCurrentAccount("  Login Name A  ")
        store.replaceHvSession(auth("202"), emptyList(), "Login Name B")
        assertEquals(listOf("101", "202"), store.accounts().map { it.memberId })
        assertTrue(store.switchAccount("101"))
        assertActive(store, "101")
        assertEquals("101", HvAuthCookies.fromCookies(store.loadForRequest(isekai))?.memberId)
        assertEquals("Login Name A", store.accounts().first().label)
        store.replaceHvSession(auth("101", "new-fixture-hash"), emptyList())
        assertEquals(2, store.accounts().size)
        assertEquals("Login Name A", store.accounts().first().label)
        assertActive(AccountStore(storage), "101")
    }

    @Test fun replacingSessionDoesNotCarryAnotherAccountsHvCookies() {
        val store = AccountStore(MemoryPersistence())
        store.replaceHvSession(auth("101"), listOf(cookie("hv_session", "fixture-A")))
        store.replaceHvSession(auth("202"), listOf(cookie("hv_session", "fixture-B"), cookie("forum_only", "ignored", "e-hentai.org")))
        assertTrue(store.loadForRequest(persistent).any { it.name == "hv_session" && it.value == "fixture-B" })
        assertTrue(store.loadForRequest(persistent).none { it.value == "fixture-A" })
        assertTrue(store.switchAccount("101"))
        assertTrue(store.loadForRequest(persistent).any { it.name == "hv_session" && it.value == "fixture-A" })
        assertTrue(store.savedSession("202")!!.cookies.none { it.domain == "e-hentai.org" })
    }

    @Test fun responseUpdatesActiveSnapshotButLeavesOtherAccountUntouched() {
        val storage = MemoryPersistence()
        val store = AccountStore(storage)
        store.replaceHvSession(auth("101"), listOf(cookie("hv_session", "fixture-A")))
        store.replaceHvSession(auth("202"), listOf(cookie("hv_session", "fixture-B")))
        store.saveFromResponse(isekai, listOf(cookie("alt_state", "fixture-alt", "alt.hentaiverse.org", path = "/isekai/", hostOnly = true),
            cookie("ipb_pass_hash", "rotated-fixture-hash")))
        assertTrue(store.savedSession("202")!!.cookies.any { it.name == "ipb_pass_hash" && it.value == "rotated-fixture-hash" })
        assertTrue(store.savedSession("101")!!.cookies.none { it.name == "alt_state" })
        assertTrue(store.switchAccount("101"))
        assertTrue(store.loadForRequest(isekai).none { it.name == "alt_state" })
        assertTrue(store.switchAccount("202"))
        val reopened = AccountStore(storage)
        assertTrue(reopened.loadForRequest(isekai).any { it.name == "alt_state" })
        assertTrue(reopened.loadForRequest(persistent).none { it.name == "alt_state" })
    }

    @Test fun expiryRemovesCookieFromSavedSnapshotWithoutLosingOtherAccounts() {
        val store = AccountStore(MemoryPersistence())
        store.replaceHvSession(auth("101"), emptyList())
        store.replaceHvSession(auth("202"), emptyList())
        store.saveFromResponse(persistent, listOf(cookie("ipb_pass_hash", "expired", expires = 1L)))
        assertFalse(store.hasCookies())
        assertFalse(store.accounts().first { it.memberId == "202" }.hasUsableSession)
        assertNull(store.savedSession("202"))
        assertFalse(store.switchAccount("202"))
        assertTrue(store.switchAccount("101"))
        assertActive(store, "101")
    }

    @Test fun failedAddSwitchRenameRemoveAndLogoutKeepOldState() {
        val storage = MemoryPersistence()
        val store = AccountStore(storage)
        store.replaceHvSession(auth("101"), emptyList(), "Name A")
        store.replaceHvSession(auth("202"), emptyList(), "Name B")
        val before = storage.state
        storage.canWrite = false
        assertFalse(store.switchAccount("101"))
        assertFalse(store.renameAccount("202", "Changed"))
        assertFalse(store.removeAccount("202"))
        assertFalse(store.logout())
        assertNull(store.saveCurrentAccount("Changed"))
        try {
            store.replaceHvSession(auth("303"), emptyList())
            fail("Expected failed persistence")
        } catch (_: IOException) { }
        assertEquals(before, storage.state)
        assertActive(store, "202")
        assertEquals("Name B", store.accounts().first { it.memberId == "202" }.label)
        assertEquals(2, store.accounts().size)
    }

    @Test fun failedResponsePersistenceKeepsPreviousSnapshot() {
        val storage = MemoryPersistence()
        val store = AccountStore(storage)
        store.replaceHvSession(auth("101"), listOf(cookie("hv_state", "before")))
        storage.canWrite = false
        try {
            store.saveFromResponse(persistent, listOf(cookie("hv_state", "after")))
            fail("Expected failed persistence")
        } catch (_: IOException) { }
        assertTrue(store.loadForRequest(persistent).any { it.name == "hv_state" && it.value == "before" })
        assertTrue(store.savedSession("101")!!.cookies.any { it.name == "hv_state" && it.value == "before" })
    }

    @Test fun logoutKeepsSavedAccountsAndIgnoresLateResponses() {
        val storage = MemoryPersistence()
        val store = AccountStore(storage)
        store.replaceHvSession(auth("101"), emptyList())
        assertTrue(store.logout())
        store.saveFromResponse(persistent, auth("101").forDomain("hentaiverse.org"))
        assertNull(store.activeAccountId())
        assertFalse(store.hasCookies())
        assertEquals(1, store.accounts().size)
        assertFalse(store.accounts().single().isActive)
        assertTrue(store.loadForRequest(persistent).isEmpty())
        val reopened = AccountStore(storage)
        assertNull(reopened.activeAccountId())
        assertTrue(reopened.switchAccount("101"))
        assertActive(reopened, "101")
    }

    @Test fun removingInactiveAccountDoesNotSignOutAndRemovingActiveDoes() {
        val storage = MemoryPersistence()
        val store = AccountStore(storage)
        store.replaceHvSession(auth("101"), emptyList())
        store.replaceHvSession(auth("202"), emptyList())
        assertTrue(store.removeAccount("101"))
        assertActive(store, "202")
        assertFalse(store.switchAccount("101"))
        assertTrue(store.removeAccount("202"))
        assertNull(store.activeAccountId())
        assertTrue(store.accounts().isEmpty())
        assertTrue(AccountStore(storage).accounts().isEmpty())
    }

    @Test fun unknownAccountOperationsDoNotModifyCurrentSession() {
        val storage = MemoryPersistence()
        val store = AccountStore(storage)
        store.replaceHvSession(auth("101"), emptyList())
        val writes = storage.writes
        assertFalse(store.switchAccount("999"))
        assertFalse(store.renameAccount("999", "Unused"))
        assertFalse(store.removeAccount("999"))
        assertNull(store.savedSession("999"))
        assertEquals(writes, storage.writes)
        assertActive(store, "101")
    }

    @Test fun gameResponseCannotChangeAccountIdentity() {
        val store = AccountStore(MemoryPersistence())
        store.replaceHvSession(auth("101"), emptyList())
        store.saveFromResponse(persistent, auth("202").forDomain("hentaiverse.org"))
        assertActive(store, "101")
        assertEquals(listOf("101"), store.accounts().map { it.memberId })
    }

    @Test fun malformedOrExpiredSavedSessionDoesNotReplaceValidActiveAccount() {
        val storage = MemoryPersistence()
        AccountStore(storage).apply { replaceHvSession(auth("101"), emptyList()); replaceHvSession(auth("202"), emptyList()); switchAccount("101") }
        val json = JSONObject(storage.state!!)
        json.getJSONArray("accounts").getJSONObject(1).put("cookies", JSONArray().put(JSONObject().put("name", "ipb_member_id").put("value", "202").put("domain", "hentaiverse.org")))
        storage.state = json.toString()
        val store = AccountStore(storage)
        assertFalse(store.switchAccount("202"))
        assertActive(store, "101")
        assertFalse(store.accounts().first { it.memberId == "202" }.hasUsableSession)
    }

    @Test fun diagnosticsNeverContainSessionValues() {
        val store = AccountStore(MemoryPersistence())
        store.replaceHvSession(auth("101", "sensitive-fixture-value"), emptyList(), "Local label")
        assertFalse(store.accounts().toString().contains("sensitive-fixture-value"))
        assertEquals("SavedAccountSession(redacted)", store.savedSession("101").toString())
        assertEquals("HvAuthCookies(redacted)", store.savedSession("101")!!.auth.toString())
        assertFalse(store.accounts().toString().contains("ipb_pass_hash"))
    }

    @Test fun cookieCodecPreservesSessionHostPathFlagsAndSkipsInvalidRows() {
        val original = cookie("state", "fixture", "alt.hentaiverse.org", path = "/isekai/", hostOnly = true)
        val array = AccountCookieCodec.encode(listOf(original, cookie("expired", "fixture", expires = 1L)))
        array.put(JSONObject().put("name", "invalid-no-domain"))
        val decoded = AccountCookieCodec.decode(array, System.currentTimeMillis())
        assertEquals(1, decoded.size)
        assertFalse(decoded.single().persistent)
        assertTrue(decoded.single().hostOnly)
        assertTrue(decoded.single().secure)
        assertTrue(decoded.single().httpOnly)
        assertEquals("/isekai/", decoded.single().path)
    }
}
