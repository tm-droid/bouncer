package com.tanush.bouncer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.models.AchievementKind
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.MatchFlowData
import com.tanush.bouncer.models.PreMatchData
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import com.tanush.bouncer.models.WinProbabilityPoint
import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.CricBuzzMatchRepository
import com.tanush.bouncer.data.CricketRepository
import com.tanush.bouncer.data.MatchRepository
import com.tanush.bouncer.data.remote.AppwriteMatchCommentaryClient
import com.tanush.bouncer.data.remote.AppwriteMatchDetailClient
import com.tanush.bouncer.data.remote.CommentaryEvent
import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.data.remote.H2HPair
import com.tanush.bouncer.data.remote.H2HRepository
import com.tanush.bouncer.ui.components.BatsmanMilestoneInfo
import com.tanush.bouncer.ui.components.MilestoneType
import com.tanush.bouncer.utils.bouncerExceptionHandler
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MatchDetailViewModel(
    private val match: CricketMatch,
    context: Context,
    private val matchRepository: MatchRepository = CricBuzzMatchRepository(),
) : ViewModel() {

    data class UiState(
        val batsmen: List<ScrapedBatsman> = emptyList(),
        val bowler: ScrapedBowler? = null,
        val recentBalls: List<String> = emptyList(),
        val scorecard: Scorecard? = null,
        val commentary: List<String> = emptyList(),
        val commentaryLoading: Boolean = true,
        val liveTeam1Score: String = "",
        val liveTeam2Score: String = "",
        val liveTeam1Overs: String = "",
        val liveTeam2Overs: String = "",
        val liveCurrentRunRate: String? = null,
        val liveRequiredRunRate: String? = null,
        val matchFlow: MatchFlowData? = null,
        val preMatchData: PreMatchData? = null,
        val probabilityHistory: List<WinProbabilityPoint> = emptyList(),
        val batsmanMilestone: BatsmanMilestoneInfo? = null,
        val headToHead: H2HPair? = null,
    )

    private val appContext = context.applicationContext
    private val firedMilestones = mutableSetOf<String>()
    private var espnMatchUrl: String? = null

    private val _uiState = MutableStateFlow(
        UiState(
            liveTeam1Score = match.teams.getOrNull(0)?.score ?: "",
            liveTeam2Score = match.teams.getOrNull(1)?.score ?: "",
            liveTeam1Overs = match.teams.getOrNull(0)?.overs ?: "",
            liveTeam2Overs = match.teams.getOrNull(1)?.overs ?: "",
            liveCurrentRunRate = match.score?.currentRunRate,
            liveRequiredRunRate = match.score?.requiredRunRate,
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Client-side typewriter reveal of the newest server commentary line. Preserves the
    // cinematic "streaming" feel now that generation happens server-side (no token stream).
    private val _streamingCommentary = MutableStateFlow("")
    val streamingCommentary: StateFlow<String> = _streamingCommentary.asStateFlow()
    private var typewriterJob: Job? = null

    private var unlockedAchievements: Set<AchievementKind> = emptySet()

    init {
        viewModelScope.launch(bouncerExceptionHandler) { loadInitial() }
        viewModelScope.launch(bouncerExceptionHandler) {
            AppwriteMatchDetailClient.events
                .filter { it.matchId == match.id }
                .collect { applyDetail(it.payload) }
        }
        viewModelScope.launch(bouncerExceptionHandler) {
            AppwriteMatchCommentaryClient.events
                .filter { it.matchId == match.id }
                .collect { onNewCommentaryEvent(it) }
        }
    }

    private suspend fun loadInitial() {
        unlockedAchievements = runCatching { AchievementKind.loadUnlocked(appContext) }.getOrDefault(emptySet())

        // Live snapshot (batsmen/bowler/score/scorecard) + commentary feed from Appwrite.
        applyDetail(AppwriteMatchDetailClient.getForMatch(match.id))
        AppResult.runCatchingSuspend {
            val lines = AppwriteMatchCommentaryClient.getForMatch(match.id)
            if (lines.isNotEmpty()) _uiState.update { it.copy(commentary = lines) }
        }.onError { breadcrumb("commentary_load", "Commentary feed load failed: ${it.message}") }
        _uiState.update { it.copy(commentaryLoading = false) }

        loadCricinfoData()

        // Head-to-head (Appwrite Storage) + win-probability history.
        AppResult.runCatchingSuspend {
            val t1 = match.teams.getOrNull(0)?.name ?: return@runCatchingSuspend
            val t2 = match.teams.getOrNull(1)?.name ?: return@runCatchingSuspend
            H2HRepository.getH2H(t1, t2)?.let { h2h -> _uiState.update { it.copy(headToHead = h2h) } }
        }.onError { breadcrumb("h2h", "H2H fetch failed: ${it.message}") }
        CricketRepository.getProbabilityHistory(match.id).onSuccess { history ->
            if (history.isNotEmpty()) _uiState.update { it.copy(probabilityHistory = history) }
        }
    }

    // CricInfo one-shots: match flow (DRS/powerplay/impact subs) + pre-match (pitch + team news).
    private suspend fun loadCricinfoData() {
        val url = espnMatchUrl
            ?: matchRepository.findCricinfoMatchUrlAndId(match.matchTitle)?.first?.also { espnMatchUrl = it }
            ?: return
        AppResult.runCatchingSuspend {
            val flow = matchRepository.fetchMatchFlow(url)
            if (flow.drsReviews.isNotEmpty() || flow.powerplays.isNotEmpty() || flow.impactSubs.isNotEmpty()) {
                _uiState.update { it.copy(matchFlow = flow) }
            }
        }.onError { breadcrumb("matchflow", "Match flow fetch failed: ${it.message}") }
        AppResult.runCatchingSuspend {
            val preMatch = matchRepository.fetchPreMatchData(url) ?: return@runCatchingSuspend
            _uiState.update { it.copy(preMatchData = preMatch) }
        }.onError { breadcrumb("prematch", "Pre-match data fetch failed: ${it.message}") }
    }

    private fun breadcrumb(category: String, message: String) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.category = "match_detail_v2.$category"
            this.message = message
            level = SentryLevel.WARNING
        })
    }

    private fun <T> pick(new: T?, old: T): T = new ?: old

    // Live snapshot updates (one-shot + Realtime) flow through here. Null fields keep prior values.
    private fun applyDetail(p: MatchLiveDetailPayload?) {
        if (p == null) return
        _uiState.update { st ->
            st.copy(
                batsmen = p.batsmen.ifEmpty { st.batsmen },
                bowler = pick(p.bowler, st.bowler),
                recentBalls = p.recentBalls.ifEmpty { st.recentBalls },
                scorecard = pick(p.scorecard?.takeIf { it.innings.isNotEmpty() }, st.scorecard),
                liveTeam1Score = pick(p.team1Score, st.liveTeam1Score),
                liveTeam2Score = pick(p.team2Score, st.liveTeam2Score),
                liveTeam1Overs = pick(p.team1Overs, st.liveTeam1Overs),
                liveTeam2Overs = pick(p.team2Overs, st.liveTeam2Overs),
                liveCurrentRunRate = pick(p.currentRunRate, st.liveCurrentRunRate),
                liveRequiredRunRate = pick(p.requiredRunRate, st.liveRequiredRunRate),
            )
        }
        p.batsmen.forEach { handleBatsmanMilestone(it) }
        p.bowler?.let { if ((it.wickets.toIntOrNull() ?: 0) >= 5) tryUnlock(AchievementKind.WICKETS_5) }
    }

    // New server commentary line: prepend to the feed (dedup) and typewriter-reveal it.
    private fun onNewCommentaryEvent(ev: CommentaryEvent) {
        var isNew = false
        _uiState.update { st ->
            if (st.commentary.contains(ev.commentary)) st
            else {
                isNew = true
                st.copy(commentary = listOf(ev.commentary) + st.commentary)
            }
        }
        if (!isNew) return
        typewriterJob?.cancel()
        typewriterJob = viewModelScope.launch {
            _streamingCommentary.value = ""
            val sb = StringBuilder(ev.commentary.length)
            for (ch in ev.commentary) {
                sb.append(ch)
                _streamingCommentary.value = sb.toString()
                delay(TYPEWRITER_CHAR_DELAY_MS)
            }
            delay(TYPEWRITER_HOLD_MS)
            _streamingCommentary.value = ""
        }
    }

    private fun handleBatsmanMilestone(b: ScrapedBatsman) {
        val runs = b.runs.toIntOrNull() ?: 0
        if (runs >= 50) tryUnlock(AchievementKind.RUNS_50)
        if (runs >= 100) tryUnlock(AchievementKind.RUNS_100)
        val milestoneType = milestoneTypeFor(runs) ?: return
        if (firedMilestones.add("${b.name}_${milestoneType.runs}")) emitBatsmanMilestone(b, runs, milestoneType)
    }

    private fun milestoneTypeFor(runs: Int): MilestoneType? = when {
        runs >= 200 -> MilestoneType.DOUBLE_HUNDRED
        runs >= 100 -> MilestoneType.HUNDRED
        runs >= 50 -> MilestoneType.FIFTY
        else -> null
    }

    private fun emitBatsmanMilestone(b: ScrapedBatsman, runs: Int, milestoneType: MilestoneType) {
        _uiState.update { it.copy(batsmanMilestone = BatsmanMilestoneInfo(
            playerName = b.name,
            teamName = match.teams.firstOrNull { t -> t.isBatting }?.name ?: "",
            currentRuns = runs,
            balls = b.balls.toIntOrNull() ?: 0,
            fours = b.fours.toIntOrNull() ?: 0,
            sixes = b.sixes.toIntOrNull() ?: 0,
            strikeRate = b.strikeRate.toFloatOrNull() ?: 0f,
            milestone = milestoneType,
            isNewMilestone = true,
        ))}
        val playerName = b.name
        viewModelScope.launch {
            delay(MILESTONE_BANNER_MS)
            _uiState.update { s ->
                if (s.batsmanMilestone?.playerName == playerName) s.copy(batsmanMilestone = null) else s
            }
        }
    }

    private fun tryUnlock(kind: AchievementKind) {
        if (unlockedAchievements.contains(kind)) return
        runCatching { AchievementKind.unlock(appContext, kind) }
        unlockedAchievements = unlockedAchievements + kind
    }

    companion object {
        private const val TYPEWRITER_CHAR_DELAY_MS = 18L
        private const val TYPEWRITER_HOLD_MS = 1500L
        private const val MILESTONE_BANNER_MS = 15_000L

        fun factory(
            context: Context,
            match: CricketMatch,
            matchRepository: MatchRepository = CricBuzzMatchRepository(),
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MatchDetailViewModel(match, context.applicationContext, matchRepository) as T
            }
    }
}
