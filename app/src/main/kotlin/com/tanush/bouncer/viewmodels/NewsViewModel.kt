package com.tanush.bouncer.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.models.NewsArticleData
import com.tanush.bouncer.network.RetrofitClient
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ViewModel for Cricbuzz News, fetching headlines from the RapidAPI endpoint
 * and scraping article body content from cricbuzz.com HTML.
 *
 * News is refreshed 6 times per day (every 4 hours).
 */
class NewsViewModel : ViewModel() {
    companion object {
        private const val TAG = "NewsViewModel"
        // Refresh every 4 hours = 6 times per day
        private const val REFRESH_INTERVAL_MS = 4 * 60 * 60 * 1000L
        // Minimum interval between user-triggered refreshes
        private const val MIN_REFRESH_INTERVAL_MS = 5 * 60 * 1000L
        private const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private val _articles = MutableStateFlow<List<NewsArticleData>>(emptyList())
    val articles: StateFlow<List<NewsArticleData>> = _articles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Selected article for detail view
    private val _selectedArticle = MutableStateFlow<NewsArticleData?>(null)
    val selectedArticle: StateFlow<NewsArticleData?> = _selectedArticle.asStateFlow()

    // Article body paragraphs (scraped from cricbuzz.com)
    private val _articleBody = MutableStateFlow<List<String>>(emptyList())
    val articleBody: StateFlow<List<String>> = _articleBody.asStateFlow()

    private val _isLoadingArticle = MutableStateFlow(false)
    val isLoadingArticle: StateFlow<Boolean> = _isLoadingArticle.asStateFlow()

    private var pollingJob: Job? = null
    private var lastFetchTime: Long = 0L

    init {
        fetchNews()
        startAutoRefresh()
    }

    /**
     * Fetch news from Cricbuzz RapidAPI endpoint.
     */
    fun fetchNews() {
        val now = System.currentTimeMillis()
        if (_isLoading.value || _isRefreshing.value) return
        if (lastFetchTime > 0 && (now - lastFetchTime) < MIN_REFRESH_INTERVAL_MS && _articles.value.isNotEmpty()) {
            Log.d(TAG, "Skipping fetch — last fetch was ${(now - lastFetchTime) / 1000}s ago")
            return
        }

        viewModelScope.launch {
            try {
                if (_articles.value.isEmpty()) {
                    _isLoading.value = true
                } else {
                    _isRefreshing.value = true
                }
                _error.value = null

                val response = RetrofitClient.cricbuzzNewsApi.getNewsIndex()
                val stories = response.storyList
                    .mapNotNull { it.story }
                    .mapIndexed { index, story ->
                        NewsArticleData.fromCricbuzzStory(story, isFirst = index == 0)
                    }

                _articles.value = stories
                lastFetchTime = System.currentTimeMillis()
                Log.d(TAG, "Fetched ${stories.size} news articles")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch news", e)
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "news.error"
                    message = "Failed to fetch news articles"
                    level = SentryLevel.ERROR
                    setData("error_type", e.javaClass.simpleName)
                })
                Sentry.captureException(e)
                _error.value = e.localizedMessage ?: "Failed to load news"
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Force refresh news (e.g., from pull-to-refresh).
     */
    fun refreshNews() {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "user.action"
            message = "Manual news refresh triggered"
            level = SentryLevel.INFO
        })
        lastFetchTime = 0L // Reset cooldown
        fetchNews()
    }

    /**
     * Auto-refresh news every 4 hours (6 times per day).
     */
    private fun startAutoRefresh() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(REFRESH_INTERVAL_MS)
                try {
                    Log.d(TAG, "Auto-refreshing news (every 4 hours)")
                    lastFetchTime = 0L
                    fetchNews()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error during auto-refresh", e)
                    Sentry.addBreadcrumb(Breadcrumb().apply {
                        category = "news.error"
                        message = "Auto-refresh failed"
                        level = SentryLevel.WARNING
                    })
                    Sentry.captureException(e)
                }
            }
        }
    }

    /**
     * Select an article to view in the detail screen.
     * Begins scraping the article body from cricbuzz.com.
     */
    fun selectArticle(article: NewsArticleData) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "user.action"
            message = "Article selected: ${article.headline.take(50)}"
            level = SentryLevel.INFO
            setData("article_id", article.id)
        })
        _selectedArticle.value = article
        _articleBody.value = emptyList()
        fetchArticleBody(article.articleUrl)
    }

    /**
     * Clear the selected article (go back from detail view).
     */
    fun clearSelectedArticle() {
        _selectedArticle.value = null
        _articleBody.value = emptyList()
    }

    /**
     * Scrape article body paragraphs from cricbuzz.com HTML.
     * Targets the div with class "flex flex-col gap-6 text-base" for article content.
     */
    private fun fetchArticleBody(url: String) {
        viewModelScope.launch {
            _isLoadingArticle.value = true
            try {
                val paragraphs = withContext(Dispatchers.IO) {
                    val doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(15000)
                        .get()

                    // Find the article body container
                    val bodyContainer = doc.selectFirst("div.flex.flex-col.gap-6.text-base")
                        ?: doc.selectFirst("div[class*=flex][class*=flex-col][class*=gap-6][class*=text-base]")

                    if (bodyContainer != null) {
                        // Get all children paragraphs
                        bodyContainer.children()
                            .map { it.text().trim() }
                            .filter { it.isNotBlank() }
                    } else {
                        // Fallback: try getting <p> tags within article content
                        val articleContent = doc.select("div.cb-nws-dtl-itms p, div.cb-nws-intr p, article p")
                        if (articleContent.isNotEmpty()) {
                            articleContent.map { it.text().trim() }.filter { it.isNotBlank() }
                        } else {
                            listOf("Unable to load article content. Please try again.")
                        }
                    }
                }
                _articleBody.value = paragraphs
                Log.d(TAG, "Scraped ${paragraphs.size} paragraphs for article")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to scrape article body from $url", e)
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "scraping.error"
                    message = "Failed to scrape article body"
                    level = SentryLevel.ERROR
                    setData("url", url)
                    setData("error_type", e.javaClass.simpleName)
                })
                Sentry.captureException(e)
                _articleBody.value = listOf("Failed to load article. Please check your connection and try again.")
            } finally {
                _isLoadingArticle.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
