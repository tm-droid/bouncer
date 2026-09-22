package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.services.backoffDelayMs
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.TimelineEventType
import com.tanush.bouncer.utils.TimelineEventExtractor
import com.tanush.bouncer.utils.bouncerExceptionHandler
import com.tanush.bouncer.data.CricBuzzMatchRepository
import com.tanush.bouncer.data.MatchRepository
import com.tanush.bouncer.data.PlayedMatchIndex
import com.tanush.bouncer.data.remote.LiveMatchDetailSource
import com.tanush.bouncer.data.remote.ProductionLiveDetailSource
import com.tanush.bouncer.data.remote.RealtimeLifecycleController
import com.tanush.bouncer.data.remote.WinProbabilityStore
import com.tanush.bouncer.services.MatchCacheService
import com.tanush.bouncer.network.CricBuzzUrls
import com.tanush.bouncer.services.MatchPresenceReporter
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLogLevel
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Date

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

sealed class LoadingState {
    object Idle : LoadingState()
    object Refreshing : LoadingState()
    data class Error(val message: String) : LoadingState()
}

enum class ProbabilityEventType {
    WICKET,
    BOUNDARY,
    DOT_BALL,
    OTHER
}

data class ProbabilityEvent(
    val timestamp: Date = Date(),
    val ball: Int,
    val over: Int,
    val probability: Double,
    val eventType: ProbabilityEventType,
    val description: String,
    val impact: Double = 0.0
)

class LiveActivityViewModel(
    private val matchRepository: MatchRepository = CricBuzzMatchRepository(),
    private val commentarySource: LiveMatchDetailSource = ProductionLiveDetailSource,
) : ViewModel() {
    companion object {
        private const val TAG = "LiveActivityViewModel"
        private const val MIN_MANUAL_REFRESH_INTERVAL_MS = 10_000L
        private const val MAX_PROBABILITY_EVENTS = 300
        private const val LIVE_POLL_INTERVAL_MS = 6_000L
        private const val SLOW_POLL_INTERVAL_MS = 60_000L
        private const val DEFAULT_POLL_INTERVAL_MS = 30_000L
        private const val MAX_BACKOFF_MULTIPLIER = 4
        private const val FAILURE_REPORT_THRESHOLD = 3
        private const val PRESENCE_HEARTBEAT_MS = 90_000L
    }

    private val _keyEvent = MutableStateFlow<Pair<String, String>?>(null)
    val keyEvent: StateFlow<Pair<String, String>?> = _keyEvent.asStateFlow()

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

    private val _allMatchesForHome = MutableStateFlow<List<CricketMatch>>(emptyList())
    val allMatchesForHome: StateFlow<List<CricketMatch>> = _allMatchesForHome.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()

    private var lastManualRefreshMs = 0L

    private val _aiCommentary = MutableStateFlow<Map<String, String>>(emptyMap())
    val aiCommentary: StateFlow<Map<String, String>> = _aiCommentary.asStateFlow()

    private val _probabilityEvents = MutableStateFlow<Map<String, List<ProbabilityEvent>>>(emptyMap())
    val probabilityEvents: StateFlow<Map<String, List<ProbabilityEvent>>> = _probabilityEvents.asStateFlow()

    /**
     * Matches a screen is currently displaying commentary-derived content for, refcounted.
     *
     * This is the device-side half of the viewer gate that `bouncer-match-sync` already applies
     * server-side. [updateAllLiveCommentaries] used to loop every live match on every poll, and
     * the Live tab polls every six seconds: with ten live matches that is ~100 commentary
     * generations a minute, each one an Appwrite function execution running an LLM, produced for
     * matches nobody was looking at. Measured on a fresh install it wanted ~150 executions/minute
     * and saturated the EXECUTIONS rate cap within seconds of launch, which starved player search
     * — a user-initiated action losing its budget to background work.
     *
     * A count rather than a set: two screens may hold the same match, and the second one closing
     * must not release it for the first.
     */
    private val commentaryFocus = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val commentaryJobs = mutableMapOf<String, Job>()

    private val cachedMatches = mutableMapOf<String, List<CricketMatch>>()
    private val pollingTasks = ConcurrentHashMap<String, Job>()
    @Volatile private var isPollingActive = false
    private var hasCompletedInitialFetch = false
    private var initialFetchJob: Job? = null
    private val consecutiveFailures = mutableMapOf<String, Int>()

    init {
        startPolling(forceInitialFetch = true)
    }

    private fun onLiveKeyEvent(title: String, body: String) {
        viewModelScope.launch(bouncerExceptionHandler) {
            try {
                breadcrumb("live.event", "Key event: $title")
                _keyEvent.value = title to body
                // The inbox is written by BouncerFirebaseMessagingService when an alert arrives.
                // It used to be written here, from whichever live match was FIRST in the list
                // rather than the one the event came from, and only while the app was open.
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Error processing key event: $title", e)
                Sentry.captureException(e)
            }
        }
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

    // Allow UI to enable/disable AI insight generation at runtime
    fun clearKeyEvent() {
        viewModelScope.launch(bouncerExceptionHandler) {
            _keyEvent.value = null
        }
    }

    private fun getPollingInterval(status: String): Long = when (status) {
        MatchCacheService.LIVE -> LIVE_POLL_INTERVAL_MS
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

        val missing = statusesToFetch(status).filterNot(cachedMatches::containsKey)
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

    private fun filterAndEmitMatches(matches: List<CricketMatch>) {
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
            if (_currentMatches.value.isEmpty()) {
                _isLoading.value = true
            }
            for (status in FETCHABLE_STATUSES) {
                try {
                    updateMatches(status)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    handleInitialFetchError(status, e)
                }
            }
            _isLoading.value = false
            hasCompletedInitialFetch = true
        }
    }

    private fun handleInitialFetchError(status: String, e: Exception) {
        Log.e(TAG, "Initial fetch error for $status", e)
        breadcrumb(
            category = "data.error",
            message = "Initial fetch failed for status: $status",
            level = SentryLevel.ERROR,
            data = mapOf("status" to status),
        )
        Sentry.captureException(e)
        _loadingState.value = LoadingState.Error(e.message ?: "Unknown error")
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

        // Suppress until 3 consecutive failures to reduce noise.
        if (failures >= FAILURE_REPORT_THRESHOLD) Sentry.captureException(e)

        if (_currentMatches.value.isEmpty()) {
            applyOfflineFallback(status, e)
        }
    }

    private suspend fun applyOfflineFallback(status: String, e: Exception) {
        val offline = MatchCacheService.getOfflineMatches(status)
        _loadingState.value = if (!offline.isNullOrEmpty()) {
            Log.d(TAG, "Using offline cache for $status (${offline.size} matches)")
            filterAndEmitMatches(offline)
            LoadingState.Error("Showing cached data — couldn't reach server")
        } else {
            LoadingState.Error(e.localizedMessage ?: "Network error")
        }
    }

    /**
     * Ask for commentary on [matchId] while a screen is showing it; release with [releaseCommentary].
     *
     * Nothing is subscribed for a match with no holder. There is deliberately no "focus everything"
     * call — the whole cost of this path used to be exactly that.
     *
     * The first hold opens a subscription to the `match-commentary` rows `bouncer-match-sync` already
     * writes for every live match, and sends the viewer heartbeat that earns the match its LLM
     * rewrite budget. This replaced a second commentary pipeline on the device: every six-second
     * poll re-scraped the Cricbuzz live page twice and Cricinfo once per held match and called
     * `bouncer-generate-commentary`, to produce a line the server had already stored.
     */
    fun holdCommentary(matchId: String) {
        val count = (commentaryFocus.value[matchId] ?: 0) + 1
        commentaryFocus.value = commentaryFocus.value + (matchId to count)
        if (count == 1) commentaryJobs[matchId] = viewModelScope.launch(bouncerExceptionHandler) {
            launch { presenceHeartbeat(matchId) }
            // Monotonic on sort_key: the channel also delivers UPDATES, and match-sync rewrites the
            // oldest stored lines, so without this a rewrite would re-fire a Wicket! notification
            // for a ball that happened minutes ago and duplicate it in the feed.
            var newest = Double.NEGATIVE_INFINITY
            commentarySource.observeCommentary(matchId).collect { ev ->
                if (ev.sortKey <= newest) return@collect
                newest = ev.sortKey
                onCommentary(matchId, ev.commentary, ev.event)
            }
        }
    }

    /** Release a hold taken by [holdCommentary]. Unbalanced releases are ignored, not negative. */
    fun releaseCommentary(matchId: String) {
        val remaining = (commentaryFocus.value[matchId] ?: 0) - 1
        if (remaining > 0) {
            commentaryFocus.value = commentaryFocus.value + (matchId to remaining)
        } else {
            commentaryFocus.value = commentaryFocus.value - matchId
            commentaryJobs.remove(matchId)?.cancel()
        }
    }

    /** Same cadence and background rule as the match-detail heartbeat. */
    private suspend fun presenceHeartbeat(matchId: String) {
        while (viewModelScope.isActive) {
            RealtimeLifecycleController.foreground.first { it }
            MatchPresenceReporter.ping(matchId)
            delay(PRESENCE_HEARTBEAT_MS)
        }
    }

    private fun onCommentary(matchId: String, commentary: String, event: TimelineEventType?) {
        _aiCommentary.value = _aiCommentary.value + (matchId to commentary)
        // The server classifies from the raw Cricbuzz text before any rewrite; prose is the
        // fallback for rows written before the `event` column existed.
        val type = event ?: TimelineEventExtractor.detectBallType(commentary)
        updateProbabilityEventsFromCommentary(matchId, commentary, type)
        when (type) {
            TimelineEventType.WICKET -> onLiveKeyEvent("Wicket!", commentary)
            TimelineEventType.SIX -> onLiveKeyEvent("Six!", commentary)
            TimelineEventType.FOUR -> onLiveKeyEvent("Boundary", commentary)
            else -> Unit
        }
    }

    /**
     * Classify a key moment. The type is the server's verdict where the row carries one — it is
     * taken from the raw Cricbuzz text before any rewrite, which prose classification of a rewritten
     * line cannot match — and prose otherwise.
     */
    private fun classifyProbabilityEvent(type: TimelineEventType?): ProbabilityEventType =
        when (type) {
            TimelineEventType.WICKET -> ProbabilityEventType.WICKET
            TimelineEventType.SIX, TimelineEventType.FOUR -> ProbabilityEventType.BOUNDARY
            else -> ProbabilityEventType.OTHER
        }

    /**
     * Record a key moment from commentary.
     *
     * This used to be a second win-probability model: it took the on-device heuristic as a base,
     * added a fixed delta per event type (wicket/boundary/dot) and wrote the result back into the
     * match. That made the displayed number the output of two different models layered on each
     * other, and it drifted further from the real one with every ball. The probability now comes
     * from the server alone; this timeline records WHAT happened, not what it is worth.
     */
    private fun updateProbabilityEventsFromCommentary(matchId: String, commentary: String, type: TimelineEventType?) {
        val match = _currentMatches.value.find { it.id == matchId } ?: return
        val eventType = classifyProbabilityEvent(type)

        val currentEvents = _probabilityEvents.value[matchId].orEmpty()
        val probability = match.winProbability ?: return
        val ball = currentEvents.size

        val newEvent = ProbabilityEvent(
            timestamp = Date(),
            ball = ball,
            over = ball / 6,
            probability = probability,
            eventType = eventType,
            description = commentary.take(100),
            impact = probability - (currentEvents.lastOrNull()?.probability ?: probability),
        )

        // Keep only last 300 events (50 overs worth) to bound memory.
        _probabilityEvents.value =
            _probabilityEvents.value + (matchId to (currentEvents + newEvent).takeLast(MAX_PROBABILITY_EVENTS))
    }

    fun getAICommentaryForMatch(matchId: String): String? = _aiCommentary.value[matchId]

    fun getProbabilityEventsForMatch(matchId: String): List<ProbabilityEvent> =
        _probabilityEvents.value[matchId].orEmpty()

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
        WinProbabilityStore.refresh()
        val matchesWithProbability = distinctMatches.map { match ->
            when {
                match.isCompleted -> match.copy(winProbability = match.resolvedWinProbability())
                // Upcoming fixtures were `else -> match` — no number, no bar, which is the half of
                // the original report that stayed broken longest. The server now publishes a
                // pre-match prior for the fixtures where both sides are known, and the flag rides
                // with it so the renderer can label it as a prior rather than a reading.
                match.isLive || match.isUpcoming -> {
                    val probability = WinProbabilityStore.probabilityFor(match.id)
                    match.copy(
                        winProbability = probability?.team1Percent,
                        winProbabilityDraw = probability?.drawPercent,
                        winProbabilityIsPrematch = probability?.isPrematch == true,
                    )
                }
                else -> match
            }
        }

        MatchCacheService.updateCache(matchesWithProbability, status)
        PlayedMatchIndex.record(status, distinctMatches)
        cachedMatches[status] = matchesWithProbability

        val selected = _selectedStatus.value
        if (status == selected || selected == STATUS_ALL) {
            matchesForStatus(cachedMatches, selected)?.let { filterAndEmitMatches(it) }
        }

        updateHomeMatches()
        if (status == MatchCacheService.LIVE) {
            pruneStaleMatchData(matchesWithProbability)
        }
    }

    private fun pruneStaleMatchData(currentLiveMatches: List<CricketMatch>) {
        val liveIds = currentLiveMatches.map { it.id }.toSet()
        val staleIds = _probabilityEvents.value.keys - liveIds
        if (staleIds.isNotEmpty()) {
            _probabilityEvents.value = _probabilityEvents.value - staleIds
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
            Sentry.captureException(e)
            _loadingState.value = LoadingState.Error(
                e.localizedMessage ?: "Failed to load matches. Pull to refresh to try again."
            )
        }
    }

    private fun reportRefreshError(e: Exception, logMsg: String, crumb: String, userMsg: String) {
        Log.e(TAG, logMsg, e)
        breadcrumb("network.error", crumb, SentryLevel.ERROR)
        Sentry.captureException(e)
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
