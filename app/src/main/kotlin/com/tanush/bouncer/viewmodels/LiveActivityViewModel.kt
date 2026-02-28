package com.tanush.bouncer.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.analysis.WinProbabilityCalculator
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.network.CricBuzzScraper
import com.tanush.bouncer.services.MatchCacheService
import com.tanush.bouncer.services.LiveCommentaryManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Loading state for the match list
 * Mirrors the iOS LoadingState enum
 */
sealed class LoadingState {
    object Idle : LoadingState()
    object Refreshing : LoadingState()
    data class Error(val message: String) : LoadingState()
}

/**
 * Probability event types for win probability graph
 * Mirrors iOS ProbabilityEvent.EventType
 */
enum class ProbabilityEventType {
    WICKET,
    BOUNDARY,
    DOT_BALL,
    OTHER
}

/**
 * Probability event for win probability tracking
 * Mirrors iOS ProbabilityEvent structure
 */
data class ProbabilityEvent(
    val timestamp: Date = Date(),
    val ball: Int,
    val over: Int,
    val probability: Double,
    val eventType: ProbabilityEventType,
    val description: String,
    val impact: Double = 0.0
)

class LiveActivityViewModel : ViewModel() {
    companion object {
        private const val TAG = "LiveActivityViewModel"
    }
    
    private val scraper = CricBuzzScraper()
    private val liveCommentaryManager = LiveCommentaryManager()
    private val winCalculator = WinProbabilityCalculator()
    private val _keyEvent = MutableStateFlow<Pair<String, String>?>(null)
    val keyEvent: StateFlow<Pair<String, String>?> = _keyEvent.asStateFlow()
    
    private val _selectedStatus = MutableStateFlow("Live")
    val selectedStatus: StateFlow<String> = _selectedStatus.asStateFlow()

    private val _selectedCompetitionType = MutableStateFlow("All")
    val selectedCompetitionType: StateFlow<String> = _selectedCompetitionType.asStateFlow()

    private val _selectedGender = MutableStateFlow("All")
    val selectedGender: StateFlow<String> = _selectedGender.asStateFlow()

    private val _currentMatches = MutableStateFlow<List<CricketMatch>>(emptyList())
    val currentMatches: StateFlow<List<CricketMatch>> = _currentMatches.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()
    
    // iOS-inspired: AI commentary per match ID for UI display
    private val _aiCommentary = MutableStateFlow<Map<String, String>>(emptyMap())
    val aiCommentary: StateFlow<Map<String, String>> = _aiCommentary.asStateFlow()
    
    // iOS-inspired: Probability events for win probability graph per match
    private val _probabilityEvents = MutableStateFlow<Map<String, List<ProbabilityEvent>>>(emptyMap())
    val probabilityEvents: StateFlow<Map<String, List<ProbabilityEvent>>> = _probabilityEvents.asStateFlow()
    
    // Track first innings end ball for each match (for graph rendering)
    private val _firstInningsEndBall = MutableStateFlow<Map<String, Int>>(emptyMap())
    val firstInningsEndBall: StateFlow<Map<String, Int>> = _firstInningsEndBall.asStateFlow()

    private val cachedMatches = mutableMapOf<String, List<CricketMatch>>()
    private val pollingTasks = mutableMapOf<String, Job>()
    private var isPollingActive = false

    init {
        // Wire commentary manager events into ViewModel flows
        liveCommentaryManager.onKeyEvent = { title, body ->
            viewModelScope.launch {
                _keyEvent.value = title to body
            }
        }
        startPolling()
    }

    // Allow UI to enable/disable AI insight generation at runtime
    fun setAIInsightsEnabled(enabled: Boolean) {
        liveCommentaryManager.aiInsightsEnabled = enabled
    }

    fun clearKeyEvent() {
        viewModelScope.launch {
            _keyEvent.value = null
        }
    }
    
    /**
     * Get polling interval for a specific status type
     * Live matches need more frequent updates
     */
    private fun getPollingInterval(status: String): Long {
        return when (status) {
            "Live" -> 6_000L // Poll every 6 seconds for live matches
            "Upcoming", "Recent" -> 60_000L // Poll every 60 seconds for upcoming and recent matches
            else -> 30_000L // Default fallback
        }
    }

    fun refreshMatches() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _loadingState.value = LoadingState.Refreshing
            fetchMatchesInternal()
            _isRefreshing.value = false
            _loadingState.value = LoadingState.Idle
        }
    }

    fun setAndFetchMatches(status: String) {
        _selectedStatus.value = status
        // Immediately show cached if available
        cachedMatches[status]?.let {
            filterAndEmitMatches(it)
        }

        // Restart polling with new status focus
        startPolling()
    }

    fun setCompetitionType(type: String) {
        _selectedCompetitionType.value = type
        cachedMatches[_selectedStatus.value]?.let { filterAndEmitMatches(it) }
    }

    fun setGender(gender: String) {
        _selectedGender.value = gender
        cachedMatches[_selectedStatus.value]?.let { filterAndEmitMatches(it) }
    }

    private fun filterAndEmitMatches(matches: List<CricketMatch>) {
        val type = _selectedCompetitionType.value
        val gender = _selectedGender.value
        
        val filtered = matches.filter { match ->
            val typeMatch = type == "All" || match.competitionType == type
            val genderMatch = gender == "All" || match.gender == gender
            typeMatch && genderMatch
        }
        _currentMatches.value = filtered
    }

    fun startPolling() {
        Log.d(TAG, "Starting poll for updates")
        isPollingActive = true
        
        // Cancel only non-live polling tasks safely
        val iterator = pollingTasks.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key != "Live") {
                Log.d(TAG, "Cancelling polling task for ${entry.key}")
                entry.value.cancel()
                iterator.remove()
            }
        }
        
        // Start polling for live matches if not already running
        if (!pollingTasks.containsKey("Live")) {
            startPollingForStatus("Live")
        }
        
        // Start polling for the selected status if not live
        val selectedStatus = _selectedStatus.value
        if (selectedStatus != "Live" && !pollingTasks.containsKey(selectedStatus)) {
            startPollingForStatus(selectedStatus)
        }
        
        // Initial data fetch for all statuses
        viewModelScope.launch {
            if (_currentMatches.value.isEmpty()) {
                _isLoading.value = true
            }
            for (status in listOf("Live", "Upcoming", "Recent")) {
                try {
                    updateMatches(status)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Initial fetch error for $status", e)
                    _loadingState.value = LoadingState.Error(e.message ?: "Unknown error")
                }
            }
            _isLoading.value = false
        }
    }
    
    fun stopPolling() {
        Log.d(TAG, "Stopping all polling tasks")
        isPollingActive = false
        pollingTasks.forEach { (status, job) ->
            Log.d(TAG, "Cancelling polling task for $status")
            job.cancel()
        }
        pollingTasks.clear()
    }
    
    private fun startPollingForStatus(status: String) {
        Log.d(TAG, "Starting polling task for $status")
        val job = viewModelScope.launch {
            while (isPollingActive) {
                try {
                    val interval = getPollingInterval(status)
                    Log.d(TAG, "Waiting $interval ms before next update for $status")
                    delay(interval)
                    
                    if (!isPollingActive) {
                        Log.d(TAG, "Polling task cancelled for $status")
                        break
                    }
                    
                    // Always fetch new data for live matches, check cache for others
                    if (status == "Live") {
                        Log.d(TAG, "Fetching new data for Live matches")
                        updateMatches(status)
                        updateAllLiveCommentaries()
                    } else if (!MatchCacheService.isCacheValid(status)) {
                        Log.d(TAG, "Cache expired, fetching new data for $status")
                        updateMatches(status)
                    } else {
                        Log.d(TAG, "Cache still valid for $status")
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        Log.d(TAG, "Polling task for $status was cancelled")
                        throw e
                    }
                    Log.e(TAG, "Error in polling loop for $status", e)
                }
            }
        }
        pollingTasks[status] = job
    }
    
    /**
     * Fetch and update commentary for all live matches
     * Stores AI commentary in the aiCommentary map for UI display
     */
    private suspend fun updateAllLiveCommentaries() {
        val updatedCommentary = _aiCommentary.value.toMutableMap()
        
        // For each current live match, fetch and fuse commentary
        for (match in _currentMatches.value) {
            val cricbuzzUrl = "https://www.cricbuzz.com/live-cricket-scores/${match.id}"
            val keyNames = match.teams.map { it.name }
            
            try {
                val commentary = liveCommentaryManager.getBestCommentaryForMatch(
                    matchTitle = match.matchTitle,
                    cricbuzzId = match.id,
                    cricbuzzUrl = cricbuzzUrl,
                    keyNames = keyNames
                )
                
                // Store latest AI commentary for the match (iOS pattern)
                val latestCommentary = commentary.firstOrNull() ?: ""
                updatedCommentary[match.id] = latestCommentary
                Log.d(TAG, "[AI Commentary] ${match.id}: ${latestCommentary.take(50)}")
                
                // Update probability events based on commentary
                updateProbabilityEventsFromCommentary(match.id, latestCommentary)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Error updating commentary for match ${match.id}", e)
            }
        }
        
        _aiCommentary.value = updatedCommentary
    }
    
    /**
     * iOS-inspired: Check if match data has meaningful changes
     * Used to avoid unnecessary UI updates
     */
    private fun hasMatchChanges(newMatches: List<CricketMatch>): Boolean {
        val currentMatchList = _currentMatches.value
        
        // If the number of matches is different, there's definitely a change
        if (newMatches.size != currentMatchList.size) return true
        
        // Create a dictionary of current matches for quick lookup
        val currentMatchesDict = currentMatchList.associateBy { it.id }
        
        // Check each new match against its current version
        for (newMatch in newMatches) {
            val currentMatch = currentMatchesDict[newMatch.id] ?: return true // New match found
            
            // Compare relevant fields that indicate a change
            if (newMatch.status != currentMatch.status ||
                newMatch.teams.getOrNull(0)?.score != currentMatch.teams.getOrNull(0)?.score ||
                newMatch.teams.getOrNull(1)?.score != currentMatch.teams.getOrNull(1)?.score ||
                newMatch.score?.currentInnings != currentMatch.score?.currentInnings
            ) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * iOS-inspired: Update probability events from commentary
     * Tracks win probability changes over time for graph visualization
     */
    private fun updateProbabilityEventsFromCommentary(matchId: String, commentary: String) {
        val lower = commentary.lowercase()
        val match = _currentMatches.value.find { it.id == matchId } ?: return
        
        // Determine event type from commentary
        val eventType = when {
            lower.contains("wicket") || lower.contains("out") || lower.contains("bowled") || 
            lower.contains("lbw") || lower.contains("stumped") || lower.contains("caught") -> ProbabilityEventType.WICKET
            lower.contains("six") || lower.contains("four") || lower.contains("boundary") || lower.contains("cracked") -> ProbabilityEventType.BOUNDARY
            lower.contains("dot") || lower.contains("0 runs") -> ProbabilityEventType.DOT_BALL
            else -> ProbabilityEventType.OTHER
        }
        
        // Get current events for this match
        val currentEvents = _probabilityEvents.value[matchId]?.toMutableList() ?: mutableListOf()
        val lastEvent = currentEvents.lastOrNull()
        
        // Calculate base probability from match state
        val baseProb = match.calculateHeuristicProbability()
        
        // Adjust probability based on the specific latest event
        val eventImpact = when (eventType) {
            ProbabilityEventType.WICKET -> -8.0
            ProbabilityEventType.BOUNDARY -> +4.0
            ProbabilityEventType.DOT_BALL -> -1.0
            ProbabilityEventType.OTHER -> 0.0
        }
        
        val newProbability = (baseProb + eventImpact).coerceIn(5.0, 95.0)
        
        val ball = currentEvents.size
        val newEvent = ProbabilityEvent(
            timestamp = Date(),
            ball = ball,
            over = ball / 6,
            probability = newProbability,
            eventType = eventType,
            description = commentary.take(100),
            impact = newProbability - (lastEvent?.probability ?: baseProb)
        )
        
        currentEvents.add(newEvent)
        
        // Keep only last 300 events (50 overs worth)
        val trimmedEvents = if (currentEvents.size > 300) currentEvents.takeLast(300) else currentEvents
        
        val updatedMap = _probabilityEvents.value.toMutableMap()
        updatedMap[matchId] = trimmedEvents
        _probabilityEvents.value = updatedMap
        
        // Also update the match state probability immediately for overall UI
        match.winProbability = newProbability
    }
    
    /**
     * Get AI commentary for a specific match
     */
    fun getAICommentaryForMatch(matchId: String): String? {
        return _aiCommentary.value[matchId]
    }
    
    /**
     * Get probability events for a specific match
     */
    fun getProbabilityEventsForMatch(matchId: String): List<ProbabilityEvent> {
        return _probabilityEvents.value[matchId] ?: emptyList()
    }
    
    /**
     * Set first innings end ball for a match (for graph rendering)
     */
    fun setFirstInningsEndBall(matchId: String, ball: Int) {
        val updatedMap = _firstInningsEndBall.value.toMutableMap()
        updatedMap[matchId] = ball
        _firstInningsEndBall.value = updatedMap
    }
    
    private suspend fun updateMatches(status: String) {
        val url = when (status) {
            "Live" -> "https://www.cricbuzz.com/cricket-match/live-scores"
            "Recent" -> "https://www.cricbuzz.com/cricket-match/live-scores/recent-matches"
            "Upcoming" -> "https://www.cricbuzz.com/cricket-match/live-scores/upcoming-matches"
            else -> "https://www.cricbuzz.com/cricket-match/live-scores"
        }
        
        val matches = scraper.fetchLiveMatches(url, status)
        val distinctMatches = matches.distinctBy { it.id }
        
        // Calculate heuristic win probability for each match
        distinctMatches.forEach { match ->
            if (match.isLive) {
                match.winProbability = match.calculateHeuristicProbability()
            }
        }
        
        // Update cache
        MatchCacheService.updateCache(distinctMatches, status)
        cachedMatches[status] = distinctMatches
        
        // Only emit if this is the currently selected status
        if (status == _selectedStatus.value) {
            filterAndEmitMatches(distinctMatches)
        }
    }

    private suspend fun fetchMatchesInternal() {
        try {
            val selectedTab = _selectedStatus.value
            updateMatches(selectedTab)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
        }
    }
}
