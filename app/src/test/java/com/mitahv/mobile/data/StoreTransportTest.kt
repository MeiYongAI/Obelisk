package com.mitahv.mobile.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class StoreTransportTest {
    private val transport = StoreTransport()
    private val api = "https://api.github.com/repos/MeiYongAI/JPX-PLUS-Configs"
    private val catalog = "https://raw.githubusercontent.com/MeiYongAI/JPX-PLUS-Configs/main/catalog/"
    private val counter = "https://jpx-plus-downloads.lernsc.workers.dev/v1/downloads"

    private fun request(url: String, method: String = "GET", headers: JSONObject = JSONObject()) =
        transport.call(JSONObject().put("url", url).put("method", method).put("headers", headers)
            .apply { if (method in setOf("POST", "PATCH")) put("body", "{}") }).request()

    @Test fun catalogDeviceFlowAndCommunityRoutesBuildWithoutGameCookies() {
        val endpoints = listOf(
            "GET" to (catalog + "index.json"),
            "GET" to (catalog + "17.json"),
            "GET" to "$api/contents/catalog/index.json",
            "GET" to "$counter?ids=17",
            "POST" to counter,
            "POST" to "https://github.com/login/device/code",
            "POST" to "https://github.com/login/oauth/access_token",
        )
        for ((method, url) in endpoints) {
            val result = request(url, method)
            assertEquals(method, result.method)
            assertNull(result.header("Cookie"))
            assertNull(result.header("Authorization"))
        }
        val authenticated = listOf("GET" to "https://api.github.com/user", "POST" to "$api/issues",
            "PATCH" to "$api/issues/17", "POST" to "$api/issues/17/comments", "DELETE" to "$api/issues/comments/23")
        for ((method, url) in authenticated) {
            val result = request(url, method, JSONObject().put("Authorization", "Bearer test-token"))
            assertEquals("Bearer test-token", result.header("Authorization"))
            assertNull(result.header("Cookie"))
        }
    }

    @Test fun credentialsForeignRepositoriesAndNavigationRequestsAreRejectedBeforeDispatch() {
        val headers = JSONObject().put("Authorization", "Bearer test-token")
        val blocked = listOf(
            { request(catalog + "index.json", headers = headers) },
            { request(counter, "POST", headers) },
            { request("https://github.com/login/device/code", "POST", headers) },
            { request("$api/issues/17", headers = JSONObject().put("Cookie", "test-cookie")) },
            { request("https://api.github.com/repos/another/project/issues") },
            { request("https://example.invalid/") },
            { request("http://api.github.com/user") },
            { request("https://api.github.com:8443/user") },
            { request("https://user@api.github.com/user") },
            { request("https://api.github.com/user", "PATCH") },
        )
        blocked.forEach { assertThrows(IllegalArgumentException::class.java) { it() } }
    }
}
