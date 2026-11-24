package com.mitahv.mobile.data

import android.util.Log
import java.io.IOException
import java.net.InetAddress
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Request
import okhttp3.Response

/** Timings only: never log URL parameters, credentials, bodies or exception messages. */
internal class HvNetworkTiming(request: Request) : EventListener() {
    private val started = System.nanoTime()
    private val label = "${request.method} ${request.url.host} " + when {
        request.url.encodedPath.contains("/dynjs/") -> "dictionary"
        request.url.encodedPath.endsWith("/json") -> "battle"
        request.header("Accept")?.contains("text/html") == true -> "page"
        else -> "resource"
    }
    private var dnsStarted = 0L
    private var tlsStarted = 0L
    private var dnsMs = 0L
    private var tlsMs = 0L
    private var headersMs = 0L
    private var encoding = "identity"
    private var wireBytes = 0L

    private fun elapsed(start: Long) = (System.nanoTime() - start) / 1_000_000

    override fun dnsStart(call: Call, domainName: String) { dnsStarted = System.nanoTime() }
    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        dnsMs += elapsed(dnsStarted)
    }
    override fun secureConnectStart(call: Call) { tlsStarted = System.nanoTime() }
    override fun secureConnectEnd(call: Call, handshake: Handshake?) { tlsMs += elapsed(tlsStarted) }
    override fun responseHeadersEnd(call: Call, response: Response) {
        headersMs = elapsed(started)
        encoding = response.header("Content-Encoding") ?: "identity"
    }
    override fun responseBodyEnd(call: Call, byteCount: Long) { wireBytes = byteCount }
    override fun callEnd(call: Call) {
        Log.d("HvNetwork", "$label total=${elapsed(started)}ms dns=${dnsMs}ms tls=${tlsMs}ms headers=${headersMs}ms bytes=$wireBytes encoding=$encoding")
    }
    override fun callFailed(call: Call, ioe: IOException) {
        val causes = generateSequence<Throwable>(ioe) { it.cause }.take(4)
            .joinToString("/") { it.javaClass.simpleName }
        Log.d("HvNetwork", "$label ${if (call.isCanceled()) "cancelled" else "failed"} " +
            "after=${elapsed(started)}ms dns=${dnsMs}ms tls=${tlsMs}ms headers=${headersMs}ms cause=$causes")
    }
}
