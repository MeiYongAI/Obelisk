package com.mitahv.mobile.data

import android.content.Context
import androidx.concurrent.futures.await
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import androidx.javascriptengine.SandboxDeadException
import com.mitahv.mobile.model.BattleLoadout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** One isolated JS realm per profile and encounter. No battle script runs in the store or login WebView. */
internal class BattleScriptSandbox(private val context: Context) : AutoCloseable {
    private val bridge by lazy {
        listOf("jpx_battle_document.js", "jpx_turn.js").joinToString("\n") { file ->
            context.assets.open(file).bufferedReader().use { it.readText() }
        }
    }
    private val connecting = Mutex()
    private val evaluations = Mutex()
    private var closed = false
    private var sandbox: JavaScriptSandbox? = null
    private var isolate: JavaScriptIsolate? = null
    private var nameIsolate: JavaScriptIsolate? = null
    private val nameResults = object : LinkedHashMap<Pair<String, String>, Boolean>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, String>, Boolean>?) = size > 256
    }
    private var encounter: Any? = null
    private var previous: RuleContext? = null

    private suspend fun connect(): JavaScriptSandbox = connecting.withLock {
        synchronized(this) { check(!closed); sandbox?.let { return it } }
        check(JavaScriptSandbox.isSupported()) { "Android System WebView does not support JavaScriptSandbox" }
        // A pause during bind must not lose the only handle to a process-wide sandbox connection.
        withContext(NonCancellable) {
            val connected = JavaScriptSandbox.createConnectedInstanceAsync(context).await()
            synchronized(this@BattleScriptSandbox) {
                if (closed) { connected.close(); throw CancellationException("Battle closed") }
                sandbox = connected
            }
            connected
        }
    }

    private fun createIsolate(engine: JavaScriptSandbox): JavaScriptIsolate {
        val params = IsolateStartupParameters()
        if (!engine.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_TERMINATION)) error("Update Android System WebView")
        if (engine.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE)) params.setMaxHeapSizeBytes(16L * 1024 * 1024)
        else error("Update Android System WebView")
        if (engine.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT)) params.setMaxEvaluationReturnSizeBytes(4096)
        else error("Update Android System WebView")
        return engine.createIsolate(params)
    }

    /** Match exactly as JPX does, resetting stateful g/y flags for each monster. This realm never runs profile code. */
    suspend fun matchesName(input: String, name: String): Boolean = evaluations.withLock {
        val key = input to name
        nameResults[key]?.let { return@withLock it }
        try {
            val engine = connect()
            val realm = synchronized(this) {
                check(!closed)
                nameIsolate ?: createIsolate(engine).also { nameIsolate = it }
            }
            val delimiter = input.lastIndexOf('/')
            val pattern = JSONObject.quote(input.substring(1, delimiter))
            val flags = JSONObject.quote(input.substring(delimiter + 1))
            val subject = JSONObject.quote(name)
            // JPX toRegExp returns null on invalid syntax, and tName treats null as a match.
            val code = "(() => { try { return String(new RegExp($pattern,$flags).test($subject)); } catch (e) { if (e instanceof SyntaxError) return 'true'; throw e; } })()"
            val result = withTimeoutOrNull(1500) { realm.evaluateJavaScriptAsync(code).await() }
                ?: throw NameMatchFailure("Monster name expression timed out")
            result.toBooleanStrict().also { nameResults[key] = it }
        } catch (e: CancellationException) {
            synchronized(this) { resetNames() }
            throw e
        } catch (e: Exception) {
            synchronized(this) {
                resetNames()
                if (e is SandboxDeadException) { resetRealm(); sandbox?.close(); sandbox = null }
            }
            throw NameMatchFailure(e.message?.lineSequence()?.firstOrNull()?.take(160) ?: e.javaClass.simpleName)
        }
    }

    suspend fun evaluate(codes: List<String>, ctx: RuleContext, loadout: BattleLoadout?): ScriptDecision = evaluations.withLock {
        try {
            val engine = connect()
            val realm = synchronized(this) {
                check(!closed)
                if (encounter !== ctx.page.encounterKey) resetRealm()
                isolate ?: createIsolate(engine).also { isolate = it; encounter = ctx.page.encounterKey }
            }
            val prior = previous
            val turn = BattleScriptTurn(ctx, loadout)
            val newRound = prior == null || prior.page.round.current != ctx.page.round.current || prior.page.round.floor != ctx.page.round.floor
            val source = turn.source(codes, prior !== ctx, newRound)
            val result = withTimeoutOrNull(1500) {
                if (prior == null) realm.evaluateJavaScriptAsync(bridge + "\n''").await()
                turn.decode(realm.evaluateJavaScriptAsync(source).await())
            }
            if (result == null || result is ScriptDecision.Error) {
                synchronized(this) { resetRealm() }
                result ?: ScriptDecision.Error("Script timed out")
            } else {
                previous = ctx
                result
            }
        } catch (e: CancellationException) {
            synchronized(this) { resetRealm() }
            throw e
        } catch (e: Exception) {
            synchronized(this) {
                resetRealm()
                if (e is SandboxDeadException) { resetNames(); sandbox?.close(); sandbox = null }
            }
            ScriptDecision.Error(e.message?.lineSequence()?.firstOrNull()?.take(160) ?: e.javaClass.simpleName)
        }
    }

    private fun resetRealm() {
        isolate?.close()
        isolate = null
        encounter = null
        previous = null
    }

    private fun resetNames() {
        nameIsolate?.close()
        nameIsolate = null
    }

    @Synchronized override fun close() {
        closed = true
        resetRealm()
        resetNames()
        sandbox?.close()
        sandbox = null
    }
}
