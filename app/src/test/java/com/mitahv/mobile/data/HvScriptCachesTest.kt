package com.mitahv.mobile.data

import java.io.Closeable
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HvScriptCachesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun serverFreshnessAllowsHitsButChangedCookieRequiresNetwork() {
        withTransport("public, max-age=3600") { client, server ->
            assertEquals("dictionary-1", client.read(server.url, "account=A"))
            assertEquals("dictionary-1", client.read(server.url, "account=A"))
            assertEquals(1, server.requests.get())
            assertEquals("dictionary-2", client.read(server.url, "account=B"))
            assertEquals("dictionary-2", client.read(server.url, "account=B"))
            assertEquals(2, server.requests.get())
        }
    }

    @Test fun serverNoStoreIsNotCached() {
        withTransport("no-store") { client, server ->
            assertEquals("dictionary-1", client.read(server.url, "account=A"))
            assertEquals("dictionary-2", client.read(server.url, "account=A"))
            assertEquals(2, server.requests.get())
        }
    }

    @Test fun accountDirectoriesAreIsolatedAndHaveOneCacheOwner() {
        val root = temporary.newFolder("account-cache")
        assertNull(HvScriptCaches.forAccount(root, null))
        val first = requireNotNull(HvScriptCaches.forAccount(root, "account-A"))
        val second = requireNotNull(HvScriptCaches.forAccount(root, "account-B"))
        try {
            assertSame(first, HvScriptCaches.forAccount(root, "account-A"))
            assertNotSame(first, second)
            assertNotEquals(first.directory, second.directory)
        } finally {
            first.close()
            second.close()
        }
    }

    private fun withTransport(cacheControl: String, test: (OkHttpClient, LocalServer) -> Unit) {
        Cache(temporary.newFolder("http-cache"), 1024L * 1024L).use { cache ->
            LocalServer(cacheControl).use { server ->
                val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).cache(cache)
                    .callTimeout(3, TimeUnit.SECONDS)
                    .addNetworkInterceptor(HvScriptCaches.cookieVaryInterceptor).build()
                try { test(client, server) } finally {
                    client.dispatcher.executorService.shutdownNow()
                    client.connectionPool.evictAll()
                }
            }
        }
    }

    private fun OkHttpClient.read(url: String, cookie: String): String =
        newCall(Request.Builder().url(url).header("Cookie", cookie).build()).execute().use {
            assertEquals(200, it.code)
            requireNotNull(it.body).string()
        }

    private class LocalServer(cacheControl: String) : Closeable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${socket.localPort}/dictionary.js"
        val requests = AtomicInteger()
        private val failure = AtomicReference<Throwable?>()
        private val worker = thread(isDaemon = true, name = "test-http-cache") {
            try {
                while (!socket.isClosed) socket.accept().use { connection ->
                    connection.soTimeout = 2_000
                    val reader = connection.getInputStream().bufferedReader(Charsets.US_ASCII)
                    requireNotNull(reader.readLine())
                    while (!reader.readLine().isNullOrEmpty()) { }
                    val body = "dictionary-${requests.incrementAndGet()}"
                    val date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(java.time.ZoneOffset.UTC))
                    val headers = "HTTP/1.1 200 OK\r\nDate: $date\r\nCache-Control: $cacheControl\r\n" +
                        "Content-Type: application/javascript\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n"
                    connection.getOutputStream().apply {
                        write((headers + body).toByteArray(Charsets.US_ASCII))
                        flush()
                    }
                }
            } catch (error: Throwable) {
                if (!socket.isClosed) failure.set(error)
            }
        }
        override fun close() {
            socket.close()
            worker.join(3_000)
            assertFalse("Loopback server did not stop", worker.isAlive)
            assertNull("Loopback server failed", failure.get())
        }
    }
}
