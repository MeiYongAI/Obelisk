package com.mitahv.mobile.data

import java.net.URLDecoder
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumLoginProtocolTest {
    @Test
    fun postsCurrentEhViewerFieldsDirectlyToForum() {
        val request = ForumLoginProtocol.request("example", "test-password")
        assertEquals("https://forums.e-hentai.org/index.php?act=Login&CODE=01", request.url.toString())
        assertEquals("POST", request.method)
        assertEquals("https://forums.e-hentai.org", request.header("Origin"))
        assertEquals(ForumLoginProtocol.LOGIN_URL, request.header("Referer"))
        assertEquals(
            mapOf("referer" to ForumLoginProtocol.LOGIN_URL, "b" to "", "bt" to "",
                "UserName" to "example", "PassWord" to "test-password", "CookieDate" to "1"),
            request.fields(),
        )
        assertTrue(request.body!!.isOneShot())
    }

    @Test
    fun preservesPasswordWhitespaceAndEncodesSeparators() {
        val password = "  test + & = 中文  "
        val fields = ForumLoginProtocol.request(" 用户 + & ", password).fields()
        assertEquals(password, fields["PassWord"])
        assertEquals("用户 + &", fields["UserName"])
    }

    @Test
    fun rejectsEmptyCredentialsBeforeNetwork() {
        assertInvalid { ForumLoginProtocol.request("  ", "test") }
        assertInvalid { ForumLoginProtocol.request("example", "") }
    }

    @Test
    fun extractsErrorParagraphAsPlainText() {
        // Synthetic snippets follow the two structures in EhViewer SignInParser.
        assertEquals("Incorrect name & password.", ForumLoginProtocol.errorMessage(
            "<h4>The error returned was:</h4><p>Incorrect <b>name</b> &amp; password.</p>",
        ))
        assertEquals("Login temporarily unavailable.", ForumLoginProtocol.errorMessage(
            "<span class=\"postcolor\">Login temporarily unavailable.</span>",
        ))
    }

    @Test
    fun identifiesPublicForumChallengeWithoutClaimingLoginSuccess() {
        assertEquals("论坛需要网页验证，请使用网页登录。", ForumLoginProtocol.errorMessage(
            "<title>Just a moment...</title><script src='/cdn-cgi/challenge-platform/example.js'></script>",
        ))
        assertNull(ForumLoginProtocol.errorMessage("<p>You are now logged in as: example</p>"))
        assertNull(ForumLoginProtocol.errorMessage("<p>Unrecognized page</p>"))
    }

    private fun Request.fields(): Map<String, String> {
        val buffer = Buffer()
        body!!.writeTo(buffer)
        return buffer.readUtf8().split('&').associate { field ->
            val pair = field.split('=', limit = 2)
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
        }
    }

    private fun assertInvalid(action: () -> Unit) {
        var rejected = false
        try { action() } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }
}
