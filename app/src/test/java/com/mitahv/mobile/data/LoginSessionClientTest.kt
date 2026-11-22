package com.mitahv.mobile.data

import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvWorld
import com.mitahv.mobile.model.HvSite
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic transport tests: no real credentials, network, redirect or Android storage validation. */
class LoginSessionClientTest {
    @Test fun importedCookiesVerifyAtSelectedSiteWithoutChangingWorld() = runBlocking {
        for (world in HvWorld.entries) for (site in HvSite.entries) {
            var saves = 0
            val client = LoginSessionClient(
                saveSession = { _, _ -> saves++ },
                execute = { request, jar ->
                    assertEquals(HvRoute.Character.url(world, site), request.url)
                    HvEndpointPolicy.check(request)
                    assertEquals(HvAuthCookies.names, jar.loadForRequest(request.url).map { it.name }.toSet())
                    LoginResponse(200, CHARACTER_HTML)
                },
                site = site,
            )
            client.importCookies(AUTH_HEADER, world)
            assertEquals(1, saves)
        }
    }

    @Test
    fun successfulForumResponseWithOnlyOneCredentialDoesNotSave() = runBlocking {
        var persistedAccount = "existing-account"
        var requests = 0
        val client = LoginSessionClient(
            saveSession = { _, _ -> persistedAccount = "new-account" },
            execute = { request, jar ->
                requests++
                jar.saveFromResponse(request.url, listOf(forumCookie("ipb_member_id", "123")))
                LoginResponse(200, "<p>You are now logged in as: test account</p>")
            },
        )

        expectLoginFailure { client.signIn("example", "test-password", HvWorld.Persistent) }

        assertEquals(1, requests)
        assertEquals("existing-account", persistedAccount)
    }

    @Test
    fun completeForumCredentialsWithHvLoginPageDoNotReplaceExistingSession() = runBlocking {
        var persistedAccount = "existing-account"
        var requests = 0
        val client = LoginSessionClient(
            saveSession = { _, _ -> persistedAccount = "new-account" },
            execute = { request, jar ->
                requests++
                if (request.url.host == "forums.e-hentai.org") {
                    addForumCredentials(jar)
                    LoginResponse(200, "<p>You are now logged in as: test account</p>")
                } else {
                    LoginResponse(200, "<form><input name='UserName'><input name='PassWord'></form>")
                }
            },
        )

        expectLoginFailure { client.signIn("example", "test-password", HvWorld.Persistent) }

        assertEquals(2, requests)
        assertEquals("existing-account", persistedAccount)
    }

    @Test
    fun hvForbiddenResponseDoesNotPersistEvenWithCharacterMarkup() = runBlocking {
        var saves = 0
        val client = LoginSessionClient(
            saveSession = { _, _ -> saves++ },
            execute = { request, jar ->
                if (request.url.host == "forums.e-hentai.org") {
                    addForumCredentials(jar)
                    LoginResponse(200, "<p>Logged in</p>")
                } else LoginResponse(403, CHARACTER_HTML)
            },
        )

        expectLoginFailure { client.signIn("example", "test-password", HvWorld.Isekai) }

        assertEquals(0, saves)
    }

    @Test
    fun verifiedCharacterPageSavesSharedCredentialsAtEachWorldRoute() = runBlocking {
        for (world in HvWorld.entries) {
            var requests = 0
            var saves = 0
            val client = LoginSessionClient(
                saveSession = { auth, cookies ->
                    saves++
                    assertEquals(HvAuthCookies.names, cookies.map { it.name }.toSet())
                    for (target in HvWorld.entries) {
                        assertTrue(auth.forDomain("hentaiverse.org").all { it.matches(target.baseUrl) })
                    }
                },
                execute = { request, jar ->
                    requests++
                    if (request.url.host == "forums.e-hentai.org") {
                        assertTrue(jar.loadForRequest(request.url).isEmpty())
                        addForumCredentials(jar)
                        LoginResponse(200, "<p>You are now logged in as: test account</p>")
                    } else {
                        assertEquals("GET", request.method)
                        assertEquals(HvRoute.Character.url(world), request.url)
                        val sent = jar.loadForRequest(request.url)
                        assertEquals(HvAuthCookies.names, sent.map { it.name }.toSet())
                        assertTrue(sent.all { it.domain == "hentaiverse.org" && it.secure && it.httpOnly })
                        assertTrue(jar.loadForRequest(FORUM_URL).isEmpty())
                        LoginResponse(200, CHARACTER_HTML)
                    }
                },
            )

            client.signIn("example", "test-password", world)

            assertEquals(2, requests)
            assertEquals(1, saves)
        }
    }

    @Test
    fun importedCredentialsCanVerifyAnActiveBattleWithoutForumRequest() = runBlocking {
        for (world in HvWorld.entries) {
            var requests = 0
            var saves = 0
            val client = LoginSessionClient(
                saveSession = { _, cookies ->
                    saves++
                    assertEquals(HvAuthCookies.names, cookies.map { it.name }.toSet())
                },
                execute = { request, jar ->
                    requests++
                    assertEquals(HvRoute.Character.url(world), request.url)
                    assertEquals(HvAuthCookies.names, jar.loadForRequest(request.url).map { it.name }.toSet())
                    LoginResponse(200, "<div id='battle_main'></div>")
                },
            )

            client.importCookies("$AUTH_HEADER; cf_clearance=unrelated-browser-cookie", world)

            assertEquals(1, requests)
            assertEquals(1, saves)
        }
    }

    @Test
    fun invalidImportedHeaderDoesNotSendARequestOrSave() = runBlocking {
        var requested = false
        var saved = false
        val client = LoginSessionClient(
            saveSession = { _, _ -> saved = true },
            execute = { _, _ -> requested = true; LoginResponse(200, CHARACTER_HTML) },
        )

        expectLoginFailure { client.importCookies("ipb_member_id=123", HvWorld.Persistent) }

        assertFalse(requested)
        assertFalse(saved)
    }

    @Test
    fun cancellationAtHvResponseDoesNotPersistVerifiedCredentials() = runBlocking {
        var saves = 0
        lateinit var operation: Job
        val client = LoginSessionClient(
            saveSession = { _, _ -> saves++ },
            execute = { _, _ ->
                operation.cancel()
                LoginResponse(200, CHARACTER_HTML)
            },
        )
        operation = launch(start = CoroutineStart.LAZY) {
            client.importCookies(AUTH_HEADER, HvWorld.Persistent)
        }

        operation.start()
        operation.join()

        assertTrue(operation.isCancelled)
        assertEquals(0, saves)
    }

    private suspend fun expectLoginFailure(action: suspend () -> Unit) {
        var failed = false
        try { action() } catch (_: LoginException) { failed = true }
        assertTrue("Authentication must fail without persisting", failed)
    }

    private fun addForumCredentials(jar: LoginCookieJar) {
        jar.saveFromResponse(FORUM_URL, listOf(
            forumCookie("ipb_member_id", "123"),
            forumCookie("ipb_pass_hash", "synthetic-test-hash"),
            forumCookie("cf_clearance", "forum-only-test-cookie"),
        ))
    }

    private fun forumCookie(name: String, value: String) = Cookie.Builder()
        .name(name).value(value).domain("e-hentai.org").path("/").secure().httpOnly().build()

    private companion object {
        val FORUM_URL = ForumLoginProtocol.LOGIN_URL.toHttpUrl()
        const val AUTH_HEADER = "ipb_member_id=123; ipb_pass_hash=synthetic-test-hash"
        const val CHARACTER_HTML = "<div id='mainpane'><table id='attr_table'></table></div>"
    }
}
