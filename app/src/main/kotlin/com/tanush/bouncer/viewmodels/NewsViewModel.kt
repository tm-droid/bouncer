package com.tanush.bouncer.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.data.repository.CricbuzzNewsRepository
import com.tanush.bouncer.data.repository.NewsRepository
import com.tanush.bouncer.data.repository.NewsUnavailableException
import com.tanush.bouncer.models.NewsArticleData
import com.tanush.bouncer.utils.bouncerExceptionHandler
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLogLevel
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * On-screen headlines came from the server's cache, not the live source.
 *
 * [ageMs] is null when the server served a cache it could not date. That is still stale and must
 * still be announced — the wording just loses the "from N days ago" part.
 */
data class StaleHeadlines(val ageMs: Long?)

/**
 * ViewModel for Cricbuzz News.
 * Fetches headlines from RapidAPI and scrapes article bodies from cricbuzz.com.
 * Auto-refreshes every 4 hours (6x/day).
 */
class NewsViewModel(
    private val repository: NewsRepository = CricbuzzNewsRepository(),
) : ViewModel() {
    companion object {
        private const val TAG = "NewsViewModel"
        private const val REFRESH_INTERVAL_MS = 4 * 60 * 60 * 1000L
        private const val MIN_REFRESH_INTERVAL_MS = 5 * 60 * 1000L
        private fun newsFetchMessageFor(statusCode: Int): String = when (statusCode) {
            401, 403 -> "News is temporarily unavailable."
            429 -> "News is temporarily rate limited. Please try again later."
            in 500..599 -> "News service is unavailable. Please try again later."
            else -> "Failed to load news."
        }

        /**
         * The message the screen shows. It is built here, where the status code exists, rather
         * than reconstructed downstream: NewsScreenV2 used to recover the code by substring-
         * matching this very string, so a 429 rendered as "Something went wrong".
         */
        private fun messageFor(e: Throwable): String = when {
            e is NewsUnavailableException && e.statusCode != null -> newsFetchMessageFor(e.statusCode)
            e is NewsUnavailableException -> e.message ?: "Failed to load news"
            else -> e.localizedMessage ?: "Failed to load news"
        }
    }

    private val _articles = MutableStateFlow<List<NewsArticleData>>(emptyList())
    val articles: StateFlow<List<NewsArticleData>> = _articles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Non-null when the headlines on screen came from the server's cache because the live source
     * was unreachable. The value is the cache's age in ms, or null inside [StaleHeadlines] when
     * the server could not date it — stale-with-unknown-age is still stale, and the UI must say so
     * rather than present week-old headlines as current.
     */
    private val _staleHeadlines = MutableStateFlow<StaleHeadlines?>(null)
    val staleHeadlines: StateFlow<StaleHeadlines?> = _staleHeadlines.asStateFlow()

    private val _selectedArticle = MutableStateFlow<NewsArticleData?>(null)
    val selectedArticle: StateFlow<NewsArticleData?> = _selectedArticle.asStateFlow()

    private val _articleBody = MutableStateFlow<List<String>>(emptyList())
    val articleBody: StateFlow<List<String>> = _articleBody.asStateFlow()

    private val _isLoadingArticle = MutableStateFlow(false)
    val isLoadingArticle: StateFlow<Boolean> = _isLoadingArticle.asStateFlow()

    /** Non-null when the article body failed to load (rendered as an error state, not body text). */
    private val _articleBodyError = MutableStateFlow<String?>(null)
    val articleBodyError: StateFlow<String?> = _articleBodyError.asStateFlow()

    private var lastArticleUrl: String? = null

    private var pollingJob: Job? = null
    private var lastFetchTime: Long = 0L

    init {
        fetchNews()
        startAutoRefresh()
    }

    private fun breadcrumb(
        category: String,
        message: String,
        level: SentryLevel = SentryLevel.INFO,
        data: Map<String, Any?> = emptyMap(),
    ) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.category = category
            this.message = message
            this.level = level
            data.forEach { (k, v) -> setData(k, v) }
        })
    }

    fun fetchNews() {
        val now = System.currentTimeMillis()
        if (_isLoading.value || _isRefreshing.value) return
        if (lastFetchTime > 0 && (now - lastFetchTime) < MIN_REFRESH_INTERVAL_MS && _articles.value.isNotEmpty()) {
            Log.d(TAG, "Skipping fetch — last fetch was ${(now - lastFetchTime) / 1000}s ago")
            return
        }

        viewModelScope.launch(bouncerExceptionHandler) {
            try {
                if (_articles.value.isEmpty()) _isLoading.value = true else _isRefreshing.value = true
                _error.value = null

                if (!AppwriteClient.isReady) {
                    Log.w(TAG, "Skipping news fetch — Appwrite not initialized")
                    breadcrumb("news.fetch", "Skipped news fetch — Appwrite not ready", SentryLevel.WARNING)
                    Sentry.metrics().count("news.fetch.skipped.missing_key", 1.0)
                    _error.value = "News is unavailable in this build."
                    return@launch
                }

                val headlines = repository.fetchHeadlines()
                _articles.value = headlines.articles
                _staleHeadlines.value = if (headlines.isStale) StaleHeadlines(headlines.staleAgeMs) else null
                lastFetchTime = System.currentTimeMillis()
                Sentry.metrics().gauge("news.articles.count", headlines.articles.size.toDouble())
                Sentry.metrics().count("news.fetch.success", 1.0)
                if (headlines.isStale) Sentry.metrics().count("news.fetch.stale", 1.0)
                Sentry.logger().log(
                    SentryLogLevel.INFO,
                    "News fetch success: %s articles",
                    headlines.articles.size.toString(),
                )
                Log.d(TAG, "Fetched ${headlines.articles.size} news articles (stale=${headlines.isStale})")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch news", e)
                breadcrumb(
                    category = "news.error",
                    message = "Failed to fetch news articles",
                    level = SentryLevel.ERROR,
                    data = mapOf(
                        "error_type" to e.javaClass.simpleName,
                        "status_code" to (e as? NewsUnavailableException)?.statusCode,
                    ),
                )
                Sentry.captureException(e)
                Sentry.metrics().count("news.fetch.error", 1.0)
                _error.value = messageFor(e)
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    /**
     * The one refresh entry point, for both pull-to-refresh and the error-state Retry button.
     *
     * There used to be a second one, `refresh()`, which set `_isRefreshing = true` before calling
     * [fetchNews] — whose first line returns early when `_isRefreshing` is already true. So it
     * never fetched anything, and because it also cleared `_error` first, tapping Retry dropped
     * the screen into the "No news yet" empty state, which had no Retry button. The tab was dead
     * until the app restarted. [fetchNews] owns the `_isRefreshing` lifecycle; nothing else may
     * set it.
     */
    fun refreshNews() {
        breadcrumb("user.action", "Manual news refresh triggered")
        lastFetchTime = 0L
        fetchNews()
    }

    private fun startAutoRefresh() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(bouncerExceptionHandler) {
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
                    breadcrumb("news.error", "Auto-refresh failed", SentryLevel.WARNING)
                    Sentry.captureException(e)
                }
            }
        }
    }

    fun selectArticle(article: NewsArticleData) {
        breadcrumb(
            category = "user.action",
            message = "Article selected: ${article.headline.take(50)}",
            data = mapOf("article_id" to article.id),
        )
        _selectedArticle.value = article
        _articleBody.value = emptyList()
        _articleBodyError.value = null
        fetchArticleBody(article.articleUrl)
    }

    fun clearSelectedArticle() {
        _selectedArticle.value = null
        _articleBody.value = emptyList()
        _articleBodyError.value = null
    }

    fun retryArticleBody() {
        val url = lastArticleUrl ?: return
        _articleBodyError.value = null
        fetchArticleBody(url)
    }

    private fun fetchArticleBody(url: String) {
        lastArticleUrl = url
        viewModelScope.launch(bouncerExceptionHandler) {
            _isLoadingArticle.value = true
            try {
                val paragraphs = repository.fetchArticleBody(url)
                _articleBody.value = paragraphs
                Sentry.metrics().distribution("news.article.paragraphs", paragraphs.size.toDouble())
                Log.d(TAG, "Scraped ${paragraphs.size} paragraphs for article")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to scrape article body from $url", e)
                breadcrumb(
                    category = "scraping.error",
                    message = "Failed to scrape article body",
                    level = SentryLevel.ERROR,
                    data = mapOf("url" to url, "error_type" to e.javaClass.simpleName),
                )
                Sentry.captureException(e)
                Sentry.metrics().count("news.article.scrape.error", 1.0)
                Sentry.logger().error("Article scrape failed: %s", e.message ?: "unknown")
                _articleBodyError.value = "Rain delay — couldn't load this article. Check your connection and retry."
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
