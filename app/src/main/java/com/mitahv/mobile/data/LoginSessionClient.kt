package com.mitahv.mobile.data

import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvWorld
import com.mitahv.mobile.model.HvSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class LoginSessionClient(
    private val saveSession: (HvAuthCookies, List<Cookie>) -> Unit,
    private val execute: (Request, LoginCookieJar) -> LoginResponse = ::executeLoginRequest,
    private val site: HvSite = HvSite.Main,
) {
    constructor(persistentCookies: PersistentCookieJar, site: HvSite = HvSite.Main) :
        this(persistentCookies::replaceHvSession, site = site)

    suspend fun signIn(username: String, password: String, world: HvWorld) = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isEmpty()) throw LoginException("请输入账号和密码")
        val jar = LoginCookieJar()
        val request = ForumLoginProtocol.request(username.trim(), password)
        val response = execute(request, jar)
        val auth = HvAuthCookies.fromCookies(jar.loadForRequest(ForumLoginProtocol.LOGIN_URL.toHttpUrl()))
        if (auth == null) {
            throw LoginException(ForumLoginProtocol.errorMessage(response.html)
                ?: if (response.code in setOf(403, 429, 503)) "论坛要求网页验证，请使用网页登录。"
                else "未取得登录凭据，请检查账号密码或使用网页登录。")
        }
        if (response.code !in 200..299) throw LoginException("论坛返回 HTTP ${response.code}，请稍后重试。")
        verifyAndSave(auth, world)
    }

    suspend fun importCookies(header: String, world: HvWorld) = withContext(Dispatchers.IO) {
        val auth = HvAuthCookies.fromHeader(header)
            ?: throw LoginException("Cookie 需要包含有效的 ipb_member_id 和 ipb_pass_hash。")
        verifyAndSave(auth, world)
    }

    private suspend fun verifyAndSave(auth: HvAuthCookies, world: HvWorld) {
        val jar = LoginCookieJar()
        jar.saveFromResponse(world.baseUrl(site), auth.forDomain("hentaiverse.org"))
        val request = Request.Builder().url(HvRoute.Character.url(world, site))
            .header("Accept", "text/html,application/xhtml+xml").build()
        val response = execute(HvEndpointPolicy.prepare(request, world, site), jar)
        if (response.code !in 200..299) throw LoginException("HV 返回 HTTP ${response.code}，请稍后重试。")
        val document = Jsoup.parse(response.html)
        if (document.selectFirst("#mainpane #attr_table") == null && document.selectFirst("#battle_main") == null) {
            throw LoginException("尚未进入 HV 登录状态，请检查 Cookie 或使用网页登录。")
        }
        currentCoroutineContext().ensureActive()
        saveSession(auth, jar.loadForRequest(world.baseUrl(site)))
    }
}

internal data class LoginResponse(val code: Int, val html: String)

private fun executeLoginRequest(request: Request, jar: LoginCookieJar): LoginResponse {
    val client = OkHttpClient.Builder()
        .cookieJar(jar)
        .retryOnConnectionFailure(false)
        .callTimeout(40, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            if (!chain.request().url.isOfficialLoginUrl()) throw LoginException("登录跳转到了非官方地址。")
            if (chain.request().tag(HvRequestScope::class.java) != null) HvEndpointPolicy.check(chain.request())
            chain.proceed(chain.request())
        }
        .build()
    return client.newCall(request).execute().use { response ->
        LoginResponse(response.code, response.body?.string().orEmpty())
    }
}

internal class LoginException(message: String) : IOException(message)

private fun HttpUrl.isOfficialLoginUrl() = scheme == "https" &&
    (host == "e-hentai.org" || host.endsWith(".e-hentai.org") ||
        host == "hentaiverse.org" || host.endsWith(".hentaiverse.org"))
