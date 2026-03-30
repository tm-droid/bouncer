package com.tanush.bouncer.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.network.CricBuzzScraper
import com.tanush.bouncer.network.CricBuzzUrls
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLogLevel
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Error state for the match list UI
 */
sealed class MatchLoadState {
    object Idle : MatchLoadState()
    object Loading : MatchLoadState()
    data class Error(val message: String, val isRetryable: Boolean = true) : MatchLoadState()
    object Success : MatchLoadState()
}

class MainViewModel : ViewModel() {
    companion object {
        private const val TAG = "MainViewModel"
    }

    private val scraper = CricBuzzScraper()

    private val _currentMatches = MutableStateFlow<List<CricketMatch>>(emptyList())
    val currentMatches: StateFlow<List<CricketMatch>> = _currentMatches.asStateFlow()

    private val _selectedStatus = MutableStateFlow("Live")
    val selectedStatus: StateFlow<String> = _selectedStatus.asStateFlow()

    private val _selectedCategories = MutableStateFlow<Set<String>>(setOf("International", "League", "Domestic", "Women"))
    val selectedCategories: StateFlow<Set<String>> = _selectedCategories.asStateFlow()

    private val _loadState = MutableStateFlow<MatchLoadState>(MatchLoadState.Idle)
    val loadState: StateFlow<MatchLoadState> = _loadState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        fetchMatches("Live")
    }

    fun setStatus(status: String) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "user.action"
            message = "Status changed to: $status"
            level = SentryLevel.INFO
        })
        _selectedStatus.value = status
        fetchMatches(status)
    }

    fun toggleCategory(category: String) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.category = "user.action"
            message = "Category toggled: $category"
            level = SentryLevel.INFO
        })
        val current = _selectedCategories.value.toMutableSet()
        if (current.contains(category)) {
            current.remove(category)
        } else {
            current.add(category)
        }
        _selectedCategories.value = current
    }

    /**
     * Retry the last failed fetch
     */
    fun retry() {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "user.action"
            message = "Retrying fetch for status: ${_selectedStatus.value}"
            level = SentryLevel.INFO
        })
        fetchMatches(_selectedStatus.value)
    }

    private fun fetchMatches(status: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _loadState.value = MatchLoadState.Loading

            val url = CricBuzzUrls.urlForStatus(status)

            try {
                val matches = scraper.fetchLiveMatches(url, status)
                _currentMatches.value = matches
                _loadState.value = if (matches.isEmpty()) {
                    MatchLoadState.Success // Empty but no error
                } else {
                    MatchLoadState.Success
                }
                Sentry.metrics().gauge("matches.loaded", matches.size.toDouble())
                Sentry.metrics().count("matches.fetch.success", 1.0)
                Sentry.logger().log(SentryLogLevel.INFO, "MainViewModel fetched %s matches for %s", matches.size.toString(), status)
                Log.d(TAG, "Fetched ${matches.size} matches for $status")
            } catch (e: CancellationException) {
                throw e
            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "No internet connection", e)
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "network.error"
                    message = "No internet connection while fetching $status matches"
                    level = SentryLevel.ERROR
                })
                Sentry.captureException(e)
                Sentry.metrics().count("matches.fetch.error.unknown_host", 1.0)
                _loadState.value = MatchLoadState.Error(
                    message = "No internet connection. Check your network and try again.",
                    isRetryable = true
                )
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Request timed out", e)
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "network.error"
                    message = "Socket timeout while fetching $status matches"
                    level = SentryLevel.ERROR
                })
                Sentry.captureException(e)
                Sentry.metrics().count("matches.fetch.error.timeout", 1.0)
                _loadState.value = MatchLoadState.Error(
                    message = "Request timed out. Try again.",
                    isRetryable = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching matches for $status", e)
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "data.error"
                    message = "Failed to fetch matches for status: $status"
                    level = SentryLevel.ERROR
                    setData("status", status)
                    setData("error_type", e.javaClass.simpleName)
                })
                Sentry.captureException(e)
                Sentry.metrics().count("matches.fetch.error.general", 1.0)
                Sentry.logger().error("MainViewModel fetch failed for %s: %s", status, e.message ?: "unknown")
                _loadState.value = MatchLoadState.Error(
                    message = "Failed to load matches: ${e.localizedMessage ?: "Unknown error"}",
                    isRetryable = true
                )
            } finally {
                _isLoading.value = false
            }
        }
    }
}
