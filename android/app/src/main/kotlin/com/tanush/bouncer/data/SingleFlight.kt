package com.tanush.bouncer.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Collapses concurrent calls for the same key onto one shared execution.
 *
 * Several independent callers ask for the same remote data at once — the match list is requested
 * by the live poll loop, the Android Auto screen and [com.tanush.bouncer.services.ScorePollingWorker]
 * simultaneously — and each was firing its own billed request for identical bytes.
 *
 * The work runs on this object's own scope rather than a caller's, so one caller giving up (screen
 * closed, coroutine cancelled) does not cancel the fetch the other callers are still waiting on.
 * The in-flight entry is cleared by the job's own completion, not by whichever caller happens to
 * return first — otherwise an early cancellation would drop the entry while the work was still
 * running and let a later caller start a duplicate.
 */
class SingleFlight<K : Any, V>(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<K, Deferred<V>>()

    suspend fun run(key: K, block: suspend () -> V): V {
        val deferred = mutex.withLock {
            // An entry that has already completed is never reused: joining it would hand a fresh
            // caller a stale result. Skipping it makes correctness independent of *when* the
            // bookkeeping below manages to remove it.
            inFlight[key]?.takeIf { !it.isCompleted }
                ?: scope.async { block() }.also { inFlight[key] = it }
        }
        try {
            return deferred.await()
        } finally {
            // Only clear a finished job. A caller that gave up early must not evict work the
            // others are still awaiting.
            if (deferred.isCompleted) {
                mutex.withLock { if (inFlight[key] === deferred) inFlight.remove(key) }
            }
        }
    }

    /** Number of executions currently shared between callers. Test/diagnostic use. */
    suspend fun inFlightCount(): Int = mutex.withLock { inFlight.size }
}
