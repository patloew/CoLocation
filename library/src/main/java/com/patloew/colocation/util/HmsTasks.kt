package com.patloew.colocation

import com.google.android.gms.tasks.RuntimeExecutionException
import com.huawei.hmf.tasks.CancellationTokenSource
import com.huawei.hmf.tasks.Task
import com.huawei.hmf.tasks.TaskCompletionSource
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Converts this deferred to the instance of [Task].
 * If deferred is cancelled then resulting task will be cancelled as well.
 */
@ExperimentalCoroutinesApi
fun <T> Deferred<T>.asTask(): Task<T> {
    val cancellation = CancellationTokenSource()
    val source = TaskCompletionSource<T>(cancellation.token)

    invokeOnCompletion callback@{
        if (it is CancellationException) {
            cancellation.cancel()
            return@callback
        }

        val t = getCompletionExceptionOrNull()
        if (t == null) {
            source.setResult(getCompleted())
        } else {
            source.setException(t as? Exception ?: RuntimeExecutionException(t))
        }
    }

    return source.task
}

/**
 * Converts this task to an instance of [Deferred].
 * If task is cancelled then resulting deferred will be cancelled as well.
 */
fun <T> Task<T>.asDeferred(): Deferred<T> {
    if (isComplete) {
        val e = exception
        return if (e == null) {
            @Suppress("UNCHECKED_CAST")
            CompletableDeferred<T>().apply { if (isCanceled) cancel() else complete(result as T) }
        } else {
            CompletableDeferred<T>().apply { completeExceptionally(e) }
        }
    }

    val result = CompletableDeferred<T>()
    addOnCompleteListener {
        val e = it.exception
        if (e == null) {
            @Suppress("UNCHECKED_CAST")
            if (isCanceled) result.cancel() else result.complete(it.result as T)
        } else {
            result.completeExceptionally(e)
        }
    }
    return result
}

/**
 * Awaits for completion of the task without blocking a thread.
 *
 * This suspending function is cancellable.
 * If the [Job] of the current coroutine is cancelled or completed while this suspending function is waiting, this function
 * stops waiting for the completion stage and immediately resumes with [CancellationException].
 */
suspend fun <T> Task<T>.await(): T {
    // fast path
    if (isComplete) {
        val e = exception
        return if (e == null) {
            if (isCanceled) {
                throw CancellationException("Task $this was cancelled normally.")
            } else {
                @Suppress("UNCHECKED_CAST")
                result as T
            }
        } else {
            throw e
        }
    }

    return suspendCancellableCoroutine { cont ->
        addOnCompleteListener {
            val e = exception
            if (e == null) {
                @Suppress("UNCHECKED_CAST")
                if (isCanceled) cont.cancel() else cont.resume(result as T)
            } else {
                cont.resumeWithException(e)
            }
        }
    }
}
