package com.mitahv.mobile.data

import android.content.Context
import androidx.concurrent.futures.await
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.JavaScriptSandbox
import androidx.javascriptengine.SandboxDeadException
import com.mitahv.mobile.model.BattleLoadout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Share the service connection, never JS globals. Closing a turn's isolate stops even a busy loop. */
internal class BattleScriptSandbox(private val context: Context) : AutoCloseable {
    private val bridge by lazy { context.assets.open("jpx_turn.js").bufferedReader().use { it.readText() } }
    private val connecting = Mutex()
    private var closed = false
    private var sandbox: JavaScriptSandbox? = null

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

    suspend fun evaluate(code: String, ctx: RuleContext, loadout: BattleLoadout?): ScriptDecision = try {
        val engine = connect()
        val params = IsolateStartupParameters()
        if (!engine.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_TERMINATION)) error("Update Android System WebView")
        if (engine.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE)) params.setMaxHeapSizeBytes(16L * 1024 * 1024)
        else error("Update Android System WebView")
        if (engine.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT)) params.setMaxEvaluationReturnSizeBytes(4096)
        else error("Update Android System WebView")
        val turn = BattleScriptTurn(ctx, loadout)
        engine.createIsolate(params).use { isolate ->
            val source = turn.source(bridge, code)
            withTimeoutOrNull(1500) { turn.decode(isolate.evaluateJavaScriptAsync(source).await()) }
                ?: ScriptDecision.Error("Script timed out")
        }
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        if (e is SandboxDeadException) synchronized(this) { sandbox?.close(); sandbox = null }
        ScriptDecision.Error(e.message?.lineSequence()?.firstOrNull()?.take(160) ?: e.javaClass.simpleName)
    }

    @Synchronized override fun close() { closed = true; sandbox?.close(); sandbox = null }
}
