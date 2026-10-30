package com.tanush.bouncer.data

import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.data.remote.FunctionOutcome
import com.tanush.bouncer.data.repository.CricbuzzNewsRepository
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The real thing: `stale-cache-response.json` is the exact 6293-byte body
 * `bouncer-news-sync` returned on 2026-08-06, captured from a live execution while the Cricbuzz
 * RapidAPI plan was still answering 429. It is the payload a device gets today.
 *
 * A hand-written fixture proves the parser handles what the parser's author imagined. This one
 * proves it handles what production actually sends — including the two `ad` entries interleaved
 * with the stories, which is the shape that made `storyList` nullable in the first place.
 */
class NewsProductionPayloadTest {

    private val repository = CricbuzzNewsRepository()

    private val payload: String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("news/stale-cache-response.json")) {
            "missing captured production payload"
        }.bufferedReader().readText()

    @Before
    fun setUp() {
        mockkObject(AppwriteClient)
        coEvery { AppwriteClient.executeFunctionDetailed(any(), any()) } returns
            FunctionOutcome.Success(payload)
    }

    @After
    fun tearDown() = unmockkObject(AppwriteClient)

    @Test
    fun `the live payload becomes a full screen of articles`() = runTest {
        val headlines = repository.fetchHeadlines()

        // 12 storyList entries: 10 stories and 2 ads. Every story must survive to a card.
        assertEquals(10, headlines.articles.size)
        assertTrue("every article needs a headline", headlines.articles.all { it.headline.isNotBlank() })
        assertTrue("every article needs an id", headlines.articles.all { it.id > 0 })
        assertTrue("every article needs a tappable URL", headlines.articles.all { it.articleUrl.startsWith("https://") })
        assertEquals(true, headlines.articles.first().isTopStory)
    }

    @Test
    fun `the live payload is recognised as stale and dated`() = runTest {
        val headlines = repository.fetchHeadlines()

        assertEquals("this body was served from cache behind a 429", true, headlines.isStale)
        val age = headlines.staleAgeMs
        assertNotNull(age)
        // ~7.2 days when captured. Asserting the band, not the value, so the fixture stays valid.
        assertTrue("age should be days, not minutes", age!! > 24L * 60 * 60 * 1000)
    }

    @Test
    fun `publication times parse rather than falling back to now`() = runTest {
        val headlines = repository.fetchHeadlines()
        val now = System.currentTimeMillis()

        // pubTime is the field that used to NPE. A silent fallback to `now` would make week-old
        // headlines all read "Just now", so the parse has to genuinely land in the past.
        assertTrue(
            "every story in this capture carries a real pubTime",
            headlines.articles.all { it.publishedTime in 1..now },
        )
    }
}
