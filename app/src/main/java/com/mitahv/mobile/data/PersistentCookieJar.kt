package com.mitahv.mobile.data

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

internal class PersistentCookieJar(context: Context) : CookieJar {
    private val store = AccountStore(PreferenceAccountPersistence(context.applicationContext
        .getSharedPreferences("hv_http_cookies", Context.MODE_PRIVATE)))

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = store.saveFromResponse(url, cookies)
    override fun loadForRequest(url: HttpUrl): List<Cookie> = store.loadForRequest(url)
    fun hasCookies(): Boolean = store.hasCookies()
    fun accounts(): List<SavedAccountInfo> = store.accounts()
    fun activeAccountId(): String? = store.activeAccountId()
    fun savedSession(memberId: String): SavedAccountSession? = store.savedSession(memberId)
    fun switchAccount(memberId: String): Boolean = store.switchAccount(memberId)
    fun removeAccount(memberId: String): Boolean = store.removeAccount(memberId)
    fun logout(): Boolean = store.logout()

    fun replaceHvSession(auth: HvAuthCookies, verifiedCookies: List<Cookie>) = store.replaceHvSession(auth, verifiedCookies)
    fun replaceHvSession(auth: HvAuthCookies, verifiedCookies: List<Cookie>, label: String?) = store.replaceHvSession(auth, verifiedCookies, label)

    /** Legacy callers clear only the active session; account removal is explicit. */
    fun clear(): Boolean = logout()
}

private class PreferenceAccountPersistence(private val preferences: SharedPreferences) : AccountPersistence {
    override fun readState(): String? = preferences.getString(STATE_KEY, null)
    override fun readLegacyCookies(): String? = preferences.getString(LEGACY_KEY, null)
    override fun writeState(value: String): Boolean {
        val previousState = preferences.getString(STATE_KEY, null)
        val previousLegacy = preferences.getString(LEGACY_KEY, null)
        val saved = runCatching { preferences.edit().putString(STATE_KEY, value).remove(LEGACY_KEY).commit() }.getOrDefault(false)
        if (!saved) {
            // SharedPreferences changes its memory view before reporting a failed disk commit.
            val rollback = preferences.edit()
            if (previousState == null) rollback.remove(STATE_KEY) else rollback.putString(STATE_KEY, previousState)
            if (previousLegacy == null) rollback.remove(LEGACY_KEY) else rollback.putString(LEGACY_KEY, previousLegacy)
            runCatching { rollback.commit() }
        }
        return saved
    }

    private companion object {
        const val STATE_KEY = "accounts_v1"
        const val LEGACY_KEY = "cookies"
    }
}
