package com.tanush.bouncer.viewmodels

import androidx.lifecycle.ViewModelStore
import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.data.repository.Headlines
import com.tanush.bouncer.data.repository.NewsRepository
import com.tanush.bouncer.data.repository.NewsStore
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The News tab keeps its last headlines and the stories that were opened. Before it did, the tab
 * was blank offline and a restored article route had nothing to find its story in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewsSavedHeadlinesTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(AppwriteClient)
        every { AppwriteClient.isReady } returns true
    }

    @After
    fun tearDown() {
        unmockkObject(AppwriteClient)
        Dispatchers.resetMain()
    }

    private class Store(
        var headlines: List<NewsArticleData> = emptyList(),
        val bodies: MutableMap<Long, List<String>> = mutableMapOf(),
    ) : NewsStore {
        override suspend fun readHeadlines() = headlines
        override suspend fun writeHeadlines(articles: List<NewsArticleData>) { headlines = articles }
        override suspend fun readBody(storyId: Long) = bodies[storyId].orEmpty()
        override suspend fun writeBody(storyId: Long, paragraphs: List<String>) { bodies[storyId] = paragraphs }
    }

    private class Repo(
        var headlines: () -> Headlines,
        var body: () -> List<String> = { listOf("from the network") },
    ) : NewsRepository {
        var bodyCalls = 0
        override suspend fun fetchHeadlines(): Headlines = headlines()
        override suspend fun fetchArticleBody(storyId: Long, articleUrl: String): List<String> {
            bodyCalls++
            return body()
        }
    }

    private fun TestScope.settle() {
        advanceTimeBy(SETTLE_MS)
        runCurrent()
    }

    // The auto-refresh loop never drains; see NewsViewModelTest.withViewModel.
    private suspend fun TestScope.withViewModel(
        repo: NewsRepository,
        store: NewsStore,
        body: suspend (NewsViewModel) -> Unit,
    ) {
        val owner = ViewModelStore()
        val viewModel = NewsViewModel(repo, store)
        owner.put("news", viewModel)
        try {
            body(viewModel)
        } finally {
            owner.clear()
            advanceUntilIdle()
        }
    }

    @Test
    fun `saved headlines stay on screen when the refresh fails`() = runTest(dispatcher) {
        val store = Store(headlines = listOf(article(1), article(2)))
        val repo = Repo(headlines = { throw NewsUnavailableException(transportFailure = true) })
        withViewModel(repo, store) { viewModel ->
            viewModel.ensureLoaded()
            settle()
            assertEquals(listOf(1L, 2L), viewModel.articles.value.map { it.id })
            assertFalse("a seeded list is a refresh, not a first load", viewModel.isLoading.value)
        }
    }

    @Test
    fun `a successful fetch replaces the saved list and is saved`() = runTest(dispatcher) {
        val store = Store(headlines = listOf(article(1)))
        val repo = Repo(headlines = { Headlines(listOf(article(7), article(8))) })
        withViewModel(repo, store) { viewModel ->
            viewModel.ensureLoaded()
            settle()
            assertEquals(listOf(7L, 8L), viewModel.articles.value.map { it.id })
            assertEquals(listOf(7L, 8L), store.headlines.map { it.id })
        }
    }

    @Test
    fun `a restored route finds its story in the saved list, and its saved text`() = runTest(dispatcher) {
        val store = Store(headlines = listOf(article(1), article(2)), bodies = mutableMapOf(2L to listOf("saved text")))
        val repo = Repo(headlines = { Headlines(emptyList()) })
        withViewModel(repo, store) { viewModel ->
            // No ensureLoaded: this is the article route restored on its own after process death.
            assertTrue(viewModel.restoreArticle(2))
            settle()
            assertEquals(2L, viewModel.selectedArticle.value?.id)
            assertEquals(listOf("saved text"), viewModel.articleBody.value)
            assertEquals("the saved text is the story; nothing was fetched", 0, repo.bodyCalls)
        }
    }

    @Test
    fun `a story in neither the list nor the store is not restored`() = runTest(dispatcher) {
        withViewModel(Repo(headlines = { Headlines(emptyList()) }), Store()) { viewModel ->
            assertFalse(viewModel.restoreArticle(99))
        }
    }

    @Test
    fun `a story opened from the network is saved for next time`() = runTest(dispatcher) {
        val store = Store(headlines = listOf(article(3)))
        val repo = Repo(headlines = { Headlines(listOf(article(3))) })
        withViewModel(repo, store) { viewModel ->
            viewModel.selectArticle(article(3))
            settle()
            assertEquals(listOf("from the network"), viewModel.articleBody.value)
            assertEquals(listOf("from the network"), store.bodies[3L])
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
