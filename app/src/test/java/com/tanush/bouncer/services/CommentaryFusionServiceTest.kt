package com.tanush.bouncer.services

import com.tanush.bouncer.utils.contentFingerprint
import com.tanush.bouncer.data.local.dao.AICacheDao
import com.tanush.bouncer.data.local.entities.AICommentaryCacheEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Generation is entirely remote now, so these tests stub the one call the service makes.
 *
 * The provider-chain tests that used to live here — 429/502/timeout cooldowns, provider fallback,
 * malformed-response handling — were deleted rather than ported: that chain no longer exists on the
 * device, because its API keys were compiled into `BuildConfig` and shipped in the APK. The same
 * behaviours are now covered by `appwrite/functions/match-sync/test/` against the code that
 * actually performs them.
 */
class CommentaryFusionServiceTest {

    private lateinit var fakeDao: FakeAICacheDaoForFusion

    @Before
    fun setup() = runTest {
        fakeDao = FakeAICacheDaoForFusion()
        AICacheService.initialize(fakeDao)
        AICacheService.clearCache()
    }

    // ── Test helpers ────────────────────────────────────────────────────────

    /** One recorded call to the generation function. */
    private data class RemoteCall(
        val cacheKey: String,
        val prompt: String,
        val maxTokens: Int,
        val verify: Boolean,
        val context: CommentaryFusionService.MatchContext?,
    )

    private val calls = mutableListOf<RemoteCall>()

    /** A service whose remote generation returns [responder]'s answer — null meaning "unavailable". */
    private fun serviceWith(responder: (Int) -> String?) = CommentaryFusionService(
        generateRemote = { cacheKey, prompt, _, maxTokens, _, _, verify, context ->
            calls.add(RemoteCall(cacheKey, prompt, maxTokens, verify, context))
            responder(calls.size)
        }
    )

    // ── Default fuse params ──────────────────────────────────────────────────

    private val snippet1 = "Kohli drives it through covers"
    private val snippet2 = "Brilliant stroke play from Kohli"
    private val matchId = "test_match_001"
    private val over = 18
    private val ballNumber = "18.5"
    private val bowler = "Bumrah"
    private val batsman = "Kohli"
    private val scoreContext = "India 280/4 in 45.5 overs"
    private val oversSummary = "Last 3 overs: 24 runs, 1 wicket"

    private val inningsId = 2
    private val deliveryTimestamp = 1_785_344_368_208L

    /**
     * The delivery's identity, and nothing derived from a model.
     *
     * The key this replaced hashed `oversSummary` — an LLM paragraph regenerated every poll — so it
     * changed on every call and the cache never hit.
     */
    private fun fuseCacheKey(): String = "ai_commentary:$matchId:$inningsId:$deliveryTimestamp"

    /** Used when the source has no per-item metadata (legacy CSS scrape). */
    private fun fuseCacheKeyWithoutTimestamp(): String =
        "ai_commentary:$matchId:$inningsId:${contentFingerprint(snippet1, snippet2)}"

    private suspend fun defaultFuse(
        service: CommentaryFusionService,
        oversSummary: String = this.oversSummary,
        deliveryTimestamp: Long = this.deliveryTimestamp
    ) = service.fuse(
        snippet1 = snippet1,
        snippet2 = snippet2,
        oversSummary = oversSummary,
        matchId = matchId,
        over = over,
        ballNumber = ballNumber,
        bowler = bowler,
        batsman = batsman,
        scoreContext = scoreContext,
        inningsId = inningsId,
        deliveryTimestamp = deliveryTimestamp
    )

    // ── fuse() tests ─────────────────────────────────────────────────────────

    @Test
    fun `fuse happy path - returns generated commentary and caches result`() = runTest {
        val aiOutput = "18.5: Kohli drives it elegantly through the covers for four!"
        val service = serviceWith { aiOutput }

        val result = defaultFuse(service)

        assertEquals(aiOutput, result)
        assertEquals(aiOutput, AICacheService.getCommentary(fuseCacheKey()))
        assertEquals(1, calls.size)
    }

    @Test
    fun `fuse asks the function to verify, and hands it the sources to check against`() = runTest {
        // The hallucination check runs where the provider keys are. If the snippets do not travel
        // with the request, the fact-checker has nothing to check against and passes everything.
        val service = serviceWith { "18.5: a clean line" }

        defaultFuse(service)

        val call = calls.single()
        assertTrue("fuse must request verification", call.verify)
        assertEquals(snippet1, call.context?.snippet1)
        assertEquals(snippet2, call.context?.snippet2)
        assertEquals(ballNumber, call.context?.ballNumber)
        assertEquals(bowler, call.context?.bowler)
        assertEquals(batsman, call.context?.batsman)
    }

    @Test
    fun `fuse falls back to the raw snippet when generation is unavailable`() = runTest {
        val service = serviceWith { null }

        val result = defaultFuse(service)

        assertEquals(snippet1, result)
    }

    @Test
    fun `the raw fallback is never cached as if it were generated`() = runTest {
        // It used to be, with permanent = isMatchComplete — so one failed generation for a finished
        // match pinned scraper text in the shared Appwrite cache with a 2099 expiry, for every
        // device. A failed generation must be retried, not memorialised.
        val service = serviceWith { null }

        assertEquals(snippet1, defaultFuse(service))

        assertNull(
            "the raw source line must not occupy the delivery's cache key",
            AICacheService.getCommentary(fuseCacheKey())
        )
    }

    @Test
    fun `fuse cache hit - skips the remote call entirely`() = runTest {
        AICacheService.setCommentary("Cached commentary", fuseCacheKey())
        val service = serviceWith { "Should not be called" }

        val result = defaultFuse(service)

        assertEquals("Cached commentary", result)
        assertEquals(0, calls.size)
    }

    @Test
    fun `fuse cache hits even though the recap changed`() = runTest {
        // The regression this guards. The recap is regenerated at temperature 0.4 on every poll, so
        // hashing it into the key meant a delivery was re-fused every time it was polled: one ball
        // drew 54 distinct keys in four minutes, and across 602 cached rows not one was ever
        // updated. The key must depend on the delivery, not on anything a model produced.
        AICacheService.setCommentary("Cached commentary", fuseCacheKey())
        val service = serviceWith { "Should not be called" }

        val result = defaultFuse(service, oversSummary = "A completely different recap paragraph")

        assertEquals("Cached commentary", result)
        assertEquals(0, calls.size)
    }

    @Test
    fun `fuse without a delivery timestamp keys on the snippets alone`() = runTest {
        // Legacy CSS scrape: no per-item metadata. Still stable across polls, because the fallback
        // fingerprints only the source snippets — never the recap.
        AICacheService.setCommentary("Cached commentary", fuseCacheKeyWithoutTimestamp())
        val service = serviceWith { "Should not be called" }

        val result = defaultFuse(
            service,
            oversSummary = "Another different recap",
            deliveryTimestamp = 0L
        )

        assertEquals("Cached commentary", result)
        assertEquals(0, calls.size)
    }

    @Test
    fun `fuse single source - uses the single-source prompt when snippet2 is empty`() = runTest {
        val aiOutput = "18.5: Kohli cuts hard to point boundary!"
        val service = serviceWith { aiOutput }

        val result = service.fuse(
            snippet1 = snippet1,
            snippet2 = "",
            oversSummary = oversSummary,
            matchId = matchId,
            over = over,
            ballNumber = ballNumber,
            bowler = bowler,
            batsman = batsman,
            scoreContext = scoreContext
        )

        assertEquals(aiOutput, result)
        val prompt = calls.single().prompt
        assertTrue("single-source prompt carries the one snippet", prompt.contains(snippet1))
        assertFalse("and must not offer an empty second source", prompt.contains("Commentary from source 2"))
    }

    @Test
    fun `previous-ball buffer does not grow with every match watched`() = runTest {
        val service = serviceWith { "unused" }

        // The inner per-match list was always trimmed, but the outer map only ever gained keys — one
        // per match id, for the life of the process.
        repeat(12) { service.addBallToBuffer("match_$it", "18.$it ball text") }

        assertTrue("the most recent match must still be buffered", service.getPreviousBalls("match_11").isNotEmpty())
        assertTrue("the oldest match must have been evicted", service.getPreviousBalls("match_0").isEmpty())
    }

    // ── generateBallCommentary() tests ───────────────────────────────────────

    @Test
    fun `ball commentary falls back to the fixed placeholder when generation is unavailable`() = runTest {
        val service = serviceWith { null }

        val result = service.generateBallCommentary(
            matchId = matchId,
            ballNumber = ballNumber,
            rawSnippet = snippet1,
        )

        assertEquals(CommentaryFusionService.FALLBACK_BALL_COMMENTARY, result)
    }

    // ── summarizeOversWithAI() tests ─────────────────────────────────────────

    @Test
    fun `summarizeOversWithAI happy path - returns and caches summary`() = runTest {
        val summary = "India dominated, scoring 34 runs in last 3 overs."
        val lines = listOf("Kohli hits four", "Rohit blocks", "Bumrah bowled")
        val service = serviceWith { summary }

        val result = service.summarizeOversWithAI(lines, matchId, over)

        assertEquals(summary, result)
        val cacheKey = "ai_summary:$matchId:$over:${contentFingerprint(lines.joinToString(" "))}"
        assertEquals(summary, AICacheService.getCommentary(cacheKey))
    }

    @Test
    fun `a recap does not pay for verification it cannot use`() = runTest {
        // There are no per-ball source snippets behind a recap, so a PASS/FAIL verdict would be
        // meaningless — and would double the call cost of every over summary.
        val service = serviceWith { "A recap paragraph" }

        service.summarizeOversWithAI(listOf("Ball 1", "Ball 2"), matchId, over)

        assertFalse("recap generation must not request verification", calls.single().verify)
    }

    @Test
    fun `summarizeOversWithAI empty lines - returns without a remote call`() = runTest {
        val service = serviceWith { "Should not be called" }

        val result = service.summarizeOversWithAI(emptyList(), matchId, over)

        assertEquals("No recent commentary available.", result)
        assertEquals(0, calls.size)
    }

    @Test
    fun `summarizeOversWithAI cache hit - skips the remote call`() = runTest {
        val lines = listOf("Ball 1", "Ball 2")
        val cacheKey = "ai_summary:$matchId:$over:${contentFingerprint(lines.joinToString(" "))}"
        AICacheService.setCommentary("Cached summary", cacheKey)
        val service = serviceWith { "Should not be called" }

        val result = service.summarizeOversWithAI(lines, matchId, over)

        assertEquals("Cached summary", result)
        assertEquals(0, calls.size)
    }

    @Test
    fun `summarizeOversWithAI generation fails - returns the placeholder without caching it`() = runTest {
        val service = serviceWith { null }

        val result = service.summarizeOversWithAI(listOf("Ball 1", "Ball 2"), matchId, over)

        assertEquals("[Summary unavailable]", result)
    }

    @Test
    fun `summarizeOversWithAI caps input at 18 balls`() = runTest {
        val service = serviceWith { "Summary" }

        val lines = (1..25).map { "Ball $it commentary" }
        service.summarizeOversWithAI(lines, matchId, over)

        val numbered = calls.single().prompt.lines().count { it.trim().matches(Regex("\\d+\\..*")) }
        assertEquals(24, numbered)
    }

    // ── ball buffer tests ────────────────────────────────────────────────────

    @Test
    fun `addBallToBuffer and getPreviousBalls round-trip`() = runTest {
        val svc = CommentaryFusionService()
        svc.addBallToBuffer("m1", "Ball 1")
        svc.addBallToBuffer("m1", "Ball 2")
        assertEquals(listOf("Ball 1", "Ball 2"), svc.getPreviousBalls("m1"))
    }

    @Test
    fun `ball buffer caps at 6 entries and evicts oldest`() = runTest {
        val svc = CommentaryFusionService()
        (1..8).forEach { svc.addBallToBuffer("m2", "Ball $it") }

        val buf = svc.getPreviousBalls("m2")
        assertEquals(6, buf.size)
        assertEquals("Ball 3", buf.first())
        assertEquals("Ball 8", buf.last())
    }

    @Test
    fun `getPreviousBalls unknown match returns empty list`() = runTest {
        val svc = CommentaryFusionService()
        assertEquals(emptyList<String>(), svc.getPreviousBalls("no_such_match"))
    }

    @Test
    fun `ball buffers are isolated per matchId`() = runTest {
        val svc = CommentaryFusionService()
        svc.addBallToBuffer("matchA", "A1")
        svc.addBallToBuffer("matchB", "B1")

        assertEquals(listOf("A1"), svc.getPreviousBalls("matchA"))
        assertEquals(listOf("B1"), svc.getPreviousBalls("matchB"))
    }
}

// ── Fake DAO ────────────────────────────────────────────────────────────────

private class FakeAICacheDaoForFusion : AICacheDao {
    val entries = mutableMapOf<String, AICommentaryCacheEntity>()

    override suspend fun getCommentary(key: String, cutoffMs: Long): String? =
        entries[key]?.takeIf { it.timestamp > cutoffMs }?.commentary

    override suspend fun insert(entity: AICommentaryCacheEntity) {
        entries[entity.cacheKey] = entity
    }

    override suspend fun deleteExpired(cutoffMs: Long) {
        entries.entries.removeIf { it.value.timestamp < cutoffMs }
    }

    override suspend fun touch(key: String, atMs: Long) {
        entries[key]?.let { entries[key] = it.copy(lastAccessedAt = atMs) }
    }

    override suspend fun markSynced(key: String, atMs: Long) {
        entries[key]?.let { entries[key] = it.copy(syncedAt = atMs) }
    }

    override suspend fun unsyncedPermanent(permanentTimestamp: Long, limit: Int): List<AICommentaryCacheEntity> =
        entries.values
            .filter { it.syncedAt == null && it.timestamp == permanentTimestamp }
            .sortedByDescending { it.lastAccessedAt }
            .take(limit)

    override suspend fun countAll(): Int = entries.size

    override suspend fun enforceRowCeiling(keepRows: Int) {
        val keep = entries.values
            .sortedWith(compareByDescending<AICommentaryCacheEntity> { it.lastAccessedAt }
                .thenByDescending { it.timestamp })
            .take(keepRows)
            .map { it.cacheKey }
            .toSet()
        entries.entries.removeIf { it.key !in keep }
    }

    override suspend fun clearAll() {
        entries.clear()
    }
}
