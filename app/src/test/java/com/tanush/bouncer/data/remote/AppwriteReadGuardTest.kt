package com.tanush.bouncer.data.remote

import com.tanush.bouncer.data.CacheConfig
import io.appwrite.exceptions.AppwriteException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class AppwriteReadGuardTest {

    private var now = 0L
    private val reports = mutableListOf<Pair<String, Map<String, String>>>()

    @Before
    fun setUp() {
        AppwriteReadGuard.reset()
        now = 0L
        AppwriteReadGuard.nowMs = { now }
        AppwriteReadGuard.reporter = { message, tags -> reports.add(message to tags) }
        reports.clear()
    }

    @After
    fun tearDown() {
        AppwriteReadGuard.reset()
    }

    private fun budget402ByCode() = AppwriteException("reads exceeded", 402, "some_type")
    private fun budget402ByType() = AppwriteException("reads exceeded", null, "limit_databases_reads_exceeded")

    @Test
    fun `closed breaker allows reads`() {
        assertNull(AppwriteReadGuard.checkAndCount("test"))
        assertNull(AppwriteReadGuard.checkAndCount("test"))
    }

    @Test
    fun `402 by code opens breaker and blocks subsequent reads`() {
        assertNull(AppwriteReadGuard.checkAndCount("test"))
        AppwriteReadGuard.recordFailure("test", budget402ByCode())
        assertEquals(AppwriteReadGuard.BlockReason.BREAKER_OPEN, AppwriteReadGuard.checkAndCount("test"))
    }

    @Test
    fun `402 by type string opens breaker`() {
        AppwriteReadGuard.recordFailure("test", budget402ByType())
        assertEquals(AppwriteReadGuard.BlockReason.BREAKER_OPEN, AppwriteReadGuard.checkAndCount("test"))
    }

    @Test
    fun `non-402 errors do not open breaker`() {
        AppwriteReadGuard.recordFailure("test", AppwriteException("server error", 500, "general_unknown"))
        AppwriteReadGuard.recordFailure("test", IOException("network down"))
        assertNull(AppwriteReadGuard.checkAndCount("test"))
    }

    @Test
    fun `open breaker allows single probe after probe interval`() {
        AppwriteReadGuard.recordFailure("test", budget402ByCode())
        now += CacheConfig.READ_GUARD_PROBE_INTERVAL_MS - 1
        assertEquals(AppwriteReadGuard.BlockReason.BREAKER_OPEN, AppwriteReadGuard.checkAndCount("test"))
        now += 1
        assertNull(AppwriteReadGuard.checkAndCount("probe")) // probe allowed
        // Second concurrent read while probe is in flight stays blocked.
        assertEquals(AppwriteReadGuard.BlockReason.BREAKER_OPEN, AppwriteReadGuard.checkAndCount("test"))
    }

    @Test
    fun `successful probe closes breaker`() {
        AppwriteReadGuard.recordFailure("test", budget402ByCode())
        now += CacheConfig.READ_GUARD_PROBE_INTERVAL_MS
        assertNull(AppwriteReadGuard.checkAndCount("probe"))
        AppwriteReadGuard.recordSuccess()
        assertNull(AppwriteReadGuard.checkAndCount("test"))
    }

    @Test
    fun `probe 402 re-latches breaker with fresh probe interval`() {
        AppwriteReadGuard.recordFailure("test", budget402ByCode())
        now += CacheConfig.READ_GUARD_PROBE_INTERVAL_MS
        assertNull(AppwriteReadGuard.checkAndCount("probe"))
        AppwriteReadGuard.recordFailure("probe", budget402ByCode())
        // Just before the new interval elapses: still blocked.
        now += CacheConfig.READ_GUARD_PROBE_INTERVAL_MS - 1
        assertEquals(AppwriteReadGuard.BlockReason.BREAKER_OPEN, AppwriteReadGuard.checkAndCount("test"))
        now += 1
        assertNull(AppwriteReadGuard.checkAndCount("probe"))
    }

    @Test
    fun `non-402 probe failure keeps breaker open but allows next probe`() {
        AppwriteReadGuard.recordFailure("test", budget402ByCode())
        now += CacheConfig.READ_GUARD_PROBE_INTERVAL_MS
        assertNull(AppwriteReadGuard.checkAndCount("probe"))
        AppwriteReadGuard.recordFailure("probe", IOException("network down"))
        // Probe interval already elapsed and probeInFlight cleared → next call probes again.
        assertNull(AppwriteReadGuard.checkAndCount("probe"))
    }

    @Test
    fun `minute cap trips and latches breaker`() {
        repeat(CacheConfig.READ_GUARD_MAX_READS_PER_MINUTE) {
            assertNull(AppwriteReadGuard.checkAndCount("burst"))
        }
        assertEquals(AppwriteReadGuard.BlockReason.RATE_CAPPED, AppwriteReadGuard.checkAndCount("burst"))
        // Latched: even after the minute window would have rolled, reads stay blocked.
        now += CacheConfig.READ_GUARD_MINUTE_WINDOW_MS
        assertEquals(AppwriteReadGuard.BlockReason.BREAKER_OPEN, AppwriteReadGuard.checkAndCount("burst"))
    }

    @Test
    fun `hour cap trips even when minute cap never does`() {
        var reads = 0
        while (reads < CacheConfig.READ_GUARD_MAX_READS_PER_HOUR) {
            // Stay under the minute cap by advancing the clock each "minute".
            repeat(CacheConfig.READ_GUARD_MAX_READS_PER_MINUTE - 1) {
                if (reads < CacheConfig.READ_GUARD_MAX_READS_PER_HOUR) {
                    assertNull(AppwriteReadGuard.checkAndCount("steady"))
                    reads++
                }
            }
            now += CacheConfig.READ_GUARD_MINUTE_WINDOW_MS
        }
        assertEquals(AppwriteReadGuard.BlockReason.RATE_CAPPED, AppwriteReadGuard.checkAndCount("steady"))
    }

    @Test
    fun `reporter fires once on 402 open not on every blocked read`() {
        AppwriteReadGuard.recordFailure("test", budget402ByCode())
        repeat(10) { AppwriteReadGuard.checkAndCount("test") }
        assertEquals(1, reports.size)
        assertEquals("breaker_open_402", reports[0].second["reason"])
        assertEquals("test", reports[0].second["source"])
    }

    @Test
    fun `reporter re-fires only after report interval`() {
        AppwriteReadGuard.recordFailure("test", budget402ByCode())
        assertEquals(1, reports.size)
        // Failed probe just before the report interval: deduped.
        now += CacheConfig.READ_GUARD_REPORT_INTERVAL_MS - 1
        AppwriteReadGuard.recordFailure("probe", budget402ByCode())
        assertEquals(1, reports.size)
        now += 1
        AppwriteReadGuard.recordFailure("probe", budget402ByCode())
        assertEquals(2, reports.size)
    }

    @Test
    fun `rate cap trip reports with rate_capped reason`() {
        repeat(CacheConfig.READ_GUARD_MAX_READS_PER_MINUTE) { AppwriteReadGuard.checkAndCount("burst") }
        AppwriteReadGuard.checkAndCount("burst")
        assertEquals(1, reports.size)
        assertEquals("rate_capped", reports[0].second["reason"])
    }

    @Test
    fun `reportIfBudget402 reports for 402 without opening breaker`() {
        AppwriteReadGuard.reportIfBudget402("storage.downloadFile", budget402ByCode())
        assertEquals(1, reports.size)
        assertEquals("storage_budget_402", reports[0].second["reason"])
        assertNull(AppwriteReadGuard.checkAndCount("test"))
    }

    @Test
    fun `reportIfBudget402 ignores non-402 errors`() {
        AppwriteReadGuard.reportIfBudget402("storage.downloadFile", IOException("network down"))
        assertTrue(reports.isEmpty())
    }

    @Test
    fun `recordSuccess counts documents so a limit-N read trips the cap N times faster`() {
        // One call reports the minute cap worth of rows minus 1 (checkAndCount already counted 1).
        assertNull(AppwriteReadGuard.checkAndCount("bulk"))
        AppwriteReadGuard.recordSuccess(CacheConfig.READ_GUARD_MAX_READS_PER_MINUTE)
        // The window is now at the cap; the next call trips it even though only 2 calls happened.
        assertEquals(AppwriteReadGuard.BlockReason.RATE_CAPPED, AppwriteReadGuard.checkAndCount("bulk"))
    }

    @Test
    fun `recordSuccess ignores single-row and empty reads`() {
        assertNull(AppwriteReadGuard.checkAndCount("one"))
        AppwriteReadGuard.recordSuccess(1)
        AppwriteReadGuard.recordSuccess(0)
        // Still far under the cap — a single-row read costs exactly its one already-counted call.
        assertNull(AppwriteReadGuard.checkAndCount("one"))
    }

    @Test
    fun `persisted breaker latch is restored on init`() {
        val saved = mutableMapOf<String, Any>()
        val store = object : AppwriteReadGuard.BreakerStore {
            override fun save(open: Boolean, openedAtMs: Long) {
                saved["open"] = open; saved["at"] = openedAtMs
            }
            override fun load(): AppwriteReadGuard.BreakerState? =
                (saved["open"] as? Boolean)?.takeIf { it }
                    ?.let { AppwriteReadGuard.BreakerState(true, saved["at"] as Long) }
        }
        // First "process": a 402 latches and persists the breaker.
        AppwriteReadGuard.initPersistence(store)
        AppwriteReadGuard.recordFailure("test", budget402ByCode())
        assertEquals(true, saved["open"])

        // Second "process": in-memory reset, but init restores the latch from the store.
        AppwriteReadGuard.reset()
        AppwriteReadGuard.nowMs = { now }
        AppwriteReadGuard.initPersistence(store)
        assertEquals(AppwriteReadGuard.BlockReason.BREAKER_OPEN, AppwriteReadGuard.checkAndCount("test"))
    }

    @Test
    fun `isBudget402 detects code and type`() {
        assertTrue(AppwriteReadGuard.isBudget402(budget402ByCode()))
        assertTrue(AppwriteReadGuard.isBudget402(budget402ByType()))
        assertEquals(false, AppwriteReadGuard.isBudget402(AppwriteException("nope", 500, "general_unknown")))
        assertEquals(false, AppwriteReadGuard.isBudget402(IOException("nope")))
    }
}
