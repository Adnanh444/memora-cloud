package com.adnan.memora.backup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class UploadRunGateTest {
    @Test fun overlappingWorkersNeverEnterTogether() = runTest {
        val gate = UploadRunGate()
        var active = 0
        var maximum = 0
        var finished = 0
        List(30) {
            launch {
                gate.run {
                    active++
                    maximum = maxOf(maximum, active)
                    delay(10)
                    finished++
                    active--
                }
            }
        }.joinAll()
        assertEquals(1, maximum)
        assertEquals(30, finished)
    }

    @Test fun failureReleasesGate() = runTest {
        val gate = UploadRunGate()
        try {
            gate.run { throw IOException("injected failure") }
            fail("expected failure")
        } catch (_: IOException) { }
        assertEquals("next worker", gate.run { "next worker" })
    }

    @Test fun cancelledOwnerReleasesGateForNextWorker() = runTest {
        val gate = UploadRunGate()
        val entered = CompletableDeferred<Unit>()
        val first = launch {
            gate.run {
                entered.complete(Unit)
                delay(Long.MAX_VALUE)
            }
        }
        entered.await()
        first.cancelAndJoin()
        assertTrue(gate.run { true })
    }

    @Test fun cancellingWaiterDoesNotCancelOwnerOrBlockLaterWork() = runTest {
        val gate = UploadRunGate()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val owner = launch { gate.run { entered.complete(Unit); release.await() } }
        entered.await()
        var waiterEntered = false
        val waiter = launch { gate.run { waiterEntered = true } }
        waiter.cancelAndJoin()
        assertFalse(waiterEntered)
        release.complete(Unit)
        owner.join()
        assertEquals(7, gate.run { 7 })
    }
}
