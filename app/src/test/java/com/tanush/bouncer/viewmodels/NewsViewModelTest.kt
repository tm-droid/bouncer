package com.tanush.bouncer.viewmodels

import androidx.lifecycle.ViewModelStore
import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.data.repository.Headlines
import com.tanush.bouncer.data.repository.NewsRepository
import com.tanush.bouncer.data.repository.NewsUnavailableException
import com.tanush.bouncer.models.NewsArticleData
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The News tab was blank for five days and could not be recovered without restarting the app.
 *
 * The upstream cause was a 429 from the Cricbuzz RapidAPI plan, but the reason it presented as
 * *nothing* rather than *an error* was `refresh()`: it set `_isRefreshing = true` and then called
 * `fetchNews()`, whose first line returns early when `_isRefreshing` is already true. It also
 * cleared `_error` before that dead call, so tapping Retry moved the screen from the error state
 * (which had a Retry button) to the "No news yet" state (which did not).
 *
 * These tests pin the three things that were wrong: a refresh must fetch, a failure must stay
 * visible, and the HTTP status must survive far enough to pick the message.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // Without this the ViewModel short-circuits on "News is unavailable in this build."
        mockkObject(AppwriteClient)
        every { AppwriteClient.isReady } returns true
    }

    @After
    fun tearDown() {
        unmockkObject(AppwriteClient)
        Dispatchers.resetMain()
    }

    private class FakeNewsRepository(
        var result: () -> Headlines = { Headlines(listOf(article(1))) },
    ) : NewsRepository {
        var headlineCalls = 0
            private set

        /** Observed at the moment the fetch is entered, so a caller can assert on in-flight state. */
        var onFetchEntered: (() -> Unit)? = null

        override suspend fun fetchHeadlines(): Headlines {
            headlineCalls++
            onFetchEntered?.invoke()
            return result()
        }

        override suspend fun fetchArticleBody(url: String): List<String> = emptyList()
    }

    /** Let the ViewModel's launched work run without advancing into the 4-hour auto-refresh. */
    private fun TestScope.settle() {
        advanceTimeBy(SETTLE_MS)
        runCurrent()
    }

    /**
     * `startAutoRefresh` is a `while (true) { delay(4h) }` loop on `viewModelScope`, which shares
     * this test's scheduler. `runTest` drains the scheduler when the body returns, and a loop that
     * always has one more delayed task never drains — it spins on virtual time forever. So the
     * ViewModel has to be cleared before that happens; `ViewModelStore.clear()` is the public route
     * to `onCleared()`, which cancels the job. Inside [body], only ever advance with [settle] —
     * `advanceUntilIdle` hangs there for the same reason.
     */
    private fun TestScope.withViewModel(repo: NewsRepository, body: (NewsViewModel) -> Unit) {
        val store = ViewModelStore()
        val viewModel = NewsViewModel(repo)
        store.put("news", viewModel)
        try {
            body(viewModel)
        } finally {
            store.clear()
            advanceUntilIdle()
        }
    }

    @Test
    fun `refreshNews reaches the repository`() = runTest(dispatcher) {
        val repo = FakeNewsRepository()
        withViewModel(repo) { viewModel ->
            settle()
            assertEquals("init should fetch once", 1, repo.headlineCalls)

            viewModel.refreshNews()
            settle()

            assertEquals("refresh must reach the repository", 2, repo.headlineCalls)
        }
    }

    /**
     * The exact shape of the bug: `refresh()` raised `_isRefreshing` and *then* called
     * `fetchNews()`, which returns early on that very flag. The spinner span and the fetch have to
     * be the same event — a refresh that is visibly in progress but never calls anything is what
     * made the tab unrecoverable. Only `fetchNews` may own the flag.
     */
    @Test
    fun `the refresh flag is raised by the fetch, not instead of it`() = runTest(dispatcher) {
        val repo = FakeNewsRepository()
        withViewModel(repo) { viewModel ->
            settle()

            var refreshingDuringFetch: Boolean? = null
            repo.onFetchEntered = { refreshingDuringFetch = viewModel.isRefreshing.value }
            viewModel.refreshNews()
            settle()

            assertEquals("the fetch must actually be entered", 2, repo.headlineCalls)
            assertEquals("and it must be entered while the spinner is up", true, refreshingDuringFetch)
            assertEquals("the flag must be released afterwards", false, viewModel.isRefreshing.value)
        }
    }

    @Test
    fun `a failed refresh keeps the error visible and does not discard loaded articles`() = runTest(dispatcher) {
        val repo = FakeNewsRepository()
        withViewModel(repo) { viewModel ->
            settle()

            repo.result = { throw NewsUnavailableException(statusCode = 500) }
            viewModel.refreshNews()
            settle()

            assertNotNull("error must survive the refresh", viewModel.error.value)
            assertEquals("stale articles beat a blank tab", 1, viewModel.articles.value.size)
            assertEquals(false, viewModel.isRefreshing.value)
            assertEquals(false, viewModel.isLoading.value)
        }
    }

    @Test
    fun `a 429 says rate limited, not something went wrong`() = runTest(dispatcher) {
        withViewModel(FakeNewsRepository { throw NewsUnavailableException(statusCode = 429) }) { viewModel ->
            settle()
            assertEquals(
                "News is temporarily rate limited. Please try again later.",
                viewModel.error.value,
            )
        }
    }

    @Test
    fun `a 403 is distinguishable from a 429`() = runTest(dispatcher) {
        withViewModel(FakeNewsRepository { throw NewsUnavailableException(statusCode = 403) }) { viewModel ->
            settle()
            assertEquals("News is temporarily unavailable.", viewModel.error.value)
        }
    }

    @Test
    fun `a failure with no status code still produces a message`() = runTest(dispatcher) {
        withViewModel(FakeNewsRepository { throw NewsUnavailableException() }) { viewModel ->
            settle()
            assertTrue("message should not be blank", !viewModel.error.value.isNullOrBlank())
        }
    }

    @Test
    fun `served-from-cache headlines are flagged stale with their age`() = runTest(dispatcher) {
        val fiveDays = 5L * 24 * 60 * 60 * 1000
        val repo = FakeNewsRepository {
            Headlines(listOf(article(1)), isStale = true, staleAgeMs = fiveDays)
        }
        withViewModel(repo) { viewModel ->
            settle()
            assertEquals(StaleHeadlines(fiveDays), viewModel.staleHeadlines.value)
            assertNull("a stale serve is a success, not an error", viewModel.error.value)
        }
    }

    @Test
    fun `a cache the server could not date is still flagged stale`() = runTest(dispatcher) {
        val repo = FakeNewsRepository {
            Headlines(listOf(article(1)), isStale = true, staleAgeMs = null)
        }
        withViewModel(repo) { viewModel ->
            settle()
            assertEquals(StaleHeadlines(null), viewModel.staleHeadlines.value)
        }
    }

    @Test
    fun `a live refresh clears a previous stale banner`() = runTest(dispatcher) {
        val repo = FakeNewsRepository {
            Headlines(listOf(article(1)), isStale = true, staleAgeMs = 1_000)
        }
        withViewModel(repo) { viewModel ->
            settle()
            assertNotNull(viewModel.staleHeadlines.value)

            repo.result = { Headlines(listOf(article(2))) }
            viewModel.refreshNews()
            settle()

            assertNull(viewModel.staleHeadlines.value)
        }
    }

    private companion object {
        const val SETTLE_MS = 1_000L

        fun article(id: Long) = NewsArticleData(
            id = id,
            headline = "Headline $id",
            intro = "intro",
            imageUrl = null,
            publishedTime = 0L,
            source = "Cricbuzz",
            storyType = "News",
            context = null,
            articleUrl = "https://example.test/$id",
        )
    }
}
