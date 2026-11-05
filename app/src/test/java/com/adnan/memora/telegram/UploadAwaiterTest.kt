package com.adnan.memora.telegram

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UploadAwaiterTest {
    @Test fun alreadyCompletedSendReturnsImmediately() = runTest {
        val result = CompletableDeferred("confirmed")
        assertEquals("confirmed", UploadAwaiter.await(result, { 0L }, { testScheduler.currentTime }, idleMs = 100, pollMs = 10))
    }
    @Test fun missingProgressEndsAsUncertainNotAnInfiniteWait() = runTest {
        val result = CompletableDeferred<String>()
        assertNull(UploadAwaiter.await(result, { 0L }, { testScheduler.currentTime }, idleMs = 100, pollMs = 10))
        assertEquals(100L, testScheduler.currentTime)
        assertFalse(result.isCancelled) // An uncertain server outcome is not treated as a rejected upload.
    }
    @Test fun transferActivityExtendsIdleDeadline() = runTest {
        val result = CompletableDeferred<String>()
        var last = 0L
        launch { delay(80); last = testScheduler.currentTime; delay(60); result.complete("ok") }
        assertEquals("ok", UploadAwaiter.await(result, { last }, { testScheduler.currentTime }, idleMs = 100, pollMs = 10))
    }
    @Test fun absoluteDeadlineStopsAnEndlessTransfer() = runTest {
        val result = CompletableDeferred<String>()
        assertNull(UploadAwaiter.await(result, { testScheduler.currentTime }, { testScheduler.currentTime }, idleMs = 100, totalMs = 200, pollMs = 10))
        assertEquals(200L, testScheduler.currentTime)
    }
    @Test fun cancellationIsNotSwallowedAsTimeout() = runTest {
        val result = CompletableDeferred<String>()
        result.cancel(CancellationException("pause"))
        var cancelled = false
        try { UploadAwaiter.await(result, { 0L }, { 0L }, pollMs = 10) } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }
}
