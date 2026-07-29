package com.tanush.bouncer.services

import com.tanush.bouncer.utils.contentFingerprint
import com.tanush.bouncer.data.local.dao.AICacheDao
import com.tanush.bouncer.data.local.entities.AICommentaryCacheEntity
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

class CommentaryFusionServiceTest {

    private lateinit var fakeDao: FakeAICacheDaoForFusion

    @Before
    fun setup() = runTest {
        fakeDao = FakeAICacheDaoForFusion()
        AICacheService.initialize(fakeDao)
        AICacheService.clearCache()
    }

    // ── Test helpers ────────────────────────────────────────────────────────

    private fun serviceWith(client: OkHttpClient) = CommentaryFusionService(
        client = client,
        testProviders = listOf(
            Triple("Groq",      "test-key", "test-model"),
            Triple("Cerebras",  "test-key", "test-model"),
            Triple("SambaNova", "test-key", "test-model"),
        )
    )

    private fun readRequestBodyJson(chain: okhttp3.Interceptor.Chain): JSONObject {
        val buffer = Buffer()
        chain.request().body?.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    private fun successBody(content: String): String =
        """{"choices":[{"message":{"content":"$content"}}]}"""

    private fun errorBody(): String = """{"error":{"message":"upstream error"}}"""

    /**
     * Builds a fake OkHttpClient whose interceptor calls [handler] with
     * (callIndex starting at 1, request body JSON) and returns the result
     * as an HTTP response.
     */
    private fun fakeClient(handler: (Int, JSONObject) -> Pair<Int, String>): OkHttpClient {
        val callCount = AtomicInteger(0)
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val n = callCount.incrementAndGet()
                val reqJson = readRequestBodyJson(chain)
                val (status, body) = handler(n, reqJson)
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(status)
                    .message(if (status == 200) "OK" else "Error")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
    }

    private fun throwingClient(ex: Exception = IOException("Connection refused")): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { throw ex }.build()

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

    private fun fuseCacheKey(): String {
        val hash = contentFingerprint(snippet1, snippet2, oversSummary)
        return "ai_commentary:$matchId:$over:$ballNumber:$hash"
    }

    private suspend fun defaultFuse(service: CommentaryFusionService) = service.fuse(
        snippet1 = snippet1,
        snippet2 = snippet2,
        oversSummary = oversSummary,
        matchId = matchId,
        over = over,
        ballNumber = ballNumber,
        bowler = bowler,
        batsman = batsman,
        scoreContext = scoreContext
    )

    // ── fuse() tests ─────────────────────────────────────────────────────────

    @Test
    fun `fuse happy path - returns AI commentary and caches result`() = runTest {
        val aiOutput = "18.5: Kohli drives it elegantly through the covers for four!"
        val service = serviceWith(fakeClient { n, _ ->
            when (n) {
                1 -> 200 to successBody(aiOutput) // Groq fusion
                2 -> 200 to successBody("PASS")   // Groq verification
                else -> 500 to errorBody()
            }
        })

        val result = defaultFuse(service)

        assertEquals(aiOutput, result)
        assertEquals(aiOutput, AICacheService.getCommentary(fuseCacheKey()))
    }

    @Test
    fun `fuse verification fails - falls back to raw snippet1`() = runTest {
        val hallucinated = "18.5: Smith smashes it for six!"
        val service = serviceWith(fakeClient { n, _ ->
            when (n) {
                1 -> 200 to successBody(hallucinated) // Groq fusion: success (hallucinated)
                2 -> 200 to successBody("FAIL")       // Groq verification: FAIL
                else -> 500 to errorBody()            // retry generation fails → fall back to snippet1
            }
        })

        val result = defaultFuse(service)

        assertEquals(snippet1, result)
    }

    @Test
    fun `fuse cache hit - skips HTTP calls entirely`() = runTest {
        AICacheService.setCommentary("Cached commentary", fuseCacheKey())

        var httpCalls = 0
        val service = serviceWith(fakeClient { _, _ ->
            httpCalls++
            200 to successBody("Should not be called")
        })

        val result = defaultFuse(service)

        assertEquals("Cached commentary", result)
        assertEquals(0, httpCalls)
    }

    @Test
    fun `fuse 429 - all providers rate-limited, second call skips HTTP`() = runTest {
        var httpCalls = 0
        val service = serviceWith(fakeClient { _, _ ->
            httpCalls++
            429 to """{"error":{"message":"Rate limited"}}"""
        })

        // First call: all 3 providers hit 429 and enter 30s cooldown.
        val result1 = defaultFuse(service)
        assertEquals(snippet1, result1)
        val callsAfterFirst = httpCalls

        // Second call: all providers still in cooldown — zero new HTTP calls.
        val result2 = defaultFuse(service)
        assertEquals(snippet1, result2)
        assertEquals(callsAfterFirst, httpCalls)
    }

    @Test
    fun `fuse 502 - a down provider enters cooldown just like a rate-limited one`() = runTest {
        var httpCalls = 0
        val service = serviceWith(fakeClient { _, _ ->
            httpCalls++
            502 to errorBody()
        })

        // Before this group only a 429 opened a cooldown. A 502 threw straight through, so the same
        // dead provider was re-dialled on every ball, forever.
        assertEquals(snippet1, defaultFuse(service))
        val callsAfterFirst = httpCalls
        assertTrue("expected the first fuse to actually hit the network", callsAfterFirst > 0)

        assertEquals(snippet1, defaultFuse(service))
        assertEquals(callsAfterFirst, httpCalls)
    }

    @Test
    fun `fuse timeout - a hung provider enters cooldown`() = runTest {
        var httpCalls = 0
        val service = serviceWith(
            OkHttpClient.Builder()
                .addInterceptor {
                    httpCalls++
                    throw SocketTimeoutException("timeout")
                }
                .build()
        )

        assertEquals(snippet1, defaultFuse(service))
        val callsAfterFirst = httpCalls
        assertTrue("expected the first fuse to actually hit the network", callsAfterFirst > 0)

        assertEquals(snippet1, defaultFuse(service))
        assertEquals(callsAfterFirst, httpCalls)
    }

    @Test
    fun `fuse 400 - a bad request does not cool the provider down`() = runTest {
        var httpCalls = 0
        val service = serviceWith(fakeClient { _, _ ->
            httpCalls++
            400 to errorBody()
        })

        // A 4xx that is not 429 is our request being wrong — a bad model id, a malformed body, a
        // revoked key. Backing off only delays a failure that will repeat identically, so the chain
        // must keep falling through to the next provider.
        assertEquals(snippet1, defaultFuse(service))
        val callsAfterFirst = httpCalls
        assertEquals(snippet1, defaultFuse(service))
        assertTrue(
            "a 400 must not open a cooldown (calls: $callsAfterFirst then $httpCalls)",
            httpCalls > callsAfterFirst,
        )
    }

    @Test
    fun `previous-ball buffer does not grow with every match watched`() = runTest {
        val service = serviceWith(fakeClient { _, _ -> 200 to successBody("unused") })

        // The inner per-match list was always trimmed, but the outer map only ever gained keys — one
        // per match id, for the life of the process.
        repeat(12) { service.addBallToBuffer("match_$it", "18.$it ball text") }

        assertTrue("the most recent match must still be buffered", service.getPreviousBalls("match_11").isNotEmpty())
        assertTrue("the oldest match must have been evicted", service.getPreviousBalls("match_0").isEmpty())
    }

    @Test
    fun `fuse all providers fail - returns static fallback string`() = runTest {
        val service = serviceWith(fakeClient { _, _ -> 500 to errorBody() })

        val result = defaultFuse(service)

        assertEquals(snippet1, result)
    }

    @Test
    fun `fuse provider fallback - first provider 500 then second succeeds`() = runTest {
        val aiOutput = "18.5: Kohli punches it through covers!"
        // Call 1: Groq fusion → 500 (fail, try next)
        // Call 2: Cerebras fusion → 200 (success)
        // Call 3: Groq verification → 200 PASS (providers reset between calls)
        val service = serviceWith(fakeClient { n, _ ->
            when (n) {
                1 -> 500 to errorBody()            // Groq fusion: fail
                2 -> 200 to successBody(aiOutput)  // Cerebras fusion: succeed
                else -> 200 to successBody("PASS") // verification
            }
        })

        val result = defaultFuse(service)
        assertEquals(aiOutput, result)
    }

    @Test
    fun `fuse single source - uses single-source prompt when snippet2 is empty`() = runTest {
        val aiOutput = "18.5: Kohli cuts hard to point boundary!"
        val service = serviceWith(fakeClient { n, _ ->
            when (n) {
                1 -> 200 to successBody(aiOutput)
                else -> 200 to successBody("PASS")
            }
        })

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
    }

    @Test
    fun `fuse IO exception - returns static fallback string`() = runTest {
        val service = serviceWith(throwingClient())

        val result = defaultFuse(service)

        assertEquals(snippet1, result)
    }

    @Test
    fun `fuse empty choices array - returns static fallback string`() = runTest {
        val service = serviceWith(fakeClient { _, _ ->
            200 to """{"choices":[]}"""
        })

        val result = defaultFuse(service)
        assertEquals(snippet1, result)
    }

    // ── summarizeOversWithAI() tests ─────────────────────────────────────────

    @Test
    fun `summarizeOversWithAI happy path - returns and caches summary`() = runTest {
        val summary = "India dominated, scoring 34 runs in last 3 overs."
        val lines = listOf("Kohli hits four", "Rohit blocks", "Bumrah bowled")
        val service = serviceWith(fakeClient { _, _ -> 200 to successBody(summary) })

        val result = service.summarizeOversWithAI(lines, matchId, over)

        assertEquals(summary, result)
        val cacheKey = "ai_summary:$matchId:$over:${contentFingerprint(lines.joinToString(" "))}"
        assertEquals(summary, AICacheService.getCommentary(cacheKey))
    }

    @Test
    fun `summarizeOversWithAI empty lines - returns without HTTP call`() = runTest {
        var httpCalls = 0
        val service = serviceWith(fakeClient { _, _ ->
            httpCalls++
            200 to successBody("Should not be called")
        })

        val result = service.summarizeOversWithAI(emptyList(), matchId, over)

        assertEquals("No recent commentary available.", result)
        assertEquals(0, httpCalls)
    }

    @Test
    fun `summarizeOversWithAI cache hit - skips HTTP call`() = runTest {
        val lines = listOf("Ball 1", "Ball 2")
        val cacheKey = "ai_summary:$matchId:$over:${contentFingerprint(lines.joinToString(" "))}"
        AICacheService.setCommentary("Cached summary", cacheKey)

        var httpCalls = 0
        val service = serviceWith(fakeClient { _, _ ->
            httpCalls++
            200 to successBody("Should not be called")
        })

        val result = service.summarizeOversWithAI(lines, matchId, over)

        assertEquals("Cached summary", result)
        assertEquals(0, httpCalls)
    }

    @Test
    fun `summarizeOversWithAI API fails - returns fallback string`() = runTest {
        val service = serviceWith(fakeClient { _, _ -> 500 to errorBody() })

        val result = service.summarizeOversWithAI(listOf("Ball 1", "Ball 2"), matchId, over)

        assertEquals("[Summary unavailable]", result)
    }

    @Test
    fun `summarizeOversWithAI caps input at 18 balls`() = runTest {
        var capturedLineCount = 0
        val service = serviceWith(fakeClient { _, reqJson ->
            val content = reqJson.optJSONArray("messages")
                ?.optJSONObject(0)
                ?.optString("content") ?: ""
            capturedLineCount = content.lines()
                .count { it.trim().matches(Regex("\\d+\\..*")) }
            200 to successBody("Summary")
        })

        val lines = (1..25).map { "Ball $it commentary" }
        service.summarizeOversWithAI(lines, matchId, over)

        assertEquals(24, capturedLineCount)
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
