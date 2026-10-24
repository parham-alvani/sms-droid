package me.parham1995.sms.data

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for suspending work. The plain one catches
 * [CancellationException] too, so a cancelled backup would be recorded as a
 * failed one and the worker would ask to be retried; notes-droid paid for
 * that once.
 */
inline fun <T> runCatchingUnlessCancelled(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

/** A one-line reason for a person: the message, or the type when there is none. */
fun Throwable.reason(): String = message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName
