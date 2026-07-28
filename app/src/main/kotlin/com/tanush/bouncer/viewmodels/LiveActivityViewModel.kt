package com.tanush.bouncer.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.managers.MatchSubscriptionManager
import com.tanush.bouncer.managers.NotificationInboxManager
import com.tanush.bouncer.managers.NotificationItem
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.TimelineEventType
import com.tanush.bouncer.ui.components.glass.BallEvent
import com.tanush.bouncer.utils.TimelineEventExtractor
import com.tanush.bouncer.utils.bouncerExceptionHandler
import com.tanush.bouncer.data.CricBuzzMatchRepository
import com.tanush.bouncer.data.MatchRepository
import com.tanush.bouncer.data.remote.WinProbabilityStore
import com.tanush.bouncer.network.CricBuzzUrls
import com.tanush.bouncer.services.LiveNotificationManager
import com.tanush.bouncer.services.MatchCacheService
import com.tanush.bouncer.services.LiveCommentaryManager
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
import kotlinx.coroutines.launch
import java.util.Date

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
) : ViewModel() {
    companion object {
        private const val TAG = "LiveActivityViewModel"
        private const val MIN_MANUAL_REFRESH_INTERVAL_MS = 10_000L
        private const val MAX_PROBABILITY_EVENTS = 300
        private const val MIN_PROB = 5.0
        private const val MAX_PROB = 95.0
        private const val IMPACT_WICKET = -8.0
        private const val IMPACT_BOUNDARY = 4.0
        private const val IMPACT_DOT = -1.0
        private const val LIVE_POLL_INTERVAL_MS = 6_000L
        private const val SLOW_POLL_INTERVAL_MS = 60_000L
        private const val DEFAULT_POLL_INTERVAL_MS = 30_000L
        private const val MAX_BACKOFF_MULTIPLIER = 4
        private const val FAILURE_REPORT_THRESHOLD = 3
        private const val OVER_BALL_COUNT = 5
    }

    private val liveCommentaryManager = LiveCommentaryManager()

    // Set from a context-aware component (Activity / Composable with LocalContext).
    // Must pass applicationContext (or a context whose lifecycle outlives this ViewModel) to avoid leaking an Activity.
    private var notificationManager: LiveNotificationManager? = null

    fun setNotificationManager(manager: LiveNotificationManager) {
        notificationManager = manager
    }

    private val _keyEvent = MutableStateFlow<Pair<String, String>?>(null)
    val keyEvent: StateFlow<Pair<String, String>?> = _keyEvent.asStateFlow()

    private val _selectedStatus = MutableStateFlow("Live")
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

    private val _firstInningsEndBall = MutableStateFlow<Map<String, Int>>(emptyMap())
    val firstInningsEndBall: StateFlow<Map<String, Int>> = _firstInningsEndBall.asStateFlow()

    private val cachedMatches = mutableMapOf<String, List<CricketMatch>>()
    private val pollingTasks = ConcurrentHashMap<String, Job>()
    @Volatile private var isPollingActive = false
    private var hasCompletedInitialFetch = false
    private var initialFetchJob: Job? = null
    private val consecutiveFailures = mutableMapOf<String, Int>()

    // Tracks the last-known recentBalls per match (from fetchBallByBallAndBatsmen, not from the match list scrape)
    private val recentBallsCache = mutableMapOf<String, List<String>>()

    // Match IDs that were live in the previous poll cycle; used to detect match completion for auto-dismiss.
    private val previouslyLiveMatchIds = mutableSetOf<String>()

    private val _completedOvers = MutableStateFlow<Map<String, List<List<String>>>>(emptyMap())
    val completedOvers: StateFlow<Map<String, List<List<String>>>> = _completedOvers.asStateFlow()

    init {
        liveCommentaryManager.onKeyEvent = { title, body -> onLiveKeyEvent(title, body) }
        startPolling(forceInitialFetch = true)
    }

    private fun onLiveKeyEvent(title: String, body: String) {
        viewModelScope.launch(bouncerExceptionHandler) {
            try {
                breadcrumb("live.event", "Key event: $title")
                _keyEvent.value = title to body

                val liveMatch = _currentMatches.value.firstOrNull { it.isLive } ?: return@launch
                if (!MatchSubscriptionManager.isSubscribed(liveMatch.id)) return@launch

                NotificationInboxManager.add(
                    NotificationItem(
                        matchId = liveMatch.id,
                        title = title,
                        body = body,
                        type = NotificationInboxManager.inferType(title),
                    )
                )

                notificationManager?.let { dispatchKeyEventAlert(it, liveMatch, title, body) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Error processing key event: $title", e)
                Sentry.captureException(e)
            }
        }
    }

    private fun dispatchKeyEventAlert(
        manager: LiveNotificationManager,
        match: CricketMatch,
        title: String,
        body: String,
    ) {
        when {
            title.contains("Wicket", ignoreCase = true) -> manager.showWicketAlert(
                match = match,
                batsmanName = body.substringBefore(" ").take(30),
                bowlerName = "",
                dismissalType = "dismissed",
                runsScored = 0
            )
            title.contains("Six", ignoreCase = true) -> manager.showSixAlert(
                match = match,
                batsmanName = "",
                bowlerName = "",
                description = body.take(60)
            )
            title.contains("Boundary", ignoreCase = true) || title.contains("Four", ignoreCase = true) ->
                manager.showFourAlert(
                    match = match,
                    batsmanName = "",
                    bowlerName = "",
                    description = body.take(60)
                )
            title.contains("AI Strategic", ignoreCase = true) -> manager.showMilestoneAlert(
                match = match,
                playerName = "AI Insight",
                milestone = title,
                description = body.take(80)
            )
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
    fun setAIInsightsEnabled(enabled: Boolean) {
        liveCommentaryManager.aiInsightsEnabled = enabled
    }

    fun clearKeyEvent() {
        viewModelScope.launch(bouncerExceptionHandler) {
            _keyEvent.value = null
        }
    }

    private fun getPollingInterval(status: String): Long = when (status) {
        "Live" -> LIVE_POLL_INTERVAL_MS
        "Upcoming", "Recent" -> SLOW_POLL_INTERVAL_MS
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
        cachedMatches[status]?.let { filterAndEmitMatches(it) }

        if (!cachedMatches.containsKey(status)) {
            viewModelScope.launch(bouncerExceptionHandler) {
                runCatching { updateMatches(status) }
            }
        }

        startPolling()
    }

    fun setCompetitionType(type: String) {
        if (_selectedCompetitionType.value == type) return
        breadcrumb("user.action", "Competition type changed to: $type")
        _selectedCompetitionType.value = type
        cachedMatches[_selectedStatus.value]?.let { filterAndEmitMatches(it) }
    }

    fun setGender(gender: String) {
        if (_selectedGender.value == gender) return
        breadcrumb("user.action", "Gender filter changed to: $gender")
        _selectedGender.value = gender
        cachedMatches[_selectedStatus.value]?.let { filterAndEmitMatches(it) }
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
            if (status != "Live") {
                Log.d(TAG, "Cancelling polling task for $status")
                job.cancel()
                true
            } else false
        }
    }

    private fun ensurePollingForCurrentStatuses() {
        if (!pollingTasks.containsKey("Live")) {
            startPollingForStatus("Live")
        }
        val selectedStatus = _selectedStatus.value
        if (selectedStatus != "Live" && !pollingTasks.containsKey(selectedStatus)) {
            startPollingForStatus(selectedStatus)
        }
    }

    private fun runInitialFetch() {
        initialFetchJob = viewModelScope.launch(bouncerExceptionHandler) {
            if (_currentMatches.value.isEmpty()) {
                _isLoading.value = true
            }
            for (status in listOf("Live", "Upcoming", "Recent")) {
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
        val selectedStatus = _selectedStatus.value
        viewModelScope.launch(bouncerExceptionHandler) {
            runCatching { updateMatches("Live") }
            if (selectedStatus != "Live") {
                runCatching { updateMatches(selectedStatus) }
            }
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
        val failures = consecutiveFailures[status] ?: 0
        // Adaptive backoff: up to 5x base interval after repeated failures.
        val interval = getPollingInterval(status) * (1 + failures.coerceAtMost(MAX_BACKOFF_MULTIPLIER))
        Log.d(TAG, "Waiting $interval ms before next update for $status (failures: $failures)")
        delay(interval)
    }

    private suspend fun runPollIteration(status: String) {
        if (status == "Live") {
            updateMatches(status)
            updateAllLiveCommentaries()
            updateLiveNotifications()
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

    private suspend fun updateAllLiveCommentaries() {
        val updatedCommentary = _aiCommentary.value.toMutableMap()

        for (match in _currentMatches.value) {
            try {
                val commentary = liveCommentaryManager.getBestCommentaryForMatch(
                    matchTitle = match.matchTitle,
                    cricbuzzId = match.id,
                    cricbuzzUrl = CricBuzzUrls.liveScoresUrl(match.id),
                    keyNames = match.teams.map { it.name },
                    isMatchComplete = match.isCompleted,
                )

                val latestCommentary = commentary.firstOrNull().orEmpty()
                updatedCommentary[match.id] = latestCommentary
                Log.d(TAG, "[AI Commentary] ${match.id}: ${latestCommentary.take(50)}")

                updateProbabilityEventsFromCommentary(match.id, latestCommentary)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Error updating commentary for match ${match.id}", e)
                breadcrumb(
                    category = "commentary.error",
                    message = "Failed to update commentary for match: ${match.id}",
                    level = SentryLevel.WARNING,
                    data = mapOf("match_id" to match.id),
                )
                Sentry.captureException(e)
            }
        }

        _aiCommentary.value = updatedCommentary
    }

    private suspend fun updateLiveNotifications() {
        val manager = notificationManager ?: return

        for (match in _currentMatches.value) {
            if (!match.isLive) continue
            updateNotificationForMatch(manager, match)
        }
    }

    private suspend fun updateNotificationForMatch(
        manager: LiveNotificationManager,
        match: CricketMatch,
    ) {
        try {
            val details = matchRepository.fetchBallByBallAndBatsmen(match.id)
            val batsmenInfo = details.batsmen.map { bat ->
                LiveNotificationManager.BatsmanInfo(
                    name = bat.name,
                    runs = bat.runs.toIntOrNull() ?: 0,
                    balls = bat.balls.toIntOrNull() ?: 0,
                    isStriker = bat.isStriker,
                )
            }

            val bowlerInfo = details.bowler?.let { b ->
                LiveNotificationManager.BowlerInfo(
                    name = b.name,
                    overs = b.overs,
                    runs = b.runs.toIntOrNull() ?: 0,
                    wickets = b.wickets.toIntOrNull() ?: 0,
                )
            }

            recordOverCompletion(match.id, details.recentBalls.orEmpty())

            val lastEvent = _aiCommentary.value[match.id]?.let {
                detectEventFromCommentary(match.id, it)
            }

            manager.showLiveMatchNotification(
                match = match,
                winProbability = match.winProbabilityFraction,
                lastEvent = lastEvent,
                batsmen = batsmenInfo,
                bowler = bowlerInfo,
                recentBalls = details.recentBalls,
                requiredRate = match.score?.requiredRunRate,
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to update notification for match ${match.id}", e)
            breadcrumb(
                category = "notification.error",
                message = "Failed to update live notification for match: ${match.id}",
                level = SentryLevel.WARNING,
                data = mapOf("match_id" to match.id),
            )
            Sentry.captureException(e)
        }
    }

    private fun recordOverCompletion(matchId: String, newBalls: List<String>) {
        val prevBalls = recentBallsCache[matchId].orEmpty()
        // Detect over completion: had ≥5 balls, now reset to fewer
        if (prevBalls.size >= OVER_BALL_COUNT && newBalls.size < prevBalls.size) {
            val overs = _completedOvers.value.toMutableMap()
            overs[matchId] = overs[matchId].orEmpty() + listOf(prevBalls)
            _completedOvers.value = overs
        }
        recentBallsCache[matchId] = newBalls
    }

    /**
     * Classify the event a notification would announce.
     *
     * Prefers the scraped recent-balls array over the commentary prose, matching what
     * [classifyProbabilityEvent] already does. A "W" in the ball array is the scorer's own record
     * of a dismissal; prose is an interpretation of it, and interpreting prose is what produced
     * false "Wicket!" push notifications off lines like "bowled a beauty, no run".
     */
    private fun detectEventFromCommentary(
        matchId: String?,
        commentary: String,
    ): LiveNotificationManager.MatchEvent? {
        val structural = matchId?.let { recentBallsCache[it]?.lastOrNull() }?.let(BallEvent::from)
        val type = when (structural) {
            BallEvent.WICKET -> LiveNotificationManager.EventType.WICKET
            BallEvent.SIX -> LiveNotificationManager.EventType.SIX
            BallEvent.FOUR -> LiveNotificationManager.EventType.FOUR
            // Any other scored ball is a known non-event; do not fall through to prose and
            // second-guess the scorer.
            null -> return detectEventFromProse(commentary)
            else -> return null
        }
        return LiveNotificationManager.MatchEvent(type, commentary.take(80))
    }

    private fun detectEventFromProse(commentary: String): LiveNotificationManager.MatchEvent? {
        val type = when (TimelineEventExtractor.detectBallType(commentary)) {
            TimelineEventType.WICKET -> LiveNotificationManager.EventType.WICKET
            TimelineEventType.SIX -> LiveNotificationManager.EventType.SIX
            TimelineEventType.FOUR -> LiveNotificationManager.EventType.FOUR
            TimelineEventType.MILESTONE -> LiveNotificationManager.EventType.MILESTONE
            else -> return null
        }
        return LiveNotificationManager.MatchEvent(type, commentary.take(80))
    }

    private fun classifyProbabilityEvent(matchId: String, commentary: String): ProbabilityEventType {
        val lastBall = recentBallsCache[matchId]?.lastOrNull()
        return if (lastBall != null) {
            when (BallEvent.from(lastBall)) {
                BallEvent.WICKET -> ProbabilityEventType.WICKET
                BallEvent.SIX, BallEvent.FOUR -> ProbabilityEventType.BOUNDARY
                BallEvent.DOT -> ProbabilityEventType.DOT_BALL
                else -> ProbabilityEventType.OTHER
            }
        } else {
            when (TimelineEventExtractor.detectBallType(commentary)) {
                TimelineEventType.WICKET -> ProbabilityEventType.WICKET
                TimelineEventType.SIX, TimelineEventType.FOUR -> ProbabilityEventType.BOUNDARY
                else -> ProbabilityEventType.OTHER
            }
        }
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
    private fun updateProbabilityEventsFromCommentary(matchId: String, commentary: String) {
        val match = _currentMatches.value.find { it.id == matchId } ?: return
        val eventType = classifyProbabilityEvent(matchId, commentary)

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

    fun setFirstInningsEndBall(matchId: String, ball: Int) {
        _firstInningsEndBall.value = _firstInningsEndBall.value + (matchId to ball)
    }

    private suspend fun updateMatches(status: String) {
        val url = CricBuzzUrls.urlForStatus(status)
        val distinctMatches = matchRepository.fetchLiveMatches(url, status).distinctBy { it.id }
        Sentry.metrics().gauge("live.matches.count", distinctMatches.size.toDouble())
        Sentry.metrics().count("live.poll.success", 1.0)

        // Win probability is computed server-side by bouncer-match-sync and published as one
        // aggregate file; the device only joins it on. A match the server has no number for keeps
        // a null probability and renders no bar.
        WinProbabilityStore.refresh()
        val matchesWithProbability = distinctMatches.map { match ->
            when {
                match.isCompleted -> match.copy(winProbability = match.resolvedWinProbability())
                match.isLive -> match.copy(winProbability = WinProbabilityStore.forMatch(match.id))
                else -> match
            }
        }

        MatchCacheService.updateCache(matchesWithProbability, status)
        cachedMatches[status] = matchesWithProbability

        if (status == _selectedStatus.value) {
            filterAndEmitMatches(matchesWithProbability)
        }

        updateHomeMatches()
        if (status == "Live") {
            pruneStaleMatchData(matchesWithProbability)
            dismissCompletedMatchNotifications(matchesWithProbability)
        }
    }

    private fun pruneStaleMatchData(currentLiveMatches: List<CricketMatch>) {
        val liveIds = currentLiveMatches.map { it.id }.toSet()
        recentBallsCache.keys.retainAll(liveIds)
        val staleIds = _probabilityEvents.value.keys - liveIds
        if (staleIds.isNotEmpty()) {
            _probabilityEvents.value = _probabilityEvents.value - staleIds
        }
        val staleOverIds = _completedOvers.value.keys - liveIds
        if (staleOverIds.isNotEmpty()) {
            _completedOvers.value = _completedOvers.value - staleOverIds
        }
        val staleInningsIds = _firstInningsEndBall.value.keys - liveIds
        if (staleInningsIds.isNotEmpty()) {
            _firstInningsEndBall.value = _firstInningsEndBall.value - staleInningsIds
        }
    }

    private fun dismissCompletedMatchNotifications(currentLiveMatches: List<CricketMatch>) {
        val currentLiveIds = currentLiveMatches.filter { it.isLive }.map { it.id }.toSet()
        val justFinished = previouslyLiveMatchIds - currentLiveIds
        justFinished.forEach { matchId ->
            notificationManager?.dismissLiveNotification(matchId)
        }
        previouslyLiveMatchIds.clear()
        previouslyLiveMatchIds.addAll(currentLiveIds)
    }

    private fun updateHomeMatches() {
        val combined = (cachedMatches["Live"].orEmpty() + cachedMatches["Upcoming"].orEmpty())
            .distinctBy { it.id }
        if (_allMatchesForHome.value != combined) {
            _allMatchesForHome.value = combined
        }
    }

    private suspend fun fetchMatchesInternal() {
        try {
            updateMatches(_selectedStatus.value)
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
        notificationManager = null
        liveCommentaryManager.close()
    }
}
