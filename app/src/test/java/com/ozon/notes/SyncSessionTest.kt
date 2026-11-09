package com.ozon.notes

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SyncSessionTest {
    @Test fun cancellationReleasesOwnershipAndResetsState() = runBlocking {
        val session = SyncSession()
        val started = CompletableDeferred<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            assertTrue(session.tryStart())
            try {
                started.complete(Unit)
                awaitCancellation()
            } finally {
                session.finish()
            }
        }
        started.await()
        assertTrue(session.isSyncing.value)
        assertFalse(session.tryStart())
        job.cancelAndJoin()
        assertFalse(session.isSyncing.value)
        assertTrue(session.tryStart())
        session.finish()
    }

    @Test fun concurrentAttemptsDoNotOverlap() = runBlocking {
        val session = SyncSession()
        val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val owner = launch(start = CoroutineStart.UNDISPATCHED) {
            assertTrue(session.tryStart())
            try {
                started.complete(Unit)
                release.await()
            } finally {
                session.finish()
            }
        }
        started.await()
        val attempts = List(20) {
            launch(kotlinx.coroutines.Dispatchers.Default) { assertFalse(session.tryStart()) }
        }
        attempts.forEach { it.join() }
        assertTrue(session.isSyncing.value)
        release.complete(Unit)
        owner.join()
        assertFalse(session.isSyncing.value)
        assertTrue(session.tryStart())
        session.finish()
    }

    @Test fun failureAndSuccessAllowSuccessiveAttempts() {
        val session = SyncSession()
        repeat(3) { attempt ->
            assertTrue(session.tryStart())
            try {
                assertFalse(session.tryStart())
                if (attempt == 0) throw IllegalStateException("failed sync")
            } catch (_: IllegalStateException) {
                // Simulate the engine reporting an ordinary failure.
            } finally {
                session.finish()
            }
            assertFalse(session.isSyncing.value)
        }
    }
}
