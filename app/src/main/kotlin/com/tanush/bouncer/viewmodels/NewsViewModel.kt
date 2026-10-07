package com.tanush.bouncer.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.data.remote.RealtimeLifecycleController
import com.tanush.bouncer.data.repository.CricbuzzNewsRepository
import com.tanush.bouncer.data.repository.NewsDiskCache
import com.tanush.bouncer.data.repository.NewsStore
import com.tanush.bouncer.data.repository.NewsRepository
import com.tanush.bouncer.data.repository.NewsUnavailableException
import com.tanush.bouncer.models.NewsArticleData
import com.tanush.bouncer.utils.BoundedLruCache
import com.tanush.bouncer.utils.bouncerExceptionHandler
import com.tanush.bouncer.utils.sentryBreadcrumb
import io.sentry.Sentry
import io.sentry.SentryLogLevel
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * On-screen headlines came from the server's cache, not the live source.
 *
 * [ageMs] is null when the server served a cache it could not date. That is still stale and must
 * still be announced — the wording just loses the "from N days ago" part.
 */
data class StaleHeadlines(val ageMs: Long?)

/** Puts the saved headlines on screen, unless a list is already there. */
private suspend fun NewsStore.seed(into: MutableStateFlow<List<NewsArticleData>>) {
    if (into.value.isNotEmpty()) return
    val saved = readHeadlines()
    if (saved.isNotEmpty() && into.value.isEmpty()) into.value = saved
}

/**
 * ViewModel for Cricbuzz News.
 * Fetches headlines from RapidAPI and scrapes article bodies from cricbuzz.com.
 * Auto-refreshes every 4 hours (6x/day).
 */
class NewsViewModel(
    private val repository: NewsRepository = CricbuzzNewsRepository(),
    private val store: NewsStore = NewsDiskCache,
) : ViewModel() {
    companion object {
        private const val TAG = "NewsViewModel"
        private const val REFRESH_INTERVAL_MS = 4 * 60 * 60 * 1000L
        private const val MIN_REFRESH_INTERVAL_MS = 5 * 60 * 1000L
        private const val ARTICLE_BODY_CACHE_SIZE = 20
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

    /** What [retryArticleBody] re-requests: the story id the server keys the body on, and the URL
     *  the fallback scrape needs. Both, because either source alone can be the one that failed. */
    private var lastArticleRef: Pair<Long, String>? = null

    private var pollingJob: Job? = null
    private var lastFetchTime: Long = 0L

    private var loadStarted = false

    // Opened story bodies, so reopening one does not execute `bouncer-news-sync` again.
    private val bodies = BoundedLruCache<Long, List<String>>(ARTICLE_BODY_CACHE_SIZE)

    /**
     * Starts the first fetch and the auto-refresh loop, once. Called by the screens that show
     * headlines (the News tab, Search).
     *
     * Not done in `init`: this ViewModel is hoisted to the app container, so `init` ran on every
     * launch and executed `bouncer-news-sync` for users who never opened News.
     */
    fun ensureLoaded() {
        if (loadStarted) return
        loadStarted = true
        viewModelScope.launch(bouncerExceptionHandler) {
            // The saved list first, so the tab paints at once and has something to show offline;
            // fetchNews then treats what follows as a refresh of it.
            store.seed(_articles)
            fetchNews()
        }
        startAutoRefresh()
    }

    /**
     * Selects the story a restored route names. After process death the back stack returns to the
     * article route with no selection in memory; this finds the story in the saved list. False
     * when it is in neither, and the route has nothing to show.
     */
    suspend fun restoreArticle(storyId: Long): Boolean {
        _selectedArticle.value?.let { return it.id == storyId }
        store.seed(_articles)
        val article = _articles.value.firstOrNull { it.id == storyId } ?: return false
        selectArticle(article)
        return true
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
                    sentryBreadcrumb("news.fetch", "Skipped news fetch — Appwrite not ready", SentryLevel.WARNING)
                    Sentry.metrics().count("news.fetch.skipped.missing_key", 1.0)
                    _error.value = "News is unavailable in this build."
                    return@launch
                }

                val headlines = repository.fetchHeadlines()
                _articles.value = headlines.articles
                store.writeHeadlines(headlines.articles)
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
                sentryBreadcrumb(
                    category = "news.error",
                    message = "Failed to fetch news articles",
                    level = SentryLevel.ERROR,
                    data = buildMap {
                        put("error_type", e.javaClass.simpleName)
                        (e as? NewsUnavailableException)?.statusCode?.let { put("status_code", it) }
                    },
                )
                // Reported unless the call never left the device. `transportFailure` is set at
                // the throw site, not inferred here — the version of this that shipped as a PR
                // compared `e.message` against the literal default message, which is one string
                // edit away from silently reporting everything again.
                //
                // An HTTP error status is skipped too: `executeFunctionDetailed` has already
                // reported "Appwrite function bouncer-news-sync returned HTTP <status>" with the
                // server's error body, and capturing it again here made one failure two issues
                // (ANDROID-3P). What is left is the unreadable or empty 200 — which nothing else
                // sees.
                val unavailable = e as? NewsUnavailableException
                if (unavailable?.transportFailure != true && unavailable?.statusCode == null) Sentry.captureException(e)
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
        sentryBreadcrumb("user.action", "Manual news refresh triggered")
        lastFetchTime = 0L
        fetchNews()
    }

    private fun startAutoRefresh() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(bouncerExceptionHandler) {
            while (true) {
                delay(REFRESH_INTERVAL_MS)
                // A backgrounded app cannot show headlines; wait for it to come back.
                RealtimeLifecycleController.foreground.first { it }
                try {
                    Log.d(TAG, "Auto-refreshing news (every 4 hours)")
                    lastFetchTime = 0L
                    fetchNews()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error during auto-refresh", e)
                    sentryBreadcrumb("news.error", "Auto-refresh failed", SentryLevel.WARNING)
                    Sentry.captureException(e)
                }
            }
        }
    }

    fun selectArticle(article: NewsArticleData) {
        sentryBreadcrumb(
            category = "user.action",
            message = "Article selected: ${article.headline.take(50)}",
            data = mapOf("article_id" to article.id),
        )
        _selectedArticle.value = article
        _articleBody.value = emptyList()
        _articleBodyError.value = null
        fetchArticleBody(article.id, article.articleUrl)
    }

    fun clearSelectedArticle() {
        _selectedArticle.value = null
        _articleBody.value = emptyList()
        _articleBodyError.value = null
    }

    fun retryArticleBody() {
        val (storyId, url) = lastArticleRef ?: return
        _articleBodyError.value = null
        fetchArticleBody(storyId, url)
    }

    private fun fetchArticleBody(storyId: Long, url: String) {
        lastArticleRef = storyId to url
        bodies[storyId]?.let { cached ->
            _articleBody.value = cached
            return
        }
        viewModelScope.launch(bouncerExceptionHandler) {
            _isLoadingArticle.value = true
            try {
                // A published story's text does not change, so a saved copy is the story.
                val saved = store.readBody(storyId)
                val paragraphs = saved.ifEmpty { repository.fetchArticleBody(storyId, url) }
                _articleBody.value = paragraphs
                if (paragraphs.isNotEmpty()) bodies[storyId] = paragraphs
                if (saved.isEmpty()) store.writeBody(storyId, paragraphs)
                Sentry.metrics().distribution("news.article.paragraphs", paragraphs.size.toDouble())
                Log.d(TAG, "Loaded ${paragraphs.size} paragraphs for article $storyId")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load article body for $storyId", e)
                sentryBreadcrumb(
                    category = "news.error",
                    message = "Failed to load article body",
                    level = SentryLevel.ERROR,
                    data = mapOf("story_id" to storyId, "error_type" to e.javaClass.simpleName),
                )
                Sentry.captureException(e)
                Sentry.metrics().count("news.article.body.error", 1.0)
                Sentry.logger().error("Article body load failed: %s", e.message ?: "unknown")
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
