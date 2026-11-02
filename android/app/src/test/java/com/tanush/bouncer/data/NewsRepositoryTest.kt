package com.tanush.bouncer.data

import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.data.remote.FunctionOutcome
import com.tanush.bouncer.data.repository.CricbuzzNewsRepository
import com.tanush.bouncer.data.repository.NewsUnavailableException
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Parsing of the `bouncer-news-sync` response, and what each failure shape becomes.
 *
 * Two things this pins. First, the status code has to survive: a 429 from an exhausted Cricbuzz
 * plan and a latched quota breaker used to reach the ViewModel as the same fixed string, and the
 * screen then tried to recover the difference by substring-matching that string. Second, every
 * field on the wire is optional in practice — Gson allocates through Unsafe, so a `String`
 * declaration guarantees nothing, and a missing `pubTime` was a live NPE (638c0cc).
 */
class NewsRepositoryTest {

    private val repository = CricbuzzNewsRepository()

    @Before
    fun setUp() = mockkObject(AppwriteClient)

    @After
    fun tearDown() = unmockkObject(AppwriteClient)

    private suspend fun respondWith(body: String) {
        coEvery { AppwriteClient.executeFunctionDetailed(any(), any()) } returns
            FunctionOutcome.Success(body)
    }

    @Test
    fun `maps stories and skips ad entries`() = runTest {
        respondWith(
            """
            {"storyList":[
              {"story":{"id":1,"hline":"Kohli tons up","intro":"i","pubTime":"1754400000000",
                        "source":"Cricbuzz","storyType":"News","imageId":9}},
              {"ad":{"name":"banner","layout":"full","position":2}},
              {"story":{"id":2,"hline":"Bumrah five-for","intro":"i","pubTime":"1754400000000",
                        "source":"Cricbuzz","storyType":"News"}}
            ]}
            """.trimIndent()
        )

        val headlines = repository.fetchHeadlines()

        assertEquals(listOf(1L, 2L), headlines.articles.map { it.id })
        assertEquals("Kohli tons up", headlines.articles[0].headline)
        assertEquals(true, headlines.articles[0].isTopStory)
        assertEquals(false, headlines.isStale)
    }

    @Test
    fun `an absent storyList is an empty list, not a crash`() = runTest {
        respondWith("""{"lastUpdatedTime":"1754400000000"}""")
        assertEquals(emptyList<Long>(), repository.fetchHeadlines().articles.map { it.id })
    }

    @Test
    fun `a story missing pubTime does not crash and falls back to now`() = runTest {
        respondWith("""{"storyList":[{"story":{"id":7,"hline":"No timestamp"}}]}""")

        val articles = repository.fetchHeadlines().articles

        assertEquals(1, articles.size)
        assertTrue("publishedTime should fall back to a real clock", articles[0].publishedTime > 0)
        assertEquals("", articles[0].source)
    }

    @Test
    fun `a story with no id or no headline is dropped rather than rendered blank`() = runTest {
        respondWith(
            """
            {"storyList":[
              {"story":{"hline":"No id"}},
              {"story":{"id":3}},
              {"story":{"id":4,"hline":"   "}},
              {"story":{"id":5,"hline":"Keeps this one"}}
            ]}
            """.trimIndent()
        )

        assertEquals(listOf(5L), repository.fetchHeadlines().articles.map { it.id })
    }

    @Test
    fun `the server's staleness markers reach the caller`() = runTest {
        respondWith(
            """{"storyList":[{"story":{"id":1,"hline":"Old news"}}],
                "_bouncerStale":true,"_bouncerCacheAgeMs":432000000}"""
        )

        val headlines = repository.fetchHeadlines()

        assertEquals(true, headlines.isStale)
        assertEquals(432_000_000L, headlines.staleAgeMs)
    }

    @Test
    fun `a stale serve the server could not date has no age`() = runTest {
        respondWith("""{"storyList":[{"story":{"id":1,"hline":"Old news"}}],"_bouncerStale":true}""")

        val headlines = repository.fetchHeadlines()

        assertEquals(true, headlines.isStale)
        assertNull(headlines.staleAgeMs)
    }

    @Test
    fun `an http error carries its status code`() = runTest {
        coEvery { AppwriteClient.executeFunctionDetailed(any(), any()) } returns
            FunctionOutcome.HttpError(429, """{"error":"Upstream error: 429"}""")

        val thrown = runCatching { repository.fetchHeadlines() }.exceptionOrNull()

        assertTrue(thrown is NewsUnavailableException)
        assertEquals(429, (thrown as NewsUnavailableException).statusCode)
    }

    @Test
    fun `an unavailable execution carries no status code`() = runTest {
        coEvery { AppwriteClient.executeFunctionDetailed(any(), any()) } returns
            FunctionOutcome.Unavailable

        val thrown = runCatching { repository.fetchHeadlines() }.exceptionOrNull()

        assertTrue(thrown is NewsUnavailableException)
        assertNull((thrown as NewsUnavailableException).statusCode)
    }

    @Test
    fun `a 200 that is not the news shape is an error, not an empty news day`() = runTest {
        respondWith("<html>gateway timeout</html>")

        val thrown = runCatching { repository.fetchHeadlines() }.exceptionOrNull()

        assertTrue(thrown is NewsUnavailableException)
    }

    @Test
    fun `an empty body is an error, not an empty news day`() = runTest {
        respondWith("")

        val thrown = runCatching { repository.fetchHeadlines() }.exceptionOrNull()

        assertTrue(thrown is NewsUnavailableException)
    }
}
