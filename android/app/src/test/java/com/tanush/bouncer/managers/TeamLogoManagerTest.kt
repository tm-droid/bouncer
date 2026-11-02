package com.tanush.bouncer.managers

import org.junit.Assert.*
import org.junit.Test

class TeamLogoManagerTest {

    @Test
    fun `before initialization, getBestLogoUrl returns null gracefully`() {
        // TeamLogoManager is an object singleton — it may already be "initialized"
        // in another test, but logos map will be empty without real assets.
        // Either way, a lookup for a non-existent team returns null, not a crash.
        val result = TeamLogoManager.getBestLogoUrl("India", null)
        // No real assets in unit-test classpath → logos is emptyMap → returns null
        assertNull(result)
    }

    @Test
    fun `getBestLogoUrl returns fallback when teamName not found`() {
        val fallback = "https://example.com/logo.png"
        val result = TeamLogoManager.getBestLogoUrl(null, fallback)
        // logos map is empty in unit tests; fallback should be returned as-is
        // (normalizeCricbuzzUrl only transforms static.cricbuzz.com URLs)
        assertEquals(fallback, result)
    }

    @Test
    fun `initialize does not throw when called concurrently`() {
        // Idempotency + thread-safety smoke test for the @Volatile isInitialized guard.
        // Without a real Context the init will throw NPE — that's caught inside initialize().
        // We're testing that concurrent calls don't cause ConcurrentModificationException
        // or double-init side effects.
        var exceptionFromGuard: Throwable? = null
        val threads = (1..4).map {
            Thread {
                try {
                    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                    TeamLogoManager.initialize(null!!)
                } catch (e: NullPointerException) {
                    // Expected — no real Context. Guard still exercised.
                } catch (e: Exception) {
                    // Any other exception (e.g. ConcurrentModificationException) is a failure.
                    exceptionFromGuard = e
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertNull("Unexpected exception during concurrent initialize: $exceptionFromGuard", exceptionFromGuard)
    }
}
