package com.mitahv.mobile.data

import okhttp3.FormBody
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.jsoup.Jsoup

/** Forum fields verified against EhViewer's EhEngine.signIn on Codeberg (2026-11-21). */
internal object ForumLoginProtocol {
    const val LOGIN_URL = "https://forums.e-hentai.org/index.php?act=Login&CODE=00"
    private const val SUBMIT_URL = "https://forums.e-hentai.org/index.php?act=Login&CODE=01"
    private const val ORIGIN = "https://forums.e-hentai.org"

    fun request(username: String, password: String): Request {
        require(username.isNotBlank()) { "请输入账号" }
        require(password.isNotEmpty()) { "请输入密码" }
        val form = FormBody.Builder()
            .add("referer", LOGIN_URL)
            .add("b", "")
            .add("bt", "")
            .add("UserName", username.trim())
            .add("PassWord", password)
            .add("CookieDate", "1")
            .build()
        return Request.Builder()
            .url(SUBMIT_URL)
            .header("Referer", LOGIN_URL)
            .header("Origin", ORIGIN)
            .header("Accept", "text/html,application/xhtml+xml")
            .post(LoginBody(form))
            .build()
    }

    // Absence of an error is not proof of authentication. The session client must
    // require a fresh credential pair and verify access to HentaiVerse.
    fun errorMessage(html: String): String? {
        val document = Jsoup.parse(html)
        if (document.title().equals("Just a moment...", ignoreCase = true) &&
            html.contains("/cdn-cgi/challenge-platform/")) {
            return "论坛需要网页验证，请使用网页登录。"
        }
        val errorHeading = document.select("h4")
            .firstOrNull { it.text().trim() == "The error returned was:" }
        val followingParagraph = errorHeading?.nextElementSibling()?.takeIf { it.tagName() == "p" }
        val error = followingParagraph?.text()?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("span.postcolor")?.text()?.takeIf { it.isNotBlank() }
        return error?.take(500)
    }

    // Credentials must not be automatically replayed after a network failure.
    private class LoginBody(private val delegate: FormBody) : RequestBody() {
        override fun contentType() = delegate.contentType()
        override fun contentLength() = delegate.contentLength()
        override fun isOneShot() = true
        override fun writeTo(sink: BufferedSink) = delegate.writeTo(sink)
    }
}
