package com.adnan.memora.telegram

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.withTimeoutOrNull

/** A quiet/never-completing TDLib update cannot hold a worker forever. Null means uncertain, not safe-to-resend. */
internal object UploadAwaiter {
    suspend fun <T : Any> await(
        result: Deferred<T>, lastProgress: () -> Long,
        now: () -> Long = { System.nanoTime() / 1_000_000 },
        idleMs: Long = 120_000, totalMs: Long = 7_200_000, pollMs: Long = 5_000,
    ): T? {
        val start = now()
        while (true) {
            withTimeoutOrNull(pollMs) { result.await() }?.let { return it }
            if (now() - lastProgress() >= idleMs || now() - start >= totalMs) return null
        }
    }
}
