package com.mitahv.mobile.data

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/** Keep cancellation attached until the body has been consumed and closed on OkHttp's worker. */
internal suspend fun <T> Call.awaitBody(read: (Response) -> T): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val value = response.use {
                        if (!continuation.isActive) return
                        read(it)
                    }
                    continuation.resume(value)
                } catch (failure: Throwable) {
                    continuation.resumeWithException(failure)
                }
            }
        })
    }
