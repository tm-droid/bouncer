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
import com.tanush.bouncer.data.CricBuzzMatchRepository
import com.tanush.bouncer.data.MatchRepository
import com.tanush.bouncer.services.LiveCommentaryManager
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    )

    private val appContext = context.applicationContext
    private val commentaryManager = LiveCommentaryManager()
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
    val streamingCommentary: StateFlow<String> = commentaryManager.streamingCommentary

    // In-memory cache to avoid repeated SharedPreferences reads on every poll tick.
    private var unlockedAchievements: Set<AchievementKind> = emptySet()

    init {
        viewModelScope.launch { startLiveDataFlow() }
    }

    private suspend fun startLiveDataFlow() {
        unlockedAchievements = runCatching { AchievementKind.loadUnlocked(appContext) }.getOrDefault(emptySet())

        // Seed commentary history once before the polling loop.
        try {
            val url = "https://www.cricbuzz.com/live-cricket-scores/${match.id}"
            val allLines = matchRepository.fetchLiveCommentary(url)
            if (allLines.isNotEmpty()) {
                commentaryManager.seedHistory(allLines)
                _uiState.update { it.copy(commentary = commentaryManager.getSeededHistory()) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "match_detail_v2.seed"
                message = "Commentary seed failed: ${e.message}"
                level = SentryLevel.WARNING
            })
        } finally {
            _uiState.update { it.copy(commentaryLoading = false) }
        }

        // Fetch match flow once (DRS reviews, powerplay summaries, impact subs).
        try {
            val url = espnMatchUrl ?: run {
                matchRepository.findCricinfoMatchUrlAndId(match.matchTitle)?.first.also { espnMatchUrl = it }
            }
            if (url != null) {
                val flow = matchRepository.fetchMatchFlow(url)
                if (flow.drsReviews.isNotEmpty() || flow.powerplays.isNotEmpty() || flow.impactSubs.isNotEmpty()) {
                    _uiState.update { it.copy(matchFlow = flow) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "match_detail_v2.matchflow"
                message = "Match flow fetch failed: ${e.message}"
                level = SentryLevel.WARNING
            })
        }

        // Fetch pre-match data (pitch report + team news) once at startup.
        try {
            val url = espnMatchUrl ?: run {
                matchRepository.findCricinfoMatchUrlAndId(match.matchTitle)?.first.also { espnMatchUrl = it }
            }
            if (url != null) {
                val preMatch = matchRepository.fetchPreMatchData(url)
                if (preMatch != null) {
                    _uiState.update { it.copy(preMatchData = preMatch) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "match_detail_v2.prematch"
                message = "Pre-match data fetch failed: ${e.message}"
                level = SentryLevel.WARNING
            })
        }

        if (!match.isLive) return

        while (true) {
            try {
                val details = matchRepository.fetchBallByBallAndBatsmen(match.id)
                val scorecard = matchRepository.fetchScorecard(match.id)

                val keyNames = details.batsmen.map { it.name }.toMutableList()
                details.bowler?.let { keyNames.add(it.name) }
                val commentary = commentaryManager.getBestCommentaryForMatch(
                    matchTitle = match.matchTitle,
                    cricbuzzId = match.id,
                    cricbuzzUrl = "https://www.cricbuzz.com/live-cricket-scores/${match.id}",
                    keyNames = keyNames,
                    isMatchComplete = match.isCompleted,
                )

                _uiState.update { state ->
                    state.copy(
                        batsmen = details.batsmen,
                        bowler = details.bowler,
                        recentBalls = details.recentBalls,
                        scorecard = scorecard,
                        commentary = commentary,
                        liveTeam1Score = details.team1Score ?: state.liveTeam1Score,
                        liveTeam2Score = details.team2Score ?: state.liveTeam2Score,
                        liveTeam1Overs = details.team1Overs ?: state.liveTeam1Overs,
                        liveTeam2Overs = details.team2Overs ?: state.liveTeam2Overs,
                        liveCurrentRunRate = details.currentRunRate ?: state.liveCurrentRunRate,
                        liveRequiredRunRate = details.requiredRunRate ?: state.liveRequiredRunRate,
                    )
                }

                checkAchievements(details.batsmen, details.bowler)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "match_detail_v2.refresh"
                    message = "Live data refresh failed"
                    level = SentryLevel.WARNING
                    setData("matchId", match.id)
                })
                Sentry.captureException(e)
            }
            delay(8000)
        }
    }

    private fun checkAchievements(batsmen: List<ScrapedBatsman>, bowler: ScrapedBowler?) {
        for (b in batsmen) {
            val runs = b.runs.toIntOrNull() ?: 0
            if (runs >= 50) tryUnlock(AchievementKind.RUNS_50)
            if (runs >= 100) tryUnlock(AchievementKind.RUNS_100)
        }
        bowler?.let { b ->
            val w = b.wickets.toIntOrNull() ?: 0
            if (w >= 5) tryUnlock(AchievementKind.WICKETS_5)
        }
    }

    private fun tryUnlock(kind: AchievementKind) {
        if (unlockedAchievements.contains(kind)) return
        runCatching { AchievementKind.unlock(appContext, kind) }
        unlockedAchievements = unlockedAchievements + kind
    }

    override fun onCleared() {
        super.onCleared()
        commentaryManager.clearMatchState(match.id)
        commentaryManager.close()
    }

    companion object {
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
