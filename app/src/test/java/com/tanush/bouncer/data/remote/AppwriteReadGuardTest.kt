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
    fun `minute cap trips and pauses reads`() {
        repeat(CacheConfig.READ_GUARD_MAX_READS_PER_MINUTE) {
            assertNull(AppwriteReadGuard.checkAndCount("burst"))
        }
        assertEquals(AppwriteReadGuard.BlockReason.RATE_CAPPED, AppwriteReadGuard.checkAndCount("burst"))
    }

    @Test
    fun `a rate cap does not latch the 402 breaker and self-clears`() {
        // Previously a rate-cap trip called openBreaker(), latching the SAME 6-hour breaker as a
        // real budget 402 — and persisting it across process restarts. A transient over-count
        // therefore became an app-wide read outage lasting six hours.
        repeat(CacheConfig.READ_GUARD_MAX_READS_PER_MINUTE) {
            AppwriteReadGuard.checkAndCount("burst")
        }
        assertEquals(AppwriteReadGuard.BlockReason.RATE_CAPPED, AppwriteReadGuard.checkAndCount("burst"))

        // Still paused, and still reported as RATE_CAPPED rather than BREAKER_OPEN.
        now += CacheConfig.READ_GUARD_RATE_CAP_PAUSE_MS / 2
        assertEquals(AppwriteReadGuard.BlockReason.RATE_CAPPED, AppwriteReadGuard.checkAndCount("burst"))

        // Pause elapses and the window has slid clear — reads resume with no probe or reset needed.
        now += CacheConfig.READ_GUARD_RATE_CAP_PAUSE_MS
        assertNull(AppwriteReadGuard.checkAndCount("burst"))
    }

    @Test
    fun `sliding minute window catches a burst straddling the old window boundary`() {
        // REGRESSION: with fixed windows, 199 reads at t=59s plus 200 at t=61s was 399 reads in
        // two seconds without tripping, because the window reset at the boundary.
        val cap = CacheConfig.READ_GUARD_MAX_READS_PER_MINUTE
        now = 59_000L
        repeat(cap - 1) { assertNull(AppwriteReadGuard.checkAndCount("edge")) }

        now = 61_000L
        var blockedAt = -1
        repeat(cap) { i ->
            if (blockedAt == -1 && AppwriteReadGuard.checkAndCount("edge") != null) blockedAt = i
        }
        assertTrue(
            "expected the burst to trip within one cap's worth of reads, blockedAt=$blockedAt",
            blockedAt in 0 until cap,
        )
    }

    @Test
    fun `sliding minute window frees up once the reads age out`() {
        repeat(CacheConfig.READ_GUARD_MAX_READS_PER_MINUTE) {
            AppwriteReadGuard.checkAndCount("burst")
        }
        assertEquals(AppwriteReadGuard.BlockReason.RATE_CAPPED, AppwriteReadGuard.checkAndCount("burst"))

        // Advance past both the pause and the full minute window.
        now += CacheConfig.READ_GUARD_RATE_CAP_PAUSE_MS + CacheConfig.READ_GUARD_MINUTE_WINDOW_MS
        repeat(CacheConfig.READ_GUARD_MAX_READS_PER_MINUTE - 1) {
            assertNull(AppwriteReadGuard.checkAndCount("burst"))
        }
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
    fun `storage 402 latches storage but leaves database reads working`() {
        // The whole point of per-quota breakers: a storage/bandwidth 402 must not switch off DB
        // reads. Previously any 402 opened the single shared breaker for six hours.
        AppwriteReadGuard.reportIfBudget402(
            "storage.downloadFile",
            AppwriteException("Storage limit for your organization has exceeded", 402, null),
        )
        assertEquals(1, reports.size)
        assertEquals("breaker_open_402", reports[0].second["reason"])
        assertEquals("STORAGE", reports[0].second["quota"])
        assertEquals(
            AppwriteReadGuard.BlockReason.BREAKER_OPEN,
            AppwriteReadGuard.checkAndCount("dl", AppwriteReadGuard.Quota.STORAGE),
        )
        assertNull(AppwriteReadGuard.checkAndCount("test", AppwriteReadGuard.Quota.DB_READ))
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

    /** In-memory [AppwriteReadGuard.BreakerStore] standing in for SharedPreferences. */
    private class FakeStore(
        private val saved: MutableMap<AppwriteReadGuard.Quota, AppwriteReadGuard.BreakerState> = mutableMapOf(),
        var legacy: AppwriteReadGuard.BreakerState? = null,
    ) : AppwriteReadGuard.BreakerStore {
        override fun save(quota: AppwriteReadGuard.Quota, state: AppwriteReadGuard.BreakerState?) {
            if (state == null) saved.remove(quota) else saved[quota] = state
        }

        override fun loadAll(): Map<AppwriteReadGuard.Quota, AppwriteReadGuard.BreakerState> = saved.toMap()

        override fun consumeLegacy(): AppwriteReadGuard.BreakerState? = legacy.also { legacy = null }

        fun isOpen(quota: AppwriteReadGuard.Quota) = saved.containsKey(quota)
    }

    @Test
    fun `persisted breaker latch is restored on init`() {
        val store = FakeStore()
        // First "process": a 402 latches and persists the breaker.
        AppwriteReadGuard.initPersistence(store)
        AppwriteReadGuard.recordFailure("test", budget402ByCode())
        assertTrue(store.isOpen(AppwriteReadGuard.Quota.DB_READ))

        // Second "process": in-memory reset, but init restores the latch from the store.
        AppwriteReadGuard.reset()
        AppwriteReadGuard.nowMs = { now }
        AppwriteReadGuard.initPersistence(store)
        assertEquals(AppwriteReadGuard.BlockReason.BREAKER_OPEN, AppwriteReadGuard.checkAndCount("test"))
    }

    @Test
    fun `legacy prefs latch migrates onto the DB_READ breaker`() {
        // Pre-per-quota installs persisted a single unlabelled latch. Dropping it on upgrade would
        // let a relaunch re-hit a budget that is still exhausted.
        val store = FakeStore(legacy = AppwriteReadGuard.BreakerState(0L, CacheConfig.READ_GUARD_PROBE_INTERVAL_MS))
        AppwriteReadGuard.initPersistence(store)

        assertEquals(AppwriteReadGuard.BlockReason.BREAKER_OPEN, AppwriteReadGuard.checkAndCount("test"))
        // Writes were never covered by the legacy latch, so they must stay open.
        assertNull(AppwriteReadGuard.checkAndCount("write", AppwriteReadGuard.Quota.DB_WRITE))
        // Consumed exactly once, and re-persisted under the new per-quota key.
        assertTrue(store.isOpen(AppwriteReadGuard.Quota.DB_READ))
        assertNull(store.legacy)
    }

    @Test
    fun `bandwidth 402 does not open the DB_READ breaker`() {
        AppwriteReadGuard.recordFailure(
            "matchDetail.fetch",
            AppwriteException("Bandwidth limit for your organization has exceeded", 402, null),
        )
        assertNull(AppwriteReadGuard.checkAndCount("matchDetail.fetch"))
        assertEquals(
            AppwriteReadGuard.BlockReason.BREAKER_OPEN,
            AppwriteReadGuard.checkAndCount("download", AppwriteReadGuard.Quota.BANDWIDTH),
        )
    }

    @Test
    fun `unattributed 402 opens only the calling funnel plus bandwidth with a shorter probe`() {
        // An unclassifiable message must not freeze every quota — that is far more damage than the
        // guess is worth. It scopes to the funnel that saw it, plus the shared bandwidth pool.
        AppwriteReadGuard.recordFailure(
            "executeFunction",
            AppwriteException("usage limit exceeded", 402, null),
            AppwriteReadGuard.Quota.EXECUTIONS,
        )
        assertEquals(
            AppwriteReadGuard.BlockReason.BREAKER_OPEN,
            AppwriteReadGuard.checkAndCount("fn", AppwriteReadGuard.Quota.EXECUTIONS),
        )
        assertEquals(
            AppwriteReadGuard.BlockReason.BREAKER_OPEN,
            AppwriteReadGuard.checkAndCount("dl", AppwriteReadGuard.Quota.BANDWIDTH),
        )
        // Untouched classes keep working — this assertion is the whole point of funnel scoping.
        assertNull(AppwriteReadGuard.checkAndCount("read", AppwriteReadGuard.Quota.DB_READ))
        assertNull(AppwriteReadGuard.checkAndCount("write", AppwriteReadGuard.Quota.DB_WRITE))
        assertNull(AppwriteReadGuard.checkAndCount("store", AppwriteReadGuard.Quota.STORAGE))

        // Shorter probe interval, because the attribution was inference rather than a stated quota.
        now += CacheConfig.READ_GUARD_UNKNOWN_402_PROBE_INTERVAL_MS
        assertNull(AppwriteReadGuard.checkAndCount("fn", AppwriteReadGuard.Quota.EXECUTIONS))
    }

    @Test
    fun `429 pauses without latching a breaker`() {
        AppwriteReadGuard.recordFailure("test", AppwriteException("Rate limit exceeded", 429, "general_rate_limit_exceeded"))
        assertEquals(AppwriteReadGuard.BlockReason.RATE_CAPPED, AppwriteReadGuard.checkAndCount("test"))
        // Self-clears like any rate cap — no probe interval, no persisted latch.
        now += CacheConfig.READ_GUARD_RATE_CAP_PAUSE_MS
        assertNull(AppwriteReadGuard.checkAndCount("test"))
    }

    @Test
    fun `write breaker is independent of the read breaker`() {
        AppwriteReadGuard.recordFailure(
            "write",
            AppwriteException("writes exceeded", 402, "limit_databases_writes_exceeded"),
            AppwriteReadGuard.Quota.DB_WRITE,
        )
        assertEquals(
            AppwriteReadGuard.BlockReason.BREAKER_OPEN,
            AppwriteReadGuard.checkAndCount("write", AppwriteReadGuard.Quota.DB_WRITE),
        )
        assertNull(AppwriteReadGuard.checkAndCount("read"))
    }

    @Test
    fun `write rate cap uses the write window not the read one`() {
        repeat(CacheConfig.WRITE_GUARD_MAX_WRITES_PER_MINUTE) {
            assertNull(AppwriteReadGuard.checkAndCount("w", AppwriteReadGuard.Quota.DB_WRITE))
        }
        assertEquals(
            AppwriteReadGuard.BlockReason.RATE_CAPPED,
            AppwriteReadGuard.checkAndCount("w", AppwriteReadGuard.Quota.DB_WRITE),
        )
        // Reads are counted separately and are nowhere near their own cap.
        assertNull(AppwriteReadGuard.checkAndCount("r"))
    }

    @Test
    fun `quotas without a local unit are breaker-only and never rate cap`() {
        // Bandwidth/storage/execution cost is server-side accounting; there is nothing meaningful
        // to count locally, so these react to a 402 but must not pre-emptively pause.
        repeat(CacheConfig.READ_GUARD_MAX_READS_PER_MINUTE * 2) {
            assertNull(AppwriteReadGuard.checkAndCount("dl", AppwriteReadGuard.Quota.BANDWIDTH))
        }
    }

    @Test
    fun `classify maps type strings and falls back to the message`() {
        assertEquals(
            AppwriteReadGuard.Quota.DB_READ,
            Appwrite402Classifier.classify(AppwriteException("x", 402, "limit_databases_reads_exceeded")),
        )
        assertEquals(
            AppwriteReadGuard.Quota.BANDWIDTH,
            Appwrite402Classifier.classify(AppwriteException("Bandwidth limit exceeded", 402, null)),
        )
        assertEquals(
            AppwriteReadGuard.Quota.EXECUTIONS,
            Appwrite402Classifier.classify(AppwriteException("x", 402, "limit_executions_exceeded")),
        )
        // Unattributable — must return null rather than guessing a quota.
        assertNull(Appwrite402Classifier.classify(AppwriteException("usage limit exceeded", 402, null)))
    }

    @Test
    fun `isBudget402 detects code and type`() {
        assertTrue(Appwrite402Classifier.isBudget402(budget402ByCode()))
        assertTrue(Appwrite402Classifier.isBudget402(budget402ByType()))
        assertEquals(false, Appwrite402Classifier.isBudget402(AppwriteException("nope", 500, "general_unknown")))
        assertEquals(false, Appwrite402Classifier.isBudget402(IOException("nope")))
    }
}
