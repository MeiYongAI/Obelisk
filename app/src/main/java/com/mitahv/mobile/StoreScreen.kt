package com.mitahv.mobile

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.JsonParser
import com.mitahv.mobile.data.StoreLibrary
import com.mitahv.mobile.data.StoreTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

private const val storeHome = "https://appassets.androidplatform.net/store/index.html"
private val storeFiles = setOf("index.html", "android.css", "manager.css", "languages.js", "store.js",
    "ratings.js", "downloads.js", "comments.js", "android.js")

private class StoreAssets(private val context: Context) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
        val name = request.url.lastPathSegment
        if (request.method == "GET" && name in storeFiles && request.url.toString() ==
            "https://appassets.androidplatform.net/store/$name" && (!request.isForMainFrame || name == "index.html")) {
            val type = when (name!!.substringAfterLast('.')) {
                "js" -> "application/javascript"
                "css" -> "text/css"
                else -> "text/html"
            }
            return WebResourceResponse(type, "UTF-8", context.assets.open("store/$name"))
        }
        return WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(byteArrayOf()))
    }
}

/** The only document this view can load is the bundled store. The native HTTP client owns all remote calls. */
private class StoreBridge(
    private val view: WebView, private val store: StoreLibrary, private val language: String, private val shareId: String?,
    darkTheme: Boolean,
    private val download: (String, String) -> String,
) {
    private val transport = StoreTransport()
    private val preferences = view.context.getSharedPreferences("jpx-store-" + store.storageNamespace, Context.MODE_PRIVATE)
    private val requests = ConcurrentHashMap<String, Call>()
    @Volatile private var closed = false
    @Volatile private var dark = darkTheme

    fun updateTheme(value: Boolean) {
        if (dark == value) return
        dark = value
        val theme = if (value) "dark" else "light"
        view.evaluateJavascript("document.documentElement.dataset.jpxTheme = '$theme'", null)
    }

    @JavascriptInterface fun call(method: String, payload: String): String = try {
        check(!closed)
        val args = JSONObject(payload)
        fun objectAt(name: String) = JsonParser.parseString(args.getJSONObject(name).toString()).asJsonObject
        fun optional(name: String) = if (args.isNull(name)) null else args.getString(name)
        fun key() = args.getString("key").also { require(it.startsWith("jpx-config-") && it.length <= 160) }
        val value: Any? = when (method) {
            "init" -> JSONObject().put("language", language).put("shareId", shareId ?: JSONObject.NULL).put("darkTheme", dark)
            "listProfiles" -> JSONArray(store.list().toString())
            "savedVersions" -> JSONObject(store.savedVersions().toString())
            "getProfile" -> store.get(args.getString("id"))?.let { JSONObject(it.toString()) }
            "findSource" -> store.findSource(objectAt("source"))?.let { JSONObject(it.toString()) }
            "removeCopy" -> { store.remove(args.getString("id"), args.getString("revision")); true }
            "saveConfig" -> {
                val source = objectAt("remote").getAsJsonObject("source")
                val saved = store.save(args.getJSONObject("envelope").toString(), source, args.getString("choice"),
                    optional("expectedId"), optional("expectedRevision"))
                JSONObject(saved.toString())
            }
            "gmGet" -> preferences.getString(key(), null)?.let { JSONTokener(it).nextValue() }
            "gmSet" -> {
                val json = JSONArray().put(args.get("value")).toString()
                require(json.length <= 64 * 1024)
                check(preferences.edit().putString(key(), json.substring(1, json.length - 1)).commit()); true
            }
            "gmDelete" -> preferences.edit().remove(key()).commit()
            "gmKeys" -> JSONArray(preferences.all.keys.toList())
            "openExternal" -> {
                val url = args.getString("url").toHttpUrl()
                require(url.isHttps && url.host == "github.com" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty())
                view.post {
                    if (!closed) runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()))) }
                        .onFailure { Toast.makeText(view.context, R.string.jpx_store_browser_error, Toast.LENGTH_SHORT).show() }
                }; true
            }
            "download" -> {
                val text = args.getString("text")
                require(text.toByteArray().size <= 2 * 1024 * 1024)
                val filename = args.getString("filename").substringAfterLast('/').substringAfterLast('\\').take(100)
                download(filename.ifBlank { "jpx-config.json" }, text)
            }
            else -> throw IllegalArgumentException()
        }
        JSONObject().put("ok", true).put("value", value ?: JSONObject.NULL).toString()
    } catch (_: Exception) {
        JSONObject().put("ok", false).put("error", "storageError").toString()
    }

    @JavascriptInterface fun request(id: String, payload: String) {
        if (closed || !id.matches(Regex("[0-9]{1,12}"))) return
        fun reply(result: JSONObject) {
            view.post {
                if (!closed) view.evaluateJavascript("window.obeliskReply(${JSONObject.quote(id)},$result)", null)
            }
        }
        val failed = JSONObject().put("error", "networkError")
        try {
            val call = transport.call(JSONObject(payload))
            check(requests.putIfAbsent(id, call) == null)
            if (closed) { requests.remove(id)?.cancel(); return }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (requests.remove(id, call)) reply(failed) }
                override fun onResponse(call: Call, response: Response) {
                    val result = try { response.use {
                        val body = it.body ?: throw IOException()
                        require(!body.source().request(6_000_001L))
                        JSONObject().put("status", it.code).put("responseText", body.string())
                            .put("finalUrl", it.request.url.toString()).put("responseHeaders", it.headers.toString())
                    } } catch (_: Exception) { failed }
                    if (requests.remove(id, call)) reply(result)
                }
            })
        } catch (_: Exception) { reply(failed) }
    }

    @JavascriptInterface fun abort(id: String) { requests.remove(id)?.cancel() }
    fun close() { closed = true; requests.values.forEach(Call::cancel); requests.clear() }
}

private fun WebView.finishStoreExport(ticket: String, saved: Boolean) {
    evaluateJavascript("window.obeliskExported?.(${JSONObject.quote(ticket)}, $saved)", null)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun StoreScreen(store: StoreLibrary, shareId: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < MaterialTheme.colorScheme.onSurface.luminance()
    val textZoom = (LocalConfiguration.current.fontScale * 100).toInt()
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf<WebView?>(null) }
    // The document picker can outlive this Activity. Only the pending file name enters saved instance state.
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val name = pending
        pending = null
        if (name != null) scope.launch {
            val file = File(context.cacheDir, name)
            val outcome = withContext(Dispatchers.IO) {
                try {
                    if (uri == null) return@withContext null
                    require(uri.scheme == "content")
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } }
                        ?: throw IOException()
                    R.string.jpx_export_ok
                } catch (_: Exception) { R.string.jpx_io } finally { file.delete() }
            }
            if (outcome != null) Toast.makeText(context, outcome, Toast.LENGTH_SHORT).show()
            view?.finishStoreExport(name, outcome == R.string.jpx_export_ok)
        }
    }
    val back = {
        val current = view
        if (current == null) onBack()
        else current.evaluateJavascript("typeof window.obeliskBack==='function' && window.obeliskBack()") {
            if (it != "true") onBack()
        }
    }
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.app_back)) }
            Text(stringResource(R.string.jpx_store), style = MaterialTheme.typography.titleLarge)
        }
        key(store, language, shareId) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.blockNetworkLoads = true
                    settings.domStorageEnabled = false
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webViewClient = StoreAssets(ctx)
                    val web = this
                    val host = StoreBridge(this, store, language, shareId, darkTheme) { filename, text ->
                        val file = File.createTempFile("store-export-", ".json", ctx.cacheDir)
                        try { file.writeText(text) } catch (e: IOException) { file.delete(); throw e }
                        web.post {
                            if (pending != null || view !== web) {
                                file.delete()
                                if (view === web) web.finishStoreExport(file.name, false)
                            }
                            else {
                                pending = file.name
                                try { launcher.launch(filename) }
                                catch (_: Exception) {
                                    pending = null; file.delete()
                                    web.finishStoreExport(file.name, false)
                                    Toast.makeText(ctx, R.string.jpx_io, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        file.name
                    }
                    tag = host
                    view = this
                    addJavascriptInterface(host, "ObeliskStore")
                    loadUrl(storeHome)
                }
            }, update = { web ->
                if (web.settings.textZoom != textZoom) web.settings.textZoom = textZoom
                (web.tag as StoreBridge).updateTheme(darkTheme)
            }, onRelease = { old ->
                (old.tag as? StoreBridge)?.close()
                old.tag = null
                if (view === old) view = null
                old.removeJavascriptInterface("ObeliskStore"); old.stopLoading(); old.destroy()
            })
        }
    }
}
