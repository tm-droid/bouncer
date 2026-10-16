package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.network.isUpstreamOutage
import com.tanush.bouncer.network.isReportableScrapeFailure
import com.tanush.bouncer.network.HttpClientProvider
import com.tanush.bouncer.services.backoffDelayMs
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.utils.bouncerExceptionHandler
import com.tanush.bouncer.data.CricBuzzMatchRepository
import com.tanush.bouncer.data.MatchRepository
import com.tanush.bouncer.data.PlayedMatchIndex
import com.tanush.bouncer.data.remote.WinProbabilityStore
import com.tanush.bouncer.services.MatchCacheService
import com.tanush.bouncer.network.CricBuzzUrls
import com.tanush.bouncer.network.MatchListUnavailableException
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLogLevel
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The home screen's "All" status: not a Cricbuzz endpoint, a union of the three that are.
 *
 * It used to be a chip that mapped to "Live" — so tapping it moved the highlight to Live and
 * changed nothing, which reads as a dead control. `AllMatchesScreenV2` dropped the chip rather
 * than fix it; this makes it mean what it says.
 */
internal const val STATUS_ALL = "All"

/** The statuses that have a URL of their own, in the order "All" presents them. */
private val FETCHABLE_STATUSES =
    listOf(MatchCacheService.LIVE, MatchCacheService.UPCOMING, MatchCacheService.RECENT)

/** Which statuses a selection has to fetch and poll. Only "All" needs more than itself. */
internal fun statusesToFetch(status: String): List<String> =
    if (status == STATUS_ALL) FETCHABLE_STATUSES else listOf(status)

/**
 * The cached list a selection should render, or null when nothing is cached for it yet.
 *
 * Null is the caller's "not ready" path everywhere, so "All" returns null until at least one of
 * the three has landed rather than briefly emitting an empty list over a populated one.
 */
internal fun matchesForStatus(
    cache: Map<String, List<CricketMatch>>,
    status: String,
): List<CricketMatch>? {
    if (status != STATUS_ALL) return cache[status]
    val parts = FETCHABLE_STATUSES.mapNotNull(cache::get)
    return if (parts.isEmpty()) null else parts.flatten().distinctBy { it.id }
}

/**
 * What the list says when it could not load. Never the exception's own text: that read
 * "Unable to resolve host www.cricbuzz.com" or an SSL class name to someone wanting a score.
 */
internal fun loadFailureMessage(e: Throwable): String = when {
    HttpClientProvider.isConnectivityException(e) -> "No internet connection. Check your network and try again."
    isUpstreamOutage(e) -> "Cricbuzz isn't responding right now. Pull to refresh to try again."
    else -> "Couldn't load matches. Pull to refresh to try again."
}

sealed class LoadingState {
    object Idle : LoadingState()
    object Refreshing : LoadingState()
    data class Error(val message: String) : LoadingState()
}

class LiveActivityViewModel(
    private val matchRepository: MatchRepository = CricBuzzMatchRepository(),
) : ViewModel() {
    companion object {
        private const val TAG = "LiveActivityViewModel"
        private const val MIN_MANUAL_REFRESH_INTERVAL_MS = 10_000L
        private const val LIVE_POLL_INTERVAL_MS = 6_000L
        private const val SLOW_POLL_INTERVAL_MS = 60_000L
        private const val DEFAULT_POLL_INTERVAL_MS = 30_000L
        private const val MAX_BACKOFF_MULTIPLIER = 4
        private const val FAILURE_REPORT_THRESHOLD = 3
        private const val CACHED_DATA_MESSAGE = "Showing cached data — couldn't reach server"
    }

    private val _selectedStatus = MutableStateFlow(MatchCacheService.LIVE)
    val selectedStatus: StateFlow<String> = _selectedStatus.asStateFlow()

    private val _selectedCompetitionType = MutableStateFlow("All")
    val selectedCompetitionType: StateFlow<String> = _selectedCompetitionType.asStateFlow()

    private val _selectedFormat = MutableStateFlow("All")
    val selectedFormat: StateFlow<String> = _selectedFormat.asStateFlow()

    // Format filter is applied in the UI layer (AllMatchesScreenV2); this ViewModel
    // only holds the selected value for persistence across tab switches.
    fun setFormat(format: String) {
        if (_selectedFormat.value == format) return
        breadcrumb("user.action", "Format changed to: $format")
        _selectedFormat.value = format
    }

    private val _selectedGender = MutableStateFlow("All")
    val selectedGender: StateFlow<String> = _selectedGender.asStateFlow()

    private val _currentMatches = MutableStateFlow<List<CricketMatch>>(emptyList())
    val currentMatches: StateFlow<List<CricketMatch>> = _currentMatches.asStateFlow()

    /**
     * The selected status's list before the competition and gender filters. All matches reads
     * this: it used to read [currentMatches], so a filter chosen on Home narrowed a screen that
     * has no control for it and nothing to say it was applied.
     */
    private val _statusMatches = MutableStateFlow<List<CricketMatch>>(emptyList())
    val statusMatches: StateFlow<List<CricketMatch>> = _statusMatches.asStateFlow()

    private val _allMatchesForHome = MutableStateFlow<List<CricketMatch>>(emptyList())
    val allMatchesForHome: StateFlow<List<CricketMatch>> = _allMatchesForHome.asStateFlow()

    /**
     * Every cached match across Live, Upcoming and Recent, with no filter applied. Search reads
     * this: it used to get [currentMatches], which Home's status, type and gender filters had
     * already narrowed, so a women's match or last week's result was unfindable from the wrong tab.
     */
    private val _allMatches = MutableStateFlow<List<CricketMatch>>(emptyList())
    val allMatches: StateFlow<List<CricketMatch>> = _allMatches.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()

    private var lastManualRefreshMs = 0L

    private val cachedMatches = mutableMapOf<String, List<CricketMatch>>()

    // Statuses fetched this session. [cachedMatches] also holds lists seeded from disk at start,
    // so "has an entry" no longer means "has been fetched".
    private val freshStatuses = mutableSetOf<String>()
    private val pollingTasks = ConcurrentHashMap<String, Job>()
    @Volatile private var isPollingActive = false
    private var hasCompletedInitialFetch = false
    private var initialFetchJob: Job? = null
    private val consecutiveFailures = mutableMapOf<String, Int>()

    init {
        startPolling(forceInitialFetch = true)
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

    // Whether a list of live scores is on screen. Off it — a news article, a series, Settings, an
    // open match — the Live list is still kept current, but once a minute rather than every 6 s:
    // that was ten page downloads and parses a minute for a list nobody could see.
    private var liveListVisible = true

    fun setLiveListVisible(visible: Boolean) {
        if (liveListVisible == visible) return
        liveListVisible = visible
        if (!isPollingActive) return
        // The running loop is asleep on the old interval; restart it on the new one.
        pollingTasks.remove(MatchCacheService.LIVE)?.cancel()
        startPollingForStatus(MatchCacheService.LIVE)
        if (visible) runFastResumeFetch()
    }

    private fun getPollingInterval(status: String): Long = when (status) {
        MatchCacheService.LIVE -> if (liveListVisible) LIVE_POLL_INTERVAL_MS else SLOW_POLL_INTERVAL_MS
        MatchCacheService.UPCOMING, MatchCacheService.RECENT -> SLOW_POLL_INTERVAL_MS
        else -> DEFAULT_POLL_INTERVAL_MS
    }

    fun refreshMatches() {
        val now = System.currentTimeMillis()
        if (_isRefreshing.value || (now - lastManualRefreshMs) < MIN_MANUAL_REFRESH_INTERVAL_MS) return
        lastManualRefreshMs = now
        viewModelScope.launch(bouncerExceptionHandler) {
            breadcrumb("user.action", "Manual refresh triggered")
            _isRefreshing.value = true
            _loadingState.value = LoadingState.Refreshing
            fetchMatchesInternal()
            _isRefreshing.value = false
            // fetchMatchesInternal sets Error on failure — don't clobber it with Idle,
            // or the refresh failure is never surfaced to the UI.
            if (_loadingState.value == LoadingState.Refreshing) {
                _loadingState.value = LoadingState.Idle
            }
        }
    }

    fun setAndFetchMatches(status: String) {
        breadcrumb("user.action", "Status changed to: $status")
        _selectedStatus.value = status
        matchesForStatus(cachedMatches, status)?.let { filterAndEmitMatches(it) }

        val missing = statusesToFetch(status).filterNot(freshStatuses::contains)
        if (missing.isNotEmpty()) {
            viewModelScope.launch(bouncerExceptionHandler) {
                missing.forEach { runCatching { updateMatches(it) } }
            }
        }

        startPolling()
    }

    fun setCompetitionType(type: String) {
        if (_selectedCompetitionType.value == type) return
        breadcrumb("user.action", "Competition type changed to: $type")
        _selectedCompetitionType.value = type
        matchesForStatus(cachedMatches, _selectedStatus.value)?.let { filterAndEmitMatches(it) }
    }

    private var filtersRestored = false

    /**
     * Applies the saved filters, once per ViewModel. Home used to re-apply the onboarding choice
     * every time it entered composition, which undid whatever the menu had been set to since.
     */
    fun restoreFilters(type: String, gender: String) {
        if (filtersRestored) return
        filtersRestored = true
        setCompetitionType(type)
        setGender(gender)
    }

    fun setGender(gender: String) {
        if (_selectedGender.value == gender) return
        breadcrumb("user.action", "Gender filter changed to: $gender")
        _selectedGender.value = gender
        matchesForStatus(cachedMatches, _selectedStatus.value)?.let { filterAndEmitMatches(it) }
    }

    /**
     * Resolve a match by id across ALL cached statuses (Live/Upcoming/Recent), not just the
     * currently-selected tab. Deep links / notification taps carry only a matchId and must not
     * depend on which tab or type/gender filter is active. Falls back to the emitted list.
     */
    fun findMatchById(id: String): CricketMatch? =
        cachedMatches.values.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == id } }
            ?: _currentMatches.value.firstOrNull { it.id == id }

    /**
     * [findMatchById], waiting for what has not loaded yet. An alert tapped on a cold start
     * arrives before the first fetch, and one tapped after its match ended names a match that is
     * on Recent, which Home may never have fetched this session.
     */
    suspend fun resolveMatch(id: String): CricketMatch? {
        findMatchById(id)?.let { return it }
        initialFetchJob?.join()
        findMatchById(id)?.let { return it }
        FETCHABLE_STATUSES.filterNot(freshStatuses::contains).forEach { status ->
            try {
                updateMatches(status)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                breadcrumb("deeplink.resolve", "Could not load $status: ${e.message}")
            }
        }
        return findMatchById(id)
    }

    private fun filterAndEmitMatches(matches: List<CricketMatch>) {
        if (_statusMatches.value != matches) _statusMatches.value = matches
        val type = _selectedCompetitionType.value
        val gender = _selectedGender.value

        val filtered = matches.filter { match ->
            val typeMatch = type == "All" || match.competitionType == type
            val genderMatch = gender == "All" || match.gender == gender
            typeMatch && genderMatch
        }
        if (_currentMatches.value != filtered) {
            _currentMatches.value = filtered
        }
    }

    fun startPolling(forceInitialFetch: Boolean = false) {
        Log.d(TAG, "Starting poll for updates")
        val wasPollingActive = isPollingActive
        isPollingActive = true

        cancelNonLivePollingTasks()
        ensurePollingForCurrentStatuses()

        val shouldRunInitialFetch = forceInitialFetch || !hasCompletedInitialFetch
        when {
            shouldRunInitialFetch && initialFetchJob?.isActive != true -> runInitialFetch()
            !wasPollingActive -> runFastResumeFetch()
        }
    }

    private fun cancelNonLivePollingTasks() {
        // Cancel only non-live polling tasks; live polling persists across status changes.
        pollingTasks.entries.removeAll { (status, job) ->
            if (status != MatchCacheService.LIVE) {
                Log.d(TAG, "Cancelling polling task for $status")
                job.cancel()
                true
            } else false
        }
    }

    private fun ensurePollingForCurrentStatuses() {
        if (!pollingTasks.containsKey(MatchCacheService.LIVE)) {
            startPollingForStatus(MatchCacheService.LIVE)
        }
        statusesToFetch(_selectedStatus.value)
            .filter { it != MatchCacheService.LIVE && !pollingTasks.containsKey(it) }
            .forEach { startPollingForStatus(it) }
    }

    private fun runInitialFetch() {
        initialFetchJob = viewModelScope.launch(bouncerExceptionHandler) {
            seedFromDisk()
            _isLoading.value = _currentMatches.value.isEmpty()
            // Concurrently, and loading ends with the status on screen. In sequence, an evening
            // with no live cricket showed the skeleton for all three scrapes before the empty state.
            coroutineScope {
                FETCHABLE_STATUSES.forEach { status -> launch { fetchInitial(status) } }
            }
            _isLoading.value = false
            hasCompletedInitialFetch = true
        }
    }

    private suspend fun fetchInitial(status: String) {
        try {
            updateMatches(status)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handlePollFailure(status, e)
        }
        if (status == _selectedStatus.value) _isLoading.value = false
    }

    /**
     * Paints the lists this device saved on its last poll, before any request has answered. They
     * were written on every poll and read only after a failure, so a cold start showed a skeleton
     * until Cricbuzz's page had been downloaded and parsed.
     */
    private suspend fun seedFromDisk() {
        FETCHABLE_STATUSES.filterNot(cachedMatches::containsKey).forEach { status ->
            val saved = MatchCacheService.getOfflineMatches(status)
            if (!saved.isNullOrEmpty()) cachedMatches[status] = saved
        }
        if (cachedMatches.isNotEmpty()) publish(_selectedStatus.value)
    }

    private fun runFastResumeFetch() {
        // Fast resume path to avoid waiting for the next polling tick.
        val targets = (listOf(MatchCacheService.LIVE) + statusesToFetch(_selectedStatus.value)).distinct()
        viewModelScope.launch(bouncerExceptionHandler) {
            targets.forEach { runCatching { updateMatches(it) } }
        }
    }

    fun stopPolling() {
        Log.d(TAG, "Stopping all polling tasks")
        isPollingActive = false
        initialFetchJob?.cancel()
        initialFetchJob = null
        pollingTasks.forEach { (status, job) ->
            Log.d(TAG, "Cancelling polling task for $status")
            job.cancel()
        }
        pollingTasks.clear()
    }

    private fun startPollingForStatus(status: String) {
        Log.d(TAG, "Starting polling task for $status")
        val job = viewModelScope.launch(bouncerExceptionHandler) {
            while (isPollingActive) {
                try {
                    awaitNextPollTick(status)
                    if (!isPollingActive) break
                    runPollIteration(status)
                    onPollSuccess(status)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    handlePollFailure(status, e)
                }
            }
        }
        pollingTasks[status] = job
    }

    private suspend fun awaitNextPollTick(status: String) {
        val base = getPollingInterval(status)
        val failures = consecutiveFailures[status] ?: 0
        // On the happy path this is the plain poll interval. After a failure it becomes the shared
        // jittered exponential backoff, which is the third of three retry schemes this codebase
        // had: the one here was *linear*, capped at 5x, and completely unjittered — so every
        // device that failed on the same upstream blip retried in lockstep, which is the load
        // pattern most likely to keep it failing.
        val interval = if (failures == 0) {
            base
        } else {
            backoffDelayMs(
                attempt = failures,
                baseMs = base,
                maxMs = base * (MAX_BACKOFF_MULTIPLIER + 1),
            ).coerceAtLeast(base)
        }
        Log.d(TAG, "Waiting $interval ms before next update for $status (failures: $failures)")
        delay(interval)
    }

    private suspend fun runPollIteration(status: String) {
        if (status == MatchCacheService.LIVE) {
            updateMatches(status)
        } else if (!MatchCacheService.isCacheValid(status)) {
            updateMatches(status)
        }
    }

    private fun onPollSuccess(status: String) {
        consecutiveFailures[status] = 0
        if (_loadingState.value is LoadingState.Error) {
            _loadingState.value = LoadingState.Idle
        }
    }

    private suspend fun handlePollFailure(status: String, e: Exception) {
        val failures = (consecutiveFailures[status] ?: 0) + 1
        consecutiveFailures[status] = failures
        Log.e(TAG, "Error in polling loop for $status (failure #$failures)", e)

        breadcrumb(
            category = "polling.error",
            message = "Polling failed for status: $status (attempt #$failures)",
            level = SentryLevel.WARNING,
            data = mapOf("status" to status, "failure_count" to failures),
        )
        Sentry.metrics().count("live.poll.error", 1.0)
        Sentry.logger().log(SentryLogLevel.WARN, "Polling failure #%s for %s", failures.toString(), status)

        // Suppress until 3 consecutive failures to reduce noise. The scraper has already reported
        // what it could not fetch, and an offline device is not an error.
        if (failures >= FAILURE_REPORT_THRESHOLD && e !is MatchListUnavailableException) Sentry.captureException(e)

        if (_currentMatches.value.isEmpty()) {
            applyOfflineFallback(status, e)
        } else if (status !in freshStatuses && statusIsOnScreen(status)) {
            // The list on screen is the one seeded from disk, and nothing newer has arrived.
            _loadingState.value = LoadingState.Error(CACHED_DATA_MESSAGE)
        }
    }

    private fun statusIsOnScreen(status: String): Boolean =
        _selectedStatus.value.let { it == status || it == STATUS_ALL }

    private suspend fun applyOfflineFallback(status: String, e: Exception) {
        val offline = MatchCacheService.getOfflineMatches(status)
        _loadingState.value = if (!offline.isNullOrEmpty()) {
            Log.d(TAG, "Using offline cache for $status (${offline.size} matches)")
            filterAndEmitMatches(offline)
            LoadingState.Error(CACHED_DATA_MESSAGE)
        } else {
            LoadingState.Error(loadFailureMessage(e))
        }
    }

    private suspend fun fetchListFor(status: String): List<CricketMatch> {
        val fetched = matchRepository.fetchLiveMatches(CricBuzzUrls.urlForStatus(status), status).distinctBy { it.id }
        Sentry.metrics().gauge("live.matches.count", fetched.size.toDouble())
        if (status != MatchCacheService.UPCOMING || fetched.isNotEmpty()) return fetched
        // Cricbuzz's Upcoming page is sometimes empty while fixtures exist. That is their state, not a
        // failure, so it breadcrumbs rather than reports.
        return MatchCacheService.lastKnownUpcoming().also {
            breadcrumb("match.upcoming", "Upcoming empty upstream; serving ${it.size} last-known fixtures")
        }
    }

    private suspend fun updateMatches(status: String) {
        val distinctMatches = fetchListFor(status)
        Sentry.metrics().count("live.poll.success", 1.0)

        // Win probability is computed server-side by bouncer-match-sync and published as one
        // aggregate file; the device only joins it on. A match the server has no number for keeps
        // a null probability and renders no bar.
        //
        // The list is published first, with whatever the store already holds, and again once the
        // file has been refreshed. It used to wait for the download — an Appwrite Storage request
        // with a 10 s read timeout — so a slow one held up scores that were already in hand.
        // Until the file has been read once, a match keeps the figure it was seeded with, so a
        // cold start does not draw the bar, drop it, and draw it again.
        val seeded = if (WinProbabilityStore.hasData) emptyMap() else cachedMatches[status].orEmpty().associateBy { it.id }
        val first = distinctMatches.map { withProbability(it, seeded[it.id]) }
        store(status, first)
        PlayedMatchIndex.record(status, distinctMatches)

        refreshWinProbabilityFor(distinctMatches)
        val joined = distinctMatches.map { withProbability(it, seeded[it.id]) }
        // Only if nothing newer was stored for this status while the file was downloading.
        if (joined != first && cachedMatches[status] === first) store(status, joined)
    }

    private fun withProbability(match: CricketMatch, seeded: CricketMatch?): CricketMatch = when {
        match.isCompleted -> match.copy(winProbability = match.resolvedWinProbability())
        // Upcoming fixtures were `else -> match` — no number, no bar, which is the half of
        // the original report that stayed broken longest. The server now publishes a
        // pre-match prior for the fixtures where both sides are known, and the flag rides
        // with it so the renderer can label it as a prior rather than a reading.
        match.isLive || match.isUpcoming -> {
            val probability = WinProbabilityStore.probabilityFor(match.id)
            if (probability == null && seeded != null && !WinProbabilityStore.hasData) {
                match.copy(
                    winProbability = seeded.winProbability,
                    winProbabilityDraw = seeded.winProbabilityDraw,
                    winProbabilityIsPrematch = seeded.winProbabilityIsPrematch,
                )
            } else {
                match.copy(
                    winProbability = probability?.team1Percent,
                    winProbabilityDraw = probability?.drawPercent,
                    winProbabilityIsPrematch = probability?.isPrematch == true,
                )
            }
        }
        else -> match
    }

    private suspend fun store(status: String, matches: List<CricketMatch>) {
        MatchCacheService.updateCache(matches, status)
        cachedMatches[status] = matches
        freshStatuses += status
        publish(status)
    }

    /** Pushes [cachedMatches] to the flows the screens read, after [status] changed. */
    private fun publish(status: String) {
        val selected = _selectedStatus.value
        if (status == selected || selected == STATUS_ALL) {
            matchesForStatus(cachedMatches, selected)?.let { filterAndEmitMatches(it) }
        }

        updateHomeMatches()
        val all = matchesForStatus(cachedMatches, STATUS_ALL).orEmpty()
        if (_allMatches.value != all) _allMatches.value = all
    }

    /**
     * Fetches whichever of Live, Upcoming and Recent has not been loaded yet, so search can find a
     * match on a status Home never opened. Each fetch is the same cached, single-flight path the
     * tabs use, so an already-warm status costs nothing.
     */
    fun warmAllStatuses() {
        val missing = FETCHABLE_STATUSES.filterNot(freshStatuses::contains)
        if (missing.isEmpty()) return
        viewModelScope.launch(bouncerExceptionHandler) {
            missing.forEach { status ->
                try {
                    updateMatches(status)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Search still has whatever else is loaded; the tabs report their own failures.
                    breadcrumb("search.warm", "Could not warm $status: ${e.message}")
                }
            }
        }
    }

    private fun updateHomeMatches() {
        val combined = (cachedMatches[MatchCacheService.LIVE].orEmpty() + cachedMatches[MatchCacheService.UPCOMING].orEmpty())
            .distinctBy { it.id }
        if (_allMatchesForHome.value != combined) {
            _allMatchesForHome.value = combined
        }
    }

    private suspend fun fetchMatchesInternal() {
        try {
            statusesToFetch(_selectedStatus.value).forEach { updateMatches(it) }
            _loadingState.value = LoadingState.Idle
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.net.UnknownHostException) {
            reportRefreshError(
                e,
                logMsg = "No internet connection during refresh",
                crumb = "No internet connection during refresh",
                userMsg = "No internet connection. Check your network and try again.",
            )
        } catch (e: java.net.SocketTimeoutException) {
            reportRefreshError(
                e,
                logMsg = "Request timed out during refresh",
                crumb = "Socket timeout during refresh",
                userMsg = "Request timed out. Pull to refresh to try again.",
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing matches", e)
            breadcrumb(
                category = "data.error",
                message = "Failed to refresh matches",
                level = SentryLevel.ERROR,
                data = mapOf("error_type" to e.javaClass.simpleName),
            )
            if (isReportableScrapeFailure(e)) Sentry.captureException(e)
            _loadingState.value = LoadingState.Error(loadFailureMessage(e))
        }
    }

    private fun reportRefreshError(e: Exception, logMsg: String, crumb: String, userMsg: String) {
        Log.e(TAG, logMsg, e)
        breadcrumb("network.error", crumb, SentryLevel.ERROR)
        // Offline and a timeout are the network's state; the screen already says so.
        if (isReportableScrapeFailure(e)) Sentry.captureException(e)
        _loadingState.value = LoadingState.Error(userMsg)
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope cancellation already kills the poll jobs; this also clears the
        // isPollingActive flag so a torn-down ViewModel never reports itself as polling.
        // The real lifecycle gate is LifecycleStartEffect in MainAppContainer — without it this
        // loop ran for the whole life of the Activity, backgrounded included.
        stopPolling()
    }
}

/**
 * Only when something in the list can carry a number: a Recent-only list has none to show, and
 * this runs on every list poll.
 */
private suspend fun refreshWinProbabilityFor(matches: List<CricketMatch>) {
    if (matches.any { it.isLive || it.isUpcoming }) WinProbabilityStore.refresh()
}
