package com.mitahv.mobile

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import java.io.InterruptedIOException
import java.io.File
import android.content.Context
import android.view.Window
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.core.view.WindowCompat
import com.mitahv.mobile.data.HvScriptCaches
import com.mitahv.mobile.data.HvNetworkTiming
import com.mitahv.mobile.data.HentaiVerseClient
import com.mitahv.mobile.data.AppPreferences
import com.mitahv.mobile.data.BattlePageParser
import com.mitahv.mobile.data.RiddlePageParser
import com.mitahv.mobile.data.CharacterAction
import com.mitahv.mobile.data.verify
import com.mitahv.mobile.data.CharacterPageParser
import com.mitahv.mobile.data.PersistentCookieJar
import com.mitahv.mobile.data.LoginSessionClient
import com.mitahv.mobile.data.LoginException
import com.mitahv.mobile.data.ForumLoginProtocol
import com.mitahv.mobile.data.HvNavigationParser
import com.mitahv.mobile.data.HvDataScriptRequests
import com.mitahv.mobile.data.CharacterSectionsParser
import com.mitahv.mobile.data.BazaarSectionsParser
import com.mitahv.mobile.data.BattleModesParser
import com.mitahv.mobile.model.ArenaChallenge
import com.mitahv.mobile.model.BattleAction
import com.mitahv.mobile.model.BattleContinuation
import com.mitahv.mobile.model.BattleLoadout
import com.mitahv.mobile.model.BattlePage
import com.mitahv.mobile.model.RiddlePage
import com.mitahv.mobile.model.CharacterPage
import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvSection
import com.mitahv.mobile.model.HvNavigation
import com.mitahv.mobile.model.NativeAction
import com.mitahv.mobile.model.CharacterSectionsPage
import com.mitahv.mobile.model.BazaarSectionsPage
import com.mitahv.mobile.model.BattleModesPage
import com.mitahv.mobile.model.HvWorld
import com.mitahv.mobile.model.HvSite
import com.mitahv.mobile.model.AppSettingsAccount
import com.mitahv.mobile.model.AppThemeMode
import com.mitahv.mobile.model.AppLanguage
import com.mitahv.mobile.i18n.ProvideLocalizedText
import com.mitahv.mobile.i18n.ProvideGameTextScope
import com.mitahv.mobile.i18n.messageText
import com.mitahv.mobile.i18n.gameText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Cache
import java.security.MessageDigest
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val cookieJar = PersistentCookieJar(this)
        setContent { ProvideLocalizedText { ObeliskApp(cookieJar, window) } }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ObeliskApp(cookieJar: PersistentCookieJar, window: Window) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var language by remember {
        mutableStateOf(AppLanguage.fromTag(AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag()))
    }
    LaunchedEffect(configuration) {
        language = AppLanguage.fromTag(AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag())
    }
    val preferences = remember { AppPreferences(context) }
    var site by remember { mutableStateOf(preferences.site) }
    var theme by remember { mutableStateOf(preferences.theme) }
    var dynamicColor by remember { mutableStateOf(preferences.dynamicColor) }
    val dark = when (theme) {
        AppThemeMode.System -> isSystemInDarkTheme()
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
    LaunchedEffect(window, dark) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    var hasSession by remember { mutableStateOf(cookieJar.hasCookies()) }
    var accountRevision by remember { mutableStateOf(0) }
    val savedAccounts = remember(accountRevision) { cookieJar.accounts() }
    val activeAccountId = remember(accountRevision) { cookieJar.activeAccountId() }
    val httpClient = remember { OkHttpClient.Builder().eventListenerFactory { HvNetworkTiming(it.request()) }.build() }
    val pageCache = remember(activeAccountId, site) {
        val cacheKey = MessageDigest.getInstance("SHA-256")
            .digest("${activeAccountId.orEmpty()}|${site.name}".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        Cache(File(context.cacheDir, "hv-pages/$cacheKey"), 32L * 1024L * 1024L)
    }
    val client = remember(cookieJar, site, activeAccountId) {
        HentaiVerseClient(cookieJar, site, httpClient,
            scriptCache = HvScriptCaches.forAccount(File(context.cacheDir, "hv-scripts"), activeAccountId),
            pageCache = pageCache)
    }
    var showSettings by rememberSaveable { mutableStateOf(!hasSession && savedAccounts.isNotEmpty()) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    var showLogin by rememberSaveable { mutableStateOf(!hasSession && !showSettings) }
    var showWebLogin by rememberSaveable { mutableStateOf(false) }
    var loginBusy by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    val loginClient = remember(cookieJar, site) { LoginSessionClient(cookieJar, site) }
    var world by rememberSaveable { mutableStateOf(HvWorld.Persistent) }
    var route by rememberSaveable { mutableStateOf(HvRoute.Character) }
    var battleSessionActive by rememberSaveable(world) { mutableStateOf(false) }
    var battleReturnUrl by rememberSaveable(world) { mutableStateOf<String?>(null) }
    var battleReturnRoute by rememberSaveable(world) { mutableStateOf<HvRoute?>(null) }
    var currentUrl by rememberSaveable(world, site) { mutableStateOf(route.url(world, site).toString()) }
    var nativeBackStack by rememberSaveable(world, route, site) { mutableStateOf(listOf<String>()) }
    var requestState by remember { mutableStateOf<RequestState>(RequestState.Idle) }
    val pageSnapshots = remember(world, site, activeAccountId, hasSession) { PageSnapshotCache() }
    // Save only the small, read-only observation; never persist raw account pages.
    var loadout by key(world, site, activeAccountId, hasSession) {
        rememberSaveable { mutableStateOf<BattleLoadout?>(null) }
    }
    var observedPersona by key(world, site, activeAccountId, hasSession) {
        rememberSaveable { mutableStateOf<String?>(null) }
    }
    var displayedRoute by remember(world, site, activeAccountId) { mutableStateOf(route) }
    var displayedUrl by remember(world, site, activeAccountId) { mutableStateOf(currentUrl) }
    var retryNonce by remember { mutableStateOf(0) }
    var pageLoading by remember { mutableStateOf(false) }
    var pageSource by remember { mutableStateOf<LoadedPageSource?>(null) }
    var responseSerial by remember { mutableStateOf(0) }
    var loadSerial by remember { mutableStateOf(0) }
    var pendingForwardUrl by remember { mutableStateOf<String?>(null) }
    var characterActionBusy by remember { mutableStateOf(false) }
    var challengeBusy by remember { mutableStateOf(false) }
    var characterActionError by remember { mutableStateOf<String?>(null) }
    var characterRevision by remember { mutableStateOf(0) }
    var navigation by remember(world) { mutableStateOf<HvNavigation?>(null) }
    var staminaBusy by remember { mutableStateOf(false) }
    var staminaMessage by remember { mutableStateOf<String?>(null) }
    var staminaNeedsRefresh by remember { mutableStateOf(false) }
    var nativeBusy by remember { mutableStateOf(false) }
    var nativeError by remember { mutableStateOf<String?>(null) }
    var nativeNeedsRefresh by remember { mutableStateOf(false) }
    var nativeSubmissionUnconfirmed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun observeEquipment(state: RequestState) {
        if (state is RequestState.CharacterReady) {
            if (observedPersona != null && observedPersona != state.page.personaId) loadout = null
            observedPersona = state.page.personaId
        }
        val page = (state as? RequestState.CharacterSectionReady)?.page ?: return
        if (page.route == HvRoute.Equipment && !page.equipmentSelection) loadout = page.battleLoadout
    }
    suspend fun acceptPage(requestRoute: HvRoute, requestWorld: HvWorld, html: String,
        url: HttpUrl = requestRoute.url(requestWorld, site),
        initialScripts: List<String> = emptyList(), requestSerial: Int = loadSerial): RequestState {
        val responseRoute = HvRoute.entries.firstOrNull {
            url.queryParameterValues("s") == listOf(it.section) &&
                url.queryParameterValues("ss") == listOf(it.subsection) && it.isAvailableIn(requestWorld)
        } ?: requestRoute
        val started = SystemClock.elapsedRealtime()
        val parsed = withContext(Dispatchers.Default) {
            val document = Jsoup.parse(html, url.toString())
            val parsedNavigation = HvNavigationParser.parse(document, requestWorld, responseRoute, url)
            val parsedPage = parseLoadedPage(responseRoute, requestWorld, html, parsedNavigation != null, url, initialScripts, document)
            val scripts = if (parsedPage is RequestState.RiddleReady || initialScripts.isNotEmpty()) emptyList()
                else HvDataScriptRequests.fromPage(document, requestWorld, site, url)
            ParsedPage(document, parsedNavigation, parsedPage, scripts)
        }
        val parsedNavigation = parsed.navigation
        val parsedPage = parsed.state
        if (requestSerial != loadSerial) throw CancellationException("Page superseded")
        observeEquipment(parsedPage)
        if (parsedPage is RequestState.BattleReady || parsedPage is RequestState.RiddleReady) {
            pageSnapshots.clear()
            battleSessionActive = true
        } else if (battleSessionActive) {
            battleSessionActive = false
            battleReturnUrl = null
            battleReturnRoute = null
        }
        route = responseRoute
        displayedRoute = responseRoute
        displayedUrl = url.toString()
        navigation = parsedNavigation
        currentUrl = url.toString()
        nativeError = null
        responseSerial++
        pageSnapshots.put(PageSnapshot(responseRoute, url, parsedNavigation, parsedPage))
        pageSource = if (parsed.scripts.isEmpty()) null
            else LoadedPageSource(responseSerial, responseRoute, requestWorld, html, url, parsedNavigation != null,
                parsed.document, parsed.scripts)
        Log.d("HvLoading", "parsed ${responseRoute.name} in ${SystemClock.elapsedRealtime() - started}ms")
        return parsedPage
    }
    LaunchedEffect(pageSource, client) {
        val source = pageSource ?: return@LaunchedEffect
        try {
            val details = client.fetchSupplementalScripts(source.scripts)
            if (details.scripts.isNotEmpty()) {
                val enriched = withContext(Dispatchers.Default) {
                    parseLoadedPage(source.route, source.world, source.html, source.hasNavigation, source.url, details.scripts, source.document)
                }
                if (pageSource == source) {
                    observeEquipment(enriched)
                    requestState = enriched
                    pageSnapshots.put(PageSnapshot(source.route, source.url, navigation, enriched))
                }
            }
            if (pageSource == source && details.failures.isNotEmpty()) {
                nativeError = context.getString(R.string.app_details_failed, failureMessage(details.failures.first(), context))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (pageSource == source) nativeError = context.getString(R.string.app_details_failed, failureMessage(error, context))
        }
    }
    // Warm up in the lobby, not on the battle-start or turn submission path.
    val inBattleLobby = !battleSessionActive && !pageLoading &&
        (requestState is RequestState.ArenaReady || requestState is RequestState.BattleModeReady)
    LaunchedEffect(client, world, inBattleLobby) {
        if (!hasSession || !inBattleLobby) return@LaunchedEffect
        val serial = loadSerial
        try {
            val observed = client.fetchBattleLoadout(world)
            if (serial == loadSerial && !battleSessionActive) loadout = observed
        } catch (_: IOException) {
            // Optional read: never block a fight or present unverified equipment as current.
            if (serial == loadSerial && !battleSessionActive) loadout = null
        }
    }
    LaunchedEffect(world, site, hasSession, activeAccountId, retryNonce) {
        if (!hasSession) return@LaunchedEffect
        val requestWorld = world
        val requestRoute = route
        val requestedUrl = currentUrl.toHttpUrl()
        val previousUrl = pendingForwardUrl
        loadSerial++
        val requestSerial = loadSerial
        pageSource = null
        characterActionError = null
        nativeError = null
        pageLoading = true
        if (requestState is RequestState.Idle || requestState is RequestState.Failed) requestState = RequestState.Loading
        val started = SystemClock.elapsedRealtime()
        try {
            val response = client.fetchPage(requestWorld, requestedUrl)
            requestState = acceptPage(requestRoute, requestWorld, response.html, response.url, requestSerial = requestSerial)
            if (route != requestRoute) nativeBackStack = emptyList()
            else if (previousUrl != null && previousUrl != response.url.toString()) {
                nativeBackStack = (nativeBackStack + previousUrl).takeLast(32)
            }
            nativeNeedsRefresh = false
            nativeSubmissionUnconfirmed = false
            staminaNeedsRefresh = false
            staminaMessage = null
            characterRevision++
            Log.d("HvLoading", "displayed ${route.name} in ${SystemClock.elapsedRealtime() - started}ms")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (requestSerial != loadSerial) throw CancellationException("Page superseded")
            if (requestState is RequestState.Loading) requestState = RequestState.Failed(failureMessage(error, context))
            else {
                nativeNeedsRefresh = true
                nativeError = failureMessage(error, context)
            }
        } finally {
            if (requestSerial == loadSerial) {
                pendingForwardUrl = null
                pageLoading = false
            }
        }
    }

    fun navigatePage(destination: HvRoute, url: HttpUrl, forwardFrom: String? = null) {
        // A rapid repeated tap can arrive while the same request is already in
        // flight. Reusing that request avoids canceling it and immediately
        // issuing an identical network call.
        if (pageLoading && currentUrl == url.toString()) return
        loadSerial++
        pageSource = null
        pendingForwardUrl = forwardFrom
        pageLoading = true
        nativeError = null
        characterActionError = null
        route = destination
        currentUrl = url.toString()
        val preview = pageSnapshots[url]
        if (preview != null) {
            displayedRoute = preview.route
            displayedUrl = preview.url.toString()
            navigation = preview.navigation
            requestState = preview.state
            Log.d("HvLoading", "preview ${preview.route.name} ready without network wait")
        } else if (requestState is RequestState.Idle || requestState is RequestState.Failed) {
            displayedRoute = destination
            displayedUrl = currentUrl
            requestState = RequestState.Loading
        }
        retryNonce++
    }

    fun executeNativeAction(action: NativeAction) {
        if (pageLoading || nativeBusy || nativeNeedsRefresh || nativeSubmissionUnconfirmed || staminaBusy || characterActionBusy || challengeBusy) return
        if (action.method == "GET") {
            navigatePage(route, world.urlAtSite(action.url, site), currentUrl)
            return
        }
        loadSerial++
        pageSource = null
        pageSnapshots.clear()
        val requestWorld = world
        val requestRoute = route
        if (requestRoute.category != HvSection.Battle && !battleSessionActive) loadout = null
        nativeBusy = true
        nativeError = null
        scope.launch {
            try {
                val response = client.executeNativeAction(requestWorld, action)
                requestState = acceptPage(requestRoute, requestWorld, response.html, response.url)
                if (route != requestRoute) nativeBackStack = emptyList()
                characterRevision++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                nativeNeedsRefresh = action.method == "GET"
                nativeSubmissionUnconfirmed = action.method == "POST"
                nativeError = if (nativeSubmissionUnconfirmed) context.getString(R.string.app_submission_unconfirmed, failureMessage(error, context))
                    else failureMessage(error, context)
            } finally {
                nativeBusy = false
            }
        }
    }

    fun updateCharacter(action: CharacterAction) {
        if (pageLoading || nativeNeedsRefresh || nativeSubmissionUnconfirmed || characterActionBusy) return
        loadSerial++
        pageSource = null
        val before = (requestState as? RequestState.CharacterReady)?.page ?: return
        pageSnapshots.clear()
        val requestWorld = world
        loadout = null
        characterActionBusy = true
        characterActionError = null
        scope.launch {
            try {
                val html = when (action) {
                    is CharacterAction.Switch -> client.submitPersonaForm(requestWorld, action.personaId)
                    is CharacterAction.Rename -> client.submitPersonaForm(requestWorld, before.personaId, rename = action.name)
                    is CharacterAction.Create -> client.submitPersonaForm(requestWorld, before.personaId, create = action.kind)
                    is CharacterAction.Allocate -> client.submitAttributeAllocation(requestWorld, action.deltas)
                }
                val loaded = acceptPage(HvRoute.Character, requestWorld, html)
                if (loaded is RequestState.CharacterReady) {
                    characterActionError = action.verify(before, loaded.page)
                    characterRevision++
                }
                requestState = loaded
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                characterActionError = context.getString(R.string.app_submission_unconfirmed, failureMessage(error, context))
            } finally {
                characterActionBusy = false
            }
        }
    }

    fun restoreStamina() {
        val before = navigation?.stamina ?: return
        val recovery = before.recovery ?: return
        if (pageLoading || staminaBusy || staminaNeedsRefresh || characterActionBusy ||
            requestState is RequestState.Loading || requestState is RequestState.BattleBusy) return
        val requestWorld = world
        val requestRoute = route
        loadSerial++
        pageSource = null
        pageSnapshots.clear()
        staminaBusy = true
        staminaMessage = null
        scope.launch {
            try {
                val response = client.restoreStamina(requestWorld, requestRoute, recovery, currentUrl.toHttpUrl())
                requestState = acceptPage(requestRoute, requestWorld, response.html, response.url)
                characterRevision++
                val after = navigation?.stamina
                staminaMessage = navigation?.messages?.takeIf { it.isNotEmpty() }?.joinToString("\n")
                    ?: if (after != null && after.value != before.value) after.value
                    else context.getString(R.string.app_stamina_unconfirmed)
                staminaNeedsRefresh = after == null || after.value == before.value
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                staminaNeedsRefresh = true
                staminaMessage = context.getString(R.string.app_restorative_unconfirmed, failureMessage(error, context))
            } finally {
                staminaBusy = false
            }
        }
    }

    fun resetSessionPage() {
        observedPersona = null
        loadout = null
        battleSessionActive = false
        battleReturnUrl = null
        battleReturnRoute = null
        loadSerial++
        pageSource = null
        pageSnapshots.clear()
        pendingForwardUrl = null
        pageLoading = false
        requestState = RequestState.Idle
        navigation = null
        currentUrl = route.url(world, site).toString()
        nativeBackStack = emptyList()
        characterActionError = null
        nativeError = null
        nativeNeedsRefresh = false
        nativeSubmissionUnconfirmed = false
        staminaNeedsRefresh = false
        staminaMessage = null
        characterRevision++
        retryNonce++
        accountRevision++
        hasSession = cookieJar.hasCookies()
    }

    fun retryPage() {
        if (pageLoading) return
        loadSerial++
        pageSource = null
        pendingForwardUrl = null
        pageLoading = true
        retryNonce++
    }

    fun continueBattle(continuation: BattleContinuation) {
        if (pageLoading || requestState !is RequestState.BattleReady) return
        continuation.action?.let { action ->
            currentUrl = world.urlAtSite(action.url, site).toString()
            route = requireNotNull(continuation.destination)
            nativeBackStack = emptyList()
        }
        requestState = RequestState.Loading
        retryPage()
    }

    fun leaveBattle() {
        val page = (requestState as? RequestState.BattleReady)?.page ?: return
        if (pageLoading || !page.isComplete || page.continuation != null) return
        val returnRoute = battleReturnRoute
        if (returnRoute != null) route = returnRoute
        currentUrl = battleReturnUrl ?: currentUrl
        nativeBackStack = emptyList()
        requestState = RequestState.Loading
        retryPage()
    }

    fun submitBattleAction(page: BattlePage, action: BattleAction) {
        if (pageLoading || requestState !is RequestState.BattleReady) return
        loadSerial++
        pageSource = null
        val requestWorld = world
        val requestRoute = route
        val requestUrl = currentUrl.toHttpUrl()
        requestState = RequestState.BattleBusy(page)
        scope.launch {
            try {
                val started = SystemClock.elapsedRealtime()
                val response = client.submitBattleAction(
                    world = requestWorld,
                    referer = requestUrl,
                    token = page.token,
                    action = action,
                )
                val received = SystemClock.elapsedRealtime()
                if (response.has("error") || response.has("reload") || response.has("login")) {
                    Log.d("HvLoading", "battle reply=${received - started}ms refresh=true (server)")
                    val refreshed = client.fetchPage(requestWorld, requestUrl)
                    requestState = acceptPage(requestRoute, requestWorld, refreshed.html, refreshed.url)
                } else {
                    val updated = withContext(Dispatchers.Default) {
                        BattlePageParser.parseActionResponse(page, response, requestWorld, requestUrl)
                    }
                    Log.d("HvLoading", "battle reply=${received - started}ms apply=${SystemClock.elapsedRealtime() - received}ms refresh=${updated == null}")
                    if (updated != null) {
                        // Keep the battle session and its current URL intact. The JSON
                        // endpoint already returned the panes that changed; sending the
                        // result through acceptPage would parse the whole page again and
                        // briefly present it as a normal page load.
                        requestState = RequestState.BattleReady(updated)
                        nativeError = null
                    } else {
                        // An unknown successful response cannot safely update the local
                        // battle model. Refresh once so the next action uses authoritative
                        // server state.
                        val refreshed = client.fetchPage(requestWorld, requestUrl)
                        requestState = acceptPage(requestRoute, requestWorld, refreshed.html, refreshed.url)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Keep the last battle visible when the action result is uncertain. The
                // user can refresh from the inline error without losing the encounter.
                requestState = RequestState.BattleReady(page)
                nativeError = failureMessage(error, context)
            }
        }
    }

    fun authenticate(action: suspend () -> Unit) {
        if (loginBusy) return
        loginBusy = true
        loginError = null
        scope.launch {
            try {
                action()
                resetSessionPage()
                hasSession = true
                showWebLogin = false
                showLogin = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                loginError = if (error is LoginException) error.message else failureMessage(error, context)
            } finally {
                loginBusy = false
            }
        }
    }
    BackHandler(enabled = showLogin && (loginBusy || hasSession || showSettings) && !showWebLogin) {
        if (!loginBusy) showLogin = false
    }
    fun openSettings() {
        settingsError = null
        accountRevision++
        showSettings = true
    }
    fun closeSettings() {
        showSettings = false
        if (!hasSession) showLogin = true
    }
    BackHandler(enabled = showSettings && !showLogin) { closeSettings() }

    val colorScheme = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && dark ->
            dynamicDarkColorScheme(context)
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
    ) {
        if (showLogin) {
            if (showWebLogin) {
                ForumLoginScreen(
                    busy = loginBusy,
                    error = loginError,
                    loginUrl = ForumLoginProtocol.LOGIN_URL,
                    onBack = { if (!loginBusy) { showWebLogin = false; loginError = null } },
                    onCookiesCaptured = { header ->
                        val requestWorld = world
                        authenticate { loginClient.importCookies(header, requestWorld) }
                    },
                )
            } else {
                LoginScreen(
                    busy = loginBusy,
                    error = loginError,
                    canGoBack = hasSession || showSettings,
                    onClearError = { loginError = null },
                    onBack = { if (!loginBusy) showLogin = false },
                    onCredentials = { username, password ->
                        val requestWorld = world
                        authenticate { loginClient.signIn(username, password, requestWorld) }
                    },
                    onCookieImport = { header ->
                        val requestWorld = world
                        authenticate { loginClient.importCookies(header, requestWorld) }
                    },
                    onWebLogin = { loginError = null; showWebLogin = true },
                )
            }
        } else if (showSettings) {
            AppSettingsScreen(
                siteIsAlt = site == HvSite.Alt,
                onSiteChanged = { useAlt ->
                    site = if (useAlt) HvSite.Alt else HvSite.Main
                    preferences.site = site
                    loadSerial++
                    pageSource = null
                    pageSnapshots.clear()
                    pendingForwardUrl = null
                    requestState = RequestState.Idle
                    navigation = null
                },
                language = language,
                onLanguageChanged = {
                    // The selected mode can change without changing the effective locale.
                    language = it
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it.tag))
                },
                themeMode = theme,
                onThemeChanged = { theme = it; preferences.theme = it },
                dynamicColorEnabled = dynamicColor,
                onDynamicColorChanged = { dynamicColor = it; preferences.dynamicColor = it },
                accounts = savedAccounts.map { AppSettingsAccount(it.memberId, it.isActive, it.hasUsableSession) },
                onSelectAccount = { id ->
                    if (cookieJar.switchAccount(id)) {
                        settingsError = null
                        resetSessionPage()
                    } else {
                        loginError = context.getString(R.string.app_account_needs_login)
                        showWebLogin = false
                        showLogin = true
                    }
                },
                onRemoveAccount = { id ->
                    val removingActive = cookieJar.activeAccountId() == id
                    if (cookieJar.removeAccount(id)) {
                        if (removingActive) resetSessionPage() else accountRevision++
                    } else settingsError = context.getString(R.string.app_account_remove_failed)
                },
                onAddAccount = { loginError = null; showWebLogin = false; showLogin = true },
                onSignOut = {
                    if (cookieJar.logout()) resetSessionPage()
                    else settingsError = context.getString(R.string.app_account_logout_failed)
                },
                onBack = ::closeSettings,
                error = settingsError,
                onClearError = { settingsError = null },
            )
        } else {
            ProvideGameTextScope(displayedUrl,
                battle = requestState is RequestState.BattleReady || requestState is RequestState.BattleBusy) {
                if (battleSessionActive) BattleSessionScreen(
                    state = requestState,
                    loadout = loadout,
                    onSettings = ::openSettings,
                    settingsEnabled = !pageLoading && !nativeBusy && !challengeBusy &&
                        requestState !is RequestState.BattleBusy && requestState !is RequestState.Loading,
                    loading = pageLoading,
                    nativeBusy = nativeBusy,
                    error = nativeError,
                    needsRefresh = nativeNeedsRefresh || nativeSubmissionUnconfirmed,
                    responseRevision = characterRevision,
                    loadImage = { image -> client.fetchImage(world, currentUrl.toHttpUrl(), image) },
                    onRetry = ::retryPage,
                    onRiddleAction = ::executeNativeAction,
                    onSubmitBattleAction = ::submitBattleAction,
                    onContinue = ::continueBattle,
                    onLeaveBattle = ::leaveBattle,
                ) else GameShell(
                    world = world,
                    route = displayedRoute,
                    selectedRoute = route,
                    state = requestState,
                    pageLoading = pageLoading,
                    navigation = navigation,
                    nativeBusy = nativeBusy,
                    nativeError = nativeError,
                    nativeNeedsRefresh = nativeNeedsRefresh,
                    nativeSubmissionUnconfirmed = nativeSubmissionUnconfirmed,
                    onNativeAction = ::executeNativeAction,
                    hasNativeBack = nativeBackStack.isNotEmpty(),
                    onNativeBack = {
                        nativeBackStack.lastOrNull()?.let { previous ->
                            nativeBackStack = nativeBackStack.dropLast(1)
                            navigatePage(route, previous.toHttpUrl())
                        }
                    },
                    staminaBusy = staminaBusy,
                    staminaMessage = staminaMessage,
                    staminaNeedsRefresh = staminaNeedsRefresh,
                    onRestoreStamina = ::restoreStamina,
                    onWorldSelected = {
                        loadSerial++
                        pageSource = null
                        pendingForwardUrl = null
                        requestState = RequestState.Loading
                        world = it
                        if (!route.isAvailableIn(it)) route = route.category.defaultRoute
                    },
                    onRouteSelected = {
                        nativeBackStack = emptyList()
                        navigatePage(it, it.url(world, site))
                    },
                    onSettings = ::openSettings,
                    onRetry = ::retryPage,
                    characterActionBusy = characterActionBusy,
                    challengeBusy = challengeBusy,
                    characterActionError = characterActionError,
                    characterRevision = characterRevision,
                    onPersonaSelected = { id ->
                        updateCharacter(CharacterAction.Switch(id))
                    },
                    onPersonaRenamed = { name ->
                        updateCharacter(CharacterAction.Rename(name))
                    },
                    onPersonaCreated = { kind ->
                        updateCharacter(CharacterAction.Create(kind))
                    },
                    onApplyAttributes = { deltas ->
                        updateCharacter(CharacterAction.Allocate(deltas.toMap()))
                    },
                    onStartChallenge = { challenge ->
                        val arena = (requestState as? RequestState.ArenaReady)?.page ?: return@GameShell
                        val challengeId = challenge.id ?: return@GameShell
                        if (pageLoading || nativeNeedsRefresh || nativeSubmissionUnconfirmed || challengeBusy) return@GameShell
                        loadSerial++
                        pageSource = null
                        pageSnapshots.clear()
                        val requestWorld = world
                        val requestRoute = route
                        battleReturnUrl = currentUrl
                        battleReturnRoute = route
                        battleSessionActive = true
                        challengeBusy = true
                        scope.launch {
                            requestState = RequestState.Loading
                            try {
                                val html = client.startArenaChallenge(requestRoute, requestWorld, challengeId, arena.postToken)
                                requestState = acceptPage(requestRoute, requestWorld, html)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                requestState = RequestState.Failed(failureMessage(error, context))
                            } finally {
                                challengeBusy = false
                            }
                        }
                    },
                    onStartBattleMode = { action ->
                        if (pageLoading || nativeBusy || nativeNeedsRefresh || nativeSubmissionUnconfirmed || staminaBusy || characterActionBusy || challengeBusy) {
                            return@GameShell
                        }
                        battleReturnUrl = currentUrl
                        battleReturnRoute = route
                        battleSessionActive = true
                        requestState = RequestState.Loading
                        executeNativeAction(action)
                    },
                )
            }
        }
    }
}

private data class LoadedPageSource(
    val serial: Int,
    val route: HvRoute,
    val world: HvWorld,
    val html: String,
    val url: HttpUrl,
    val hasNavigation: Boolean,
    val document: Document,
    val scripts: List<Request>,
)

private data class ParsedPage(
    val document: Document,
    val navigation: HvNavigation?,
    val state: RequestState,
    val scripts: List<Request>,
)

internal sealed interface RequestState {
    data object Idle : RequestState
    data object Loading : RequestState
    data class Failed(val message: String) : RequestState
    data class CharacterReady(val page: CharacterPage) : RequestState
    data class ArenaReady(val page: com.mitahv.mobile.model.ArenaPage) : RequestState
    data class BattleReady(val page: BattlePage) : RequestState
    data class BattleBusy(val page: BattlePage) : RequestState
    data class RiddleReady(val page: RiddlePage) : RequestState
    data class CharacterSectionReady(val page: CharacterSectionsPage) : RequestState
    data class BazaarSectionReady(val page: BazaarSectionsPage) : RequestState
    data class BattleModeReady(val page: BattleModesPage) : RequestState
}

internal fun parseLoadedPage(route: HvRoute, world: HvWorld, html: String, hasNavigation: Boolean,
    currentUrl: HttpUrl, supplementalScripts: List<String>, document: Document = Jsoup.parse(html, currentUrl.toString())): RequestState = try {
    when {
        RiddlePageParser.isRiddlePage(document) -> RequestState.RiddleReady(RiddlePageParser.parse(document, world, currentUrl))
        BattlePageParser.isBattlePage(document) -> RequestState.BattleReady(
            BattlePageParser.parseBattle(document, supplementalScripts, world, currentUrl))
        route == HvRoute.Character -> RequestState.CharacterReady(CharacterPageParser.parse(document))
        route == HvRoute.Battle -> RequestState.ArenaReady(BattlePageParser.parseArena(document))
        hasNavigation && route.category == HvSection.Character ->
            RequestState.CharacterSectionReady(CharacterSectionsParser.parse(document, route, world, currentUrl, supplementalScripts))
        hasNavigation && route.category == HvSection.Bazaar ->
            RequestState.BazaarSectionReady(BazaarSectionsParser.parse(document, route, world, currentUrl, supplementalScripts))
        hasNavigation && route.category == HvSection.Battle ->
            RequestState.BattleModeReady(BattleModesParser.parse(document, route, world, currentUrl, supplementalScripts))
        else -> throw PageParseException()
    }
} catch (_: IllegalArgumentException) {
    throw PageParseException()
}

private class PageParseException : IOException()

private fun failureMessage(error: Exception, context: Context): String = when (error) {
    is com.mitahv.mobile.data.HvHttpException -> context.getString(R.string.app_http_error, error.statusCode)
    is org.jsoup.select.Selector.SelectorParseException ->
        context.getString(R.string.app_selector_error, error.message.orEmpty().take(120))
    is UnknownHostException -> context.getString(R.string.app_dns_error)
    is InterruptedIOException -> context.getString(R.string.app_timeout_error)
    is SSLException -> context.getString(R.string.app_ssl_error)
    is PageParseException -> context.getString(R.string.app_parse_error)
    is IOException -> context.getString(R.string.app_network_error, error.javaClass.simpleName)
    else -> context.getString(R.string.app_page_error, error.javaClass.simpleName)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GameShell(
    world: HvWorld,
    route: HvRoute,
    selectedRoute: HvRoute,
    state: RequestState,
    pageLoading: Boolean,
    navigation: HvNavigation?,
    nativeBusy: Boolean,
    nativeError: String?,
    nativeNeedsRefresh: Boolean,
    nativeSubmissionUnconfirmed: Boolean,
    onNativeAction: (NativeAction) -> Unit,
    hasNativeBack: Boolean,
    onNativeBack: () -> Unit,
    staminaBusy: Boolean,
    staminaMessage: String?,
    staminaNeedsRefresh: Boolean,
    onRestoreStamina: () -> Unit,
    onWorldSelected: (HvWorld) -> Unit,
    onRouteSelected: (HvRoute) -> Unit,
    onSettings: () -> Unit,
    onRetry: () -> Unit,
    characterActionBusy: Boolean,
    challengeBusy: Boolean,
    characterActionError: String?,
    characterRevision: Int,
    onPersonaSelected: (String) -> Unit,
    onPersonaRenamed: (String) -> Unit,
    onPersonaCreated: (String) -> Unit,
    onApplyAttributes: (Map<String, Int>) -> Unit,
    onStartChallenge: (ArenaChallenge) -> Unit,
    onStartBattleMode: (NativeAction) -> Unit,
) {
    var characterHasDraft by remember(world, route, characterRevision) { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingNativeAction by remember(world, route) { mutableStateOf<NativeAction?>(null) }
    var menuSection by rememberSaveable(world) { mutableStateOf<HvSection?>(null) }
    var staminaOpen by rememberSaveable(world, route) { mutableStateOf(false) }
    var marketOverflowOpen by rememberSaveable(world, route) { mutableStateOf(false) }
    val compactMarketHeader = route == HvRoute.Market
    val mailMode = (state as? RequestState.BazaarSectionReady)?.page?.mail?.mode
    val mailSubpage = mailMode != null && mailMode != com.mitahv.mobile.model.MailViewMode.List
    val armoryMode = (state as? RequestState.BazaarSectionReady)?.page?.armory?.mode
    val focusedSubpage = mailSubpage || armoryMode != null && armoryMode != com.mitahv.mobile.model.ArmoryViewMode.List
    // A failed GET only blocks actions that depend on the stale page. Keep the
    // shell navigable so the user can switch to another page or retry instead
    // of getting stuck behind the error state.
    val navigationEnabled = !characterActionBusy && !challengeBusy && !staminaBusy &&
        !nativeBusy
    val contentEnabled = navigationEnabled && !pageLoading && !nativeNeedsRefresh && !nativeSubmissionUnconfirmed
    val stamina = navigation?.stamina
    fun pages(section: HvSection) = navigation?.routes?.filter { it.category == section }
        ?: HvRoute.pages(section, world)
    fun navigate(action: () -> Unit) {
        if (!navigationEnabled) return
        if (characterHasDraft) pendingNavigation = action else action()
    }
    fun nativeAction(action: NativeAction) {
        if (!contentEnabled) return
        if (action.method == "GET") navigate { onNativeAction(action) }
        else if (action.confirmation != null) pendingNativeAction = action else onNativeAction(action)
    }
    pendingNativeAction?.let { action ->
        var checked by remember(action) { mutableStateOf<Set<Int>>(emptySet()) }
        AlertDialog(
            onDismissRequest = { pendingNativeAction = null },
            title = { Text(messageText(action.label)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(messageText(action.confirmation.orEmpty()))
                    action.confirmationChecks.forEachIndexed { index, label ->
                        Row(Modifier.fillMaxWidth().toggleable(index in checked, role = Role.Checkbox) {
                            checked = if (it) checked + index else checked - index
                        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = index in checked, onCheckedChange = null)
                            Text(messageText(label), Modifier.weight(1f).padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                pendingNativeAction = null
                onNativeAction(action)
            }, enabled = checked.size == action.confirmationChecks.size) { Text(stringResource(R.string.app_confirm)) } },
            dismissButton = { TextButton(onClick = { pendingNativeAction = null }) { Text(stringResource(R.string.app_cancel)) } },
        )
    }
    BackHandler(enabled = !navigationEnabled || pageLoading) { }
    BackHandler(enabled = navigationEnabled && !pageLoading && (hasNativeBack || route != route.category.defaultRoute) &&
        menuSection == null && !staminaOpen) {
        navigate { if (hasNativeBack) onNativeBack() else onRouteSelected(route.category.defaultRoute) }
    }
    pendingNavigation?.let { action ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingNavigation = null },
            title = { Text(if (route == HvRoute.Character) stringResource(R.string.app_discard_attributes) else stringResource(R.string.app_discard_changes)) },
            confirmButton = { TextButton(onClick = {
                pendingNavigation = null
                characterHasDraft = false
                action()
            }) { Text(stringResource(R.string.app_discard)) } },
            dismissButton = { TextButton(onClick = { pendingNavigation = null }) { Text(stringResource(R.string.app_keep_editing)) } },
        )
    }
    menuSection?.let { section ->
        SectionNavigationSheet(
            section = section,
            routes = pages(section),
            selectedRoute = selectedRoute,
            onRouteSelected = { selected ->
                menuSection = null
                if (selected != selectedRoute || hasNativeBack) navigate { onRouteSelected(selected) }
            },
            onDismiss = { menuSection = null },
        )
    }
    if (staminaOpen && stamina != null) AlertDialog(
        onDismissRequest = { if (!staminaBusy) staminaOpen = false },
        title = { Text(gameText(stamina.value)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (stamina.description.isNotBlank()) Text(gameText(stamina.description))
                if (staminaBusy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                if (staminaMessage != null) Text(messageText(staminaMessage))
                else if (stamina.recovery == null) Text(stringResource(R.string.app_no_restorative))
            }
        },
        confirmButton = {
            if (staminaNeedsRefresh) TextButton(onClick = {
                staminaOpen = false
                navigate(onRetry)
            }, enabled = !staminaBusy) { Text(stringResource(R.string.app_refresh)) }
            else if (stamina.recovery != null) TextButton(
                onClick = {
                    staminaOpen = false
                    navigate { staminaOpen = true; onRestoreStamina() }
                },
                enabled = navigationEnabled && !pageLoading && state !is RequestState.Loading && state !is RequestState.Failed,
            ) { Text(stringResource(R.string.app_restorative)) }
        },
        dismissButton = { TextButton(onClick = { staminaOpen = false }, enabled = !staminaBusy) { Text(stringResource(R.string.app_close)) } },
    )
    Scaffold(
        topBar = {
            if (!focusedSubpage) TopAppBar(
                title = {
                    SectionPageTitle(route)
                },
                actions = {
                    if (compactMarketHeader) {
                        Box(Modifier.widthIn(max = 132.dp)) {
                            CompactWorldButton(world, navigationEnabled) { item -> navigate { onWorldSelected(item) } }
                        }
                    } else {
                        IconButton(onClick = { navigate(onRetry) },
                            enabled = navigationEnabled && !pageLoading && state !is RequestState.Loading,
                        ) { Icon(Icons.Filled.Refresh, stringResource(R.string.app_refresh_page)) }
                    }
                    IconButton(onClick = { navigate(onSettings) },
                        enabled = navigationEnabled && !pageLoading && state !is RequestState.Loading) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.app_settings))
                    }
                    if (compactMarketHeader) Box {
                        IconButton(onClick = { marketOverflowOpen = true }, enabled = navigationEnabled) {
                            Icon(Icons.Filled.MoreVert, stringResource(R.string.game_ui_more_actions))
                        }
                        DropdownMenu(
                            expanded = marketOverflowOpen && navigationEnabled,
                            onDismissRequest = { marketOverflowOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.app_refresh_page)) },
                                leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                enabled = !pageLoading && state !is RequestState.Loading,
                                onClick = { marketOverflowOpen = false; navigate(onRetry) },
                            )
                            if (stamina != null) DropdownMenuItem(
                                text = { Text(stringResource(R.string.game_ui_stamina_value, stamina.value.substringAfter(':').trim())) },
                                leadingIcon = { Icon(Icons.Filled.Bolt, null) },
                                enabled = !pageLoading && state !is RequestState.Loading && state !is RequestState.Failed,
                                onClick = { marketOverflowOpen = false; staminaOpen = true },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!focusedSubpage) NavigationBar {
                HvSection.entries.forEach { item ->
                    val label = gameText(item.label)
                    val menuLabel = stringResource(R.string.game_ui_select_section, label)
                    NavigationBarItem(
                        modifier = Modifier.semantics { contentDescription = menuLabel },
                        selected = selectedRoute.category == item,
                        onClick = { menuSection = item },
                        enabled = navigationEnabled && pages(item).isNotEmpty(),
                        icon = {
                            Icon(
                                imageVector = when (item) {
                                    HvSection.Character -> Icons.Filled.Person
                                    HvSection.Bazaar -> Icons.Filled.Storefront
                                    HvSection.Battle -> Icons.Filled.Bolt
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (!compactMarketHeader && !focusedSubpage) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                CompactWorldButton(world, navigationEnabled) { item -> navigate { onWorldSelected(item) } }
                Spacer(Modifier.weight(1f))
                if (stamina != null) StaminaButton(stamina.value.substringAfter(':').trim(),
                    enabled = navigationEnabled && !pageLoading && state !is RequestState.Loading && state !is RequestState.Failed,
                    onClick = { staminaOpen = true })
            }
            Box(Modifier.fillMaxWidth().height(4.dp)) {
                if (nativeBusy || pageLoading || characterActionBusy || state is RequestState.BattleBusy)
                    androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (nativeError != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(messageText(nativeError), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { navigate(onRetry) },
                    enabled = !pageLoading && state !is RequestState.Loading) { Text(stringResource(R.string.app_refresh)) }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (state) {
                    RequestState.Loading -> androidx.compose.material3.LoadingIndicator(
                        modifier = Modifier.size(44.dp),
                    )
                    is RequestState.Failed -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(messageText(state.message))
                        Button(onClick = onRetry) { Text(stringResource(R.string.app_retry)) }
                    }
                    is RequestState.CharacterReady -> CharacterScreen(
                        page = state.page,
                        worldKey = world.name,
                        busy = !contentEnabled,
                        actionError = characterActionError,
                        revision = characterRevision,
                        onRefresh = onRetry,
                        onDraftChanged = { characterHasDraft = it },
                        onPersonaSelected = onPersonaSelected,
                        onRenamePersona = onPersonaRenamed,
                        onCreatePersona = onPersonaCreated,
                        onApplyAttributes = onApplyAttributes,
                    )
                    is RequestState.ArenaReady -> ArenaScreen(
                        page = state.page,
                        busy = !contentEnabled,
                        onStartChallenge = onStartChallenge,
                    )
                    is RequestState.BattleReady, is RequestState.BattleBusy, is RequestState.RiddleReady -> Unit
                    is RequestState.CharacterSectionReady -> CharacterSectionsScreen(state.page,
                        busy = !contentEnabled, onAction = ::nativeAction,
                        onDraftChanged = { characterHasDraft = it }, responseRevision = characterRevision, loading = pageLoading)
                    is RequestState.BazaarSectionReady -> {
                        if (state.page.route == HvRoute.Bazaar) ItemShopScreen(state.page,
                            busy = !contentEnabled, onAction = ::nativeAction, responseRevision = characterRevision)
                        else if (state.page.route == HvRoute.MoogleMail) MailScreen(state.page,
                            busy = !contentEnabled, onAction = ::nativeAction, responseRevision = characterRevision,
                            onBack = { fallback -> navigate { if (hasNativeBack) onNativeBack() else onNativeAction(fallback) } },
                            onDraftChanged = { characterHasDraft = it })
                        else if (state.page.route == HvRoute.Armory) ArmoryScreen(state.page,
                            busy = !contentEnabled, onAction = ::nativeAction, responseRevision = characterRevision,
                            onBack = { fallback -> navigate { if (hasNativeBack) onNativeBack() else onNativeAction(fallback) } })
                        else if (state.page.route == HvRoute.Market) MarketScreen(state.page,
                            busy = !contentEnabled, onAction = ::nativeAction,
                            onDraftChanged = { characterHasDraft = it }, responseRevision = characterRevision)
                        else BazaarSectionsScreen(state.page,
                            busy = !contentEnabled, onAction = ::nativeAction,
                            onDraftChanged = { characterHasDraft = it }, responseRevision = characterRevision)
                    }
                    is RequestState.BattleModeReady -> BattleModesScreen(state.page,
                        busy = !contentEnabled, onAction = ::nativeAction, onStartBattle = onStartBattleMode,
                        responseRevision = characterRevision)
                    RequestState.Idle -> Unit
                }
            }
        }
    }
}


