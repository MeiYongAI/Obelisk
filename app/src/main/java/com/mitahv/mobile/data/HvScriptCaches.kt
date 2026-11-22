package com.mitahv.mobile.data

import java.io.File
import java.security.MessageDigest
import okhttp3.Cache
import okhttp3.Interceptor

/** One OkHttp owner per account directory; freshness and revalidation remain server-controlled. */
internal object HvScriptCaches {
    private val caches = mutableMapOf<String, Cache>()

    @Synchronized
    fun forAccount(cacheRoot: File, accountId: String?): Cache? {
        if (accountId.isNullOrBlank()) return null
        val accountKey = MessageDigest.getInstance("SHA-256")
            .digest(accountId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val directory = File(cacheRoot, accountKey).absoluteFile
        return caches.getOrPut(directory.path) { Cache(directory, 16L * 1024L * 1024L) }
    }

    // A mutable CookieJar may switch account before an old client's call finishes. Never reuse a
    // dictionary cached under different credentials, even inside the old account's cache directory.
    val cookieVaryInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val vary = response.headers.values("Vary").flatMap { it.split(',') }.map { it.trim() }
        if (vary.any { it == "*" || it.equals("Cookie", ignoreCase = true) }) response
        else response.newBuilder().addHeader("Vary", "Cookie").build()
    }
}
