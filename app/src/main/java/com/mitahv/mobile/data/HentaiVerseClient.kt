package com.mitahv.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl
import okhttp3.CookieJar
import okio.BufferedSink
import org.json.JSONObject
import com.mitahv.mobile.model.BattleLoadout
import org.jsoup.Jsoup
import com.mitahv.mobile.model.BattleAction
import com.mitahv.mobile.model.HvRoute
import com.mitahv.mobile.model.HvWorld
import com.mitahv.mobile.model.HvSite
import com.mitahv.mobile.model.HvStaminaRecovery
import com.mitahv.mobile.model.NativeAction
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class HentaiVerseClient(
    cookieJar: CookieJar,
    private val site: HvSite = HvSite.Main,
    httpClient: OkHttpClient = OkHttpClient(),
    scriptCache: Cache? = null,
    pageCache: Cache? = null,
) {
    private val pageCacheStore = pageCache
    private val client = httpClient.newBuilder()
        // GET responses may be served or revalidated according to the server's
        // cache headers. Mutating requests are forced through mutationClient below.
        // This keeps revisiting an unchanged page cheap without making stale POST
        // results authoritative.
        .cache(pageCache)
        .cookieJar(cookieJar)
        .followRedirects(true)
        .callTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            HvEndpointPolicy.check(chain.request())
            chain.proceed(chain.request())
        }
        .build()
    private val scriptClient = client.newBuilder()
        .cache(scriptCache)
        .addNetworkInterceptor(HvScriptCaches.cookieVaryInterceptor)
        .build()
    private val mutationClient = client.newBuilder()
        .cache(null)
        .retryOnConnectionFailure(false)
        .build()
    // Dictionary scripts are immutable for the lifetime of an account/site client. Keep a
    // small in-memory layer above OkHttp's disk cache so page switches avoid repeated I/O.
    private val supplementalScriptMemoryCache = ConcurrentHashMap<HttpUrl, String>()

    suspend fun fetchHtml(route: HvRoute, world: HvWorld): String = withContext(Dispatchers.IO) {
        execute(Request.Builder()
            .url(route.url(world, site))
            .header("Accept", "text/html,application/xhtml+xml")
            .get()
            .build(), world)
    }

    suspend fun fetchPage(world: HvWorld, url: HttpUrl): HvPageResponse = withContext(Dispatchers.IO) {
        executePage(NativeRequests.build(world, NativeAction(url, "GET", label = "刷新"), site), world)
    }

    suspend fun fetchAbilityTree(world: HvWorld, url: HttpUrl) = withContext(Dispatchers.Default) {
        val response = fetchPage(world, url)
        CharacterSectionsParser.parse(response.html, HvRoute.Abilities, world, response.url)
    }

    suspend fun fetchBattleLoadout(world: HvWorld): BattleLoadout? = withContext(Dispatchers.Default) {
        val response = fetchPage(world, HvRoute.Equipment.url(world, site))
        val doc = Jsoup.parse(response.html, response.url.toString())
        if (doc.selectFirst("#eqch_outer #eqsb") == null) return@withContext null
        val scripts = fetchSupplementalScripts(HvDataScriptRequests.fromPage(doc, world, site, response.url))
        CharacterSectionsParser.parse(doc, HvRoute.Equipment, world, response.url, scripts.scripts).battleLoadout
    }

    suspend fun fetchSupplementalScripts(world: HvWorld, html: String, currentUrl: HttpUrl): HvSupplementalScripts =
        withContext(Dispatchers.IO) {
            fetchSupplementalScripts(HvDataScriptRequests.fromPage(html, world, site, currentUrl))
        }

    suspend fun fetchSupplementalScripts(requests: List<Request>): HvSupplementalScripts =
        withContext(Dispatchers.IO) {
            val pending = requests.distinctBy { it.url }
                .filterNot { supplementalScriptMemoryCache.containsKey(it.url) }
            val results = coroutineScope {
                pending.map { request ->
                    async {
                        try {
                            request.url to Result.success(scriptClient.newCall(request).awaitBody { response ->
                                if (!response.isSuccessful) throw HvHttpException(response.code)
                                HvEndpointPolicy.check(response.request)
                                response.body?.string() ?: throw IOException("Empty HentaiVerse data dictionary")
                            })
                        } catch (failure: IOException) {
                            request.url to Result.failure<String>(failure)
                        }
                    }
                }.awaitAll()
            }
            results.forEach { (url, result) ->
                result.getOrNull()?.let { supplementalScriptMemoryCache.putIfAbsent(url, it) }
            }
            val failuresByUrl = results.toMap()
            HvSupplementalScripts(
                scripts = requests.mapNotNull { supplementalScriptMemoryCache[it.url] },
                failures = requests.mapNotNull { request ->
                    if (supplementalScriptMemoryCache.containsKey(request.url)) null
                    else failuresByUrl[request.url]?.exceptionOrNull() as? IOException
                },
            )
        }

    suspend fun fetchImage(world: HvWorld, currentUrl: HttpUrl, imageUrl: HttpUrl): ByteArray = withContext(Dispatchers.IO) {
        client.newCall(HvImageRequests.build(world, site, currentUrl, imageUrl)).awaitBody { response ->
            if (!response.isSuccessful) throw HvHttpException(response.code)
            HvEndpointPolicy.check(response.request)
            response.body?.bytes() ?: throw IOException("Empty HentaiVerse image")
        }
    }

    suspend fun restoreStamina(world: HvWorld, route: HvRoute, recovery: HvStaminaRecovery,
        currentUrl: HttpUrl = route.url(world, site)): HvPageResponse =
        withContext(Dispatchers.IO) { executePage(StaminaRequests.build(world, route, recovery, currentUrl, site), world) }

    suspend fun executeNativeAction(world: HvWorld, action: NativeAction): HvPageResponse =
        withContext(Dispatchers.IO) { executePage(NativeRequests.build(world, action, site), world) }

    suspend fun startArenaChallenge(
        route: HvRoute,
        world: HvWorld,
        challengeId: Int,
        postToken: String,
    ): String = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("initid", challengeId.toString())
            .add("postoken", postToken)
            .build()
        execute(
            Request.Builder()
                .url(route.url(world, site))
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Referer", route.url(world, site).toString())
                .post(oneShot(body))
                .build(),
            world,
        )
    }

    suspend fun submitPersonaForm(
        world: HvWorld,
        personaId: String,
        rename: String? = null,
        create: String? = null,
    ): String = withContext(Dispatchers.IO) {
        execute(CharacterRequests.persona(world, personaId, rename, create, site), world)
    }

    suspend fun submitAttributeAllocation(
        world: HvWorld,
        deltas: Map<String, Int>,
    ): String = withContext(Dispatchers.IO) {
        execute(CharacterRequests.attributes(world, deltas, site), world)
    }

    suspend fun submitBattleAction(
        world: HvWorld,
        referer: HttpUrl,
        token: String,
        action: BattleAction,
    ): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("type", "battle")
            .put("method", "action")
            .put("token", token)
            .put("mode", action.mode)
            .put("target", action.target)
            .put("skill", action.skill)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val response = execute(
            Request.Builder()
                .url(requireNotNull(world.baseUrl(site).resolve("json")))
                .header("Content-Type", "application/json")
                .header("Referer", referer.toString())
                .post(oneShot(body))
                .build(),
            world,
        )
        runCatching { JSONObject(response) }
            .getOrElse { throw IOException("Invalid battle action response", it) }
    }

    private suspend fun execute(request: Request, world: HvWorld): String = executePage(request, world).html

    private suspend fun executePage(request: Request, world: HvWorld): HvPageResponse {
        val prepared = HvEndpointPolicy.prepare(request, world, site)
        val isMutation = prepared.body?.isOneShot() == true
        val routed = if (isMutation) {
            prepared.newBuilder()
                .cacheControl(CacheControl.Builder().noCache().noStore().build())
                .build()
        } else {
            prepared
        }
        if (isMutation) pageCacheStore?.evictAll()
        return (if (isMutation) mutationClient else client)
            .newCall(routed).awaitBody { response ->
            if (!response.isSuccessful) throw HvHttpException(response.code)
            HvEndpointPolicy.check(response.request)
            HvPageResponse(response.body?.string() ?: throw IOException("Empty HentaiVerse response"), response.request.url)
        }
    }

    private fun oneShot(body: RequestBody) = object : RequestBody() {
        override fun contentType() = body.contentType()
        override fun contentLength() = body.contentLength()
        override fun isOneShot() = true
        override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal class HvHttpException(val statusCode: Int) : IOException()

internal data class HvPageResponse(val html: String, val url: HttpUrl)

internal data class HvSupplementalScripts(val scripts: List<String>, val failures: List<IOException>)
