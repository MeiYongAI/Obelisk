package com.mitahv.mobile.data

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class HvHttpCancellationTest {
    @Test fun cancellationBeforeHeadersCancelsCallAndClosesLateResponse() = runBlocking {
        val call = ControlledCall()
        val operation = launch(start = CoroutineStart.UNDISPATCHED) {
            call.awaitBody { it.body!!.string() }
        }

        operation.cancelAndJoin()

        assertTrue(call.isCanceled())
        val lateBody = TrackingBody("late response")
        call.respond(lateBody)
        assertTrue(lateBody.closed.get())
    }

    @Test fun cancellationWhileReadingBodyCancelsCallAndClosesResponse() = runBlocking {
        val readStarted = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val bodyClosed = CountDownLatch(1)
        val call = ControlledCall(onCancel = { releaseRead.countDown() })
        val body = object : ResponseBody() {
            private val buffered = object : Source {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    readStarted.countDown()
                    if (!releaseRead.await(2, TimeUnit.SECONDS)) throw IOException("Read was not canceled")
                    throw IOException("Canceled")
                }
                override fun timeout() = Timeout.NONE
                override fun close() { bodyClosed.countDown() }
            }.buffer()
            override fun contentType(): MediaType? = null
            override fun contentLength() = -1L
            override fun source(): BufferedSource = buffered
        }
        val operation = launch(start = CoroutineStart.UNDISPATCHED) {
            call.awaitBody { it.body!!.string() }
        }
        val callbackFailure = AtomicReference<Throwable?>()
        val callbackThread = thread(isDaemon = true, name = "test-response-body") {
            try { call.respond(body) } catch (failure: Throwable) { callbackFailure.set(failure) }
        }
        try {
            assertTrue("Response body was never read", readStarted.await(2, TimeUnit.SECONDS))
            operation.cancelAndJoin()

            assertTrue(call.isCanceled())
            assertTrue("Canceled response body remained open", bodyClosed.await(2, TimeUnit.SECONDS))
            callbackThread.join(2_000)
            assertFalse(callbackThread.isAlive)
            assertNull(callbackFailure.get())
        } finally {
            releaseRead.countDown()
            operation.cancelAndJoin()
            callbackThread.join(2_000)
        }
    }

    @Test fun successfulBodyIsConsumedAndClosed() = runBlocking {
        val call = ControlledCall()
        val body = TrackingBody("dictionary")
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            call.awaitBody { it.body!!.string() }
        }

        call.respond(body)

        assertEquals("dictionary", result.await())
        assertTrue(body.closed.get())
    }

    @Test fun readerFailureIsPropagatedAndResponseIsClosed() = runBlocking {
        val call = ControlledCall()
        val body = TrackingBody("dictionary")
        val failure = IOException("Cannot read dictionary")
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { call.awaitBody<String> { throw failure } }
        }

        call.respond(body)

        val propagated = result.await().exceptionOrNull()
        assertTrue(propagated is IOException)
        assertEquals(failure.message, propagated?.message)
        assertTrue(body.closed.get())
    }

    private class TrackingBody(text: String) : ResponseBody() {
        val closed = AtomicBoolean(false)
        private val buffered = object : ForwardingSource(Buffer().writeUtf8(text)) {
            override fun close() {
                closed.set(true)
                super.close()
            }
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength() = -1L
        override fun source(): BufferedSource = buffered
    }

    private class ControlledCall(private val onCancel: () -> Unit = {}) : Call {
        private val request = Request.Builder().url("https://hentaiverse.org/").build()
        private val canceled = AtomicBoolean(false)
        @Volatile private var callback: Callback? = null

        override fun request(): Request = request
        override fun execute(): Response = error("Only asynchronous execution is supported")
        override fun enqueue(responseCallback: Callback) { callback = responseCallback }
        override fun cancel() {
            canceled.set(true)
            onCancel()
        }
        override fun isExecuted() = callback != null
        override fun isCanceled() = canceled.get()
        override fun timeout() = Timeout.NONE
        override fun clone(): Call = ControlledCall(onCancel)

        fun respond(body: ResponseBody) {
            requireNotNull(callback).onResponse(this, Response.Builder().request(request)
                .protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build())
        }
    }
}
