package com.mitahv.mobile.data

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

internal class PersistentCookieJar(context: Context) : CookieJar {
    private val preferences = context.applicationContext.getSharedPreferences("hv_http_cookies", Context.MODE_PRIVATE)
    private val store = SessionStore(preferences.getString(SESSION, null)) { value ->
        val previous = preferences.getString(SESSION, null)
        // This file belongs only to authentication. Never retain a second sign-in on disk.
        val saved = preferences.edit().clear().putString(SESSION, value).commit()
        if (!saved) preferences.edit().putString(SESSION, previous).commit()
        saved
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = store.saveFromResponse(url, cookies)
    override fun loadForRequest(url: HttpUrl) = store.loadForRequest(url)
    fun memberId() = store.memberId()
    fun logout() = store.logout()
    fun replaceHvSession(auth: HvAuthCookies, verifiedCookies: List<Cookie>) = store.replaceHvSession(auth, verifiedCookies)

    private companion object { const val SESSION = "session" }
}
