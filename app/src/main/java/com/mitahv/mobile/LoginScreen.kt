package com.mitahv.mobile

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mitahv.mobile.i18n.messageText

@Composable
internal fun LoginScreen(
    busy: Boolean,
    error: String?,
    onClearError: () -> Unit,
    onCredentials: (String, String) -> Unit,
    onCookieImport: (String) -> Unit,
    onWebLogin: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    // Secrets stay in memory and are never written to saved instance state.
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var cookieDialog by remember { mutableStateOf(false) }
    var cookieText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val passwordFocus = remember { FocusRequester() }
    val canSubmit = !busy && username.isNotBlank() && password.isNotEmpty()

    fun submit() {
        if (!canSubmit) return
        focusManager.clearFocus()
        onCredentials(username.trim(), password)
    }
    fun closeCookieDialog() {
        if (!busy) {
            cookieDialog = false
            cookieText = ""
            onClearError()
        }
    }

    BackHandler(enabled = busy) { }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_login)) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Obelisk", style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(bottom = 16.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; onClearError() },
                    label = { Text(stringResource(R.string.app_username)) },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; onClearError() },
                    label = { Text(stringResource(R.string.app_password)) },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !busy) {
                            Icon(if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                if (passwordVisible) stringResource(R.string.app_hide_password) else stringResource(R.string.app_show_password))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus)
                        .semantics { contentType = ContentType.Password },
                )
                if (error != null && !cookieDialog) LoginError(error)
                Button(onClick = { submit() }, enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.app_login))
                }
                OutlinedButton(onClick = { focusManager.clearFocus(); onClearError(); cookieDialog = true }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(Icons.Filled.Cookie, null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.app_cookie_login))
                }
                OutlinedButton(onClick = { focusManager.clearFocus(); onWebLogin() }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(Icons.Filled.Language, null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.app_web_login))
                }
            }
        }
    }
    if (cookieDialog) AlertDialog(
        onDismissRequest = { closeCookieDialog() },
        title = { Text(stringResource(R.string.app_cookie_login)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = cookieText,
                    onValueChange = { cookieText = it; onClearError() },
                    label = { Text("Cookie") },
                    minLines = 3,
                    maxLines = 6,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error != null) LoginError(error)
            }
        },
        confirmButton = {
            Button(onClick = { focusManager.clearFocus(); onCookieImport(cookieText) },
                enabled = !busy && cookieText.isNotBlank()) { Text(stringResource(R.string.app_login)) }
        },
        dismissButton = { TextButton(onClick = { closeCookieDialog() }, enabled = !busy) { Text(stringResource(R.string.app_cancel)) } },
    )
}

@Composable
internal fun ForumLoginScreen(
    busy: Boolean,
    error: String?,
    loginUrl: String,
    onBack: () -> Unit,
    onCookiesCaptured: (String) -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val resources = LocalResources.current
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var lastSubmittedCookies by remember { mutableStateOf<String?>(null) }
    val initialCookies = remember { forumAuthCookieHeader() }
    val currentBusy by rememberUpdatedState(busy)
    val captureCallback by rememberUpdatedState(onCookiesCaptured)

    fun captureCookies(automatic: Boolean) {
        if (currentBusy) return
        val header = forumAuthCookieHeader()
        if (header == null) {
            if (!automatic) pageError = resources.getString(R.string.app_forum_login_first)
            return
        }
        if (automatic && (header == initialCookies || header == lastSubmittedCookies)) return
        lastSubmittedCookies = header
        pageError = null
        CookieManager.getInstance().flush()
        captureCallback(header)
    }

    fun navigateBack() {
        if (busy) return
        val view = webView
        if (view?.canGoBack() == true) view.goBack() else onBack()
    }

    BackHandler(enabled = true) { navigateBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_web_login)) },
                navigationIcon = {
                    IconButton(onClick = { navigateBack() }, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.app_back))
                    }
                },
                actions = {
                    IconButton(onClick = { pageError = null; webView?.reload() }, enabled = !busy) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.app_refresh_login))
                    }
                    TextButton(onClick = { captureCookies(automatic = false) }, enabled = !busy) {
                        Text(stringResource(R.string.app_finish_login))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            (error ?: pageError)?.let { message ->
                LoginError(message, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val uri = request.url
                                if (uri.scheme != "https") return true
                                // Forum verification may load a third-party HTTPS challenge frame.
                                return request.isForMainFrame && (currentBusy || !isForumLoginHost(uri))
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                loading = true
                                pageError = null
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                loading = false
                                val uri = url?.let(Uri::parse)
                                if (pageError == null && uri?.scheme == "https" && isForumLoginHost(uri)) {
                                    captureCookies(automatic = true)
                                }
                            }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) {
                                    loading = false
                                    pageError = resources.getString(R.string.app_forum_load_failed)
                                }
                            }

                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                if (request.isForMainFrame) {
                                    loading = false
                                    pageError = resources.getString(R.string.app_forum_http_error, response.statusCode)
                                }
                            }
                        }
                        webView = this
                        loadUrl(loginUrl)
                    }
                },
                update = { it.isEnabled = !busy },
                onRelease = { view ->
                    view.stopLoading()
                    view.webViewClient = WebViewClient()
                    view.destroy()
                    if (webView === view) webView = null
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LoginError(message: String, modifier: Modifier = Modifier) {
    val localized = messageText(message)
    Text(localized, modifier.semantics { error(localized) }, color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium)
}

private fun isForumLoginHost(uri: Uri): Boolean =
    uri.host?.lowercase() in setOf("forums.e-hentai.org", "e-hentai.org", "www.e-hentai.org")

private fun forumAuthCookieHeader(): String? {
    val manager = CookieManager.getInstance()
    for (url in listOf("https://forums.e-hentai.org/", "https://e-hentai.org/")) {
        val values = manager.getCookie(url).orEmpty().split(';').mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) null else part.substring(0, separator).trim() to part.substring(separator + 1).trim()
        }.toMap()
        val memberId = values["ipb_member_id"]?.takeIf { it.toLongOrNull()?.let { id -> id > 0 } == true }
        val passHash = values["ipb_pass_hash"]?.takeIf { it.isNotBlank() }
        if (memberId != null && passHash != null) return "ipb_member_id=$memberId; ipb_pass_hash=$passHash"
    }
    return null
}
