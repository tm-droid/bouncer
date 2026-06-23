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
import com.tanush.bouncer.network.CricBuzzUrls
import com.tanush.bouncer.services.LiveCommentaryManager
import com.tanush.bouncer.data.remote.H2HPair
import com.tanush.bouncer.data.remote.H2HRepository
import com.tanush.bouncer.ui.components.BatsmanMilestoneInfo
import com.tanush.bouncer.ui.components.MilestoneType
import com.tanush.bouncer.utils.bouncerExceptionHandler
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
        val probabilityHistory: List<WinProbabilityPoint> = emptyList(),
        val batsmanMilestone: BatsmanMilestoneInfo? = null,
        val headToHead: H2HPair? = null,
    )

    private val appContext = context.applicationContext
    private val commentaryManager = LiveCommentaryManager()
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
    val streamingCommentary: StateFlow<String> = commentaryManager.streamingCommentary

    // In-memory cache to avoid repeated SharedPreferences reads on every poll tick.
    private var unlockedAchievements: Set<AchievementKind> = emptySet()

    init {
        viewModelScope.launch(bouncerExceptionHandler) { startLiveDataFlow() }
    }

    private suspend fun startLiveDataFlow() {
        unlockedAchievements = runCatching { AchievementKind.loadUnlocked(appContext) }.getOrDefault(emptySet())

        // Seed commentary history once before the polling loop.
        try {
            val url = CricBuzzUrls.liveScoresUrl(match.id)
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
        AppResult.runCatchingSuspend {
            val url = espnMatchUrl ?: run {
                matchRepository.findCricinfoMatchUrlAndId(match.matchTitle)?.first.also { espnMatchUrl = it }
            }
            if (url != null) {
                val flow = matchRepository.fetchMatchFlow(url)
                if (flow.drsReviews.isNotEmpty() || flow.powerplays.isNotEmpty() || flow.impactSubs.isNotEmpty()) {
                    _uiState.update { it.copy(matchFlow = flow) }
                }
            }
        }.onError {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "match_detail_v2.matchflow"
                message = "Match flow fetch failed: ${it.message}"
                level = SentryLevel.WARNING
            })
        }

        // Fetch head-to-head stats from Appwrite Storage (built by train-win-probability function).
        AppResult.runCatchingSuspend {
            val t1 = match.teams.getOrNull(0)?.name ?: return@runCatchingSuspend
            val t2 = match.teams.getOrNull(1)?.name ?: return@runCatchingSuspend
            val h2h = H2HRepository.getH2H(t1, t2)
            if (h2h != null) _uiState.update { it.copy(headToHead = h2h) }
        }.onError {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "match_detail_v2.h2h"
                message = "H2H fetch failed: ${it.message}"
                level = SentryLevel.WARNING
            })
        }

        // Fetch pre-match data (pitch report + team news) once at startup.
        AppResult.runCatchingSuspend {
            val url = espnMatchUrl ?: run {
                matchRepository.findCricinfoMatchUrlAndId(match.matchTitle)?.first.also { espnMatchUrl = it }
            }
            if (url != null) {
                val preMatch = matchRepository.fetchPreMatchData(url)
                if (preMatch != null) {
                    _uiState.update { it.copy(preMatchData = preMatch) }
                }
            }
        }.onError {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "match_detail_v2.prematch"
                message = "Pre-match data fetch failed: ${it.message}"
                level = SentryLevel.WARNING
            })
        }

        // Fetch scorecard once for all match states (live polling updates it below for live matches).
        AppResult.runCatchingSuspend {
            val scorecard = matchRepository.fetchScorecard(match.id)
            _uiState.update { it.copy(scorecard = scorecard) }
        }.onError {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "match_detail_v2.scorecard_once"
                message = "One-shot scorecard fetch failed: ${it.message}"
                level = SentryLevel.WARNING
            })
        }

        // For completed/upcoming matches, try to surface cached commentary once.
        if (_uiState.value.commentary.isEmpty()) {
            AppResult.runCatchingSuspend {
                val commentary = commentaryManager.getBestCommentaryForMatch(
                    matchTitle = match.matchTitle,
                    cricbuzzId = match.id,
                    cricbuzzUrl = CricBuzzUrls.liveScoresUrl(match.id),
                    keyNames = emptyList(),
                    isMatchComplete = match.isCompleted,
                )
                if (commentary.isNotEmpty()) {
                    _uiState.update { it.copy(commentary = commentary) }
                }
            }.onError {
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "match_detail_v2.commentary_once"
                    message = "One-shot commentary fetch failed: ${it.message}"
                    level = SentryLevel.WARNING
                })
            }
        }

        // Fetch win probability history once (fire-and-forget; errors logged in CricketRepository).
        CricketRepository.getProbabilityHistory(match.id).onSuccess { history ->
            if (history.isNotEmpty()) _uiState.update { it.copy(probabilityHistory = history) }
        }

        // match.isCompleted = definitively over; one-shot above already ran.
        // match.isLive false but not completed = stale snapshot from the list; must still poll commentary.
        if (match.isCompleted) return

        while (true) {
            try {
                if (match.isLive) {
                    val details = matchRepository.fetchBallByBallAndBatsmen(match.id)
                    val scorecard = matchRepository.fetchScorecard(match.id)

                    val keyNames = details.batsmen.map { it.name }.toMutableList()
                    details.bowler?.let { keyNames.add(it.name) }
                    val commentary = commentaryManager.getBestCommentaryForMatch(
                        matchTitle = match.matchTitle,
                        cricbuzzId = match.id,
                        cricbuzzUrl = CricBuzzUrls.liveScoresUrl(match.id),
                        keyNames = keyNames,
                        isMatchComplete = match.isCompleted,
                    )

                    _uiState.update { state ->
                        state.copy(
                            batsmen = details.batsmen,
                            bowler = details.bowler,
                            recentBalls = details.recentBalls,
                            scorecard = scorecard.takeIf { it.innings.isNotEmpty() } ?: state.scorecard,
                            commentary = if (commentary.isNotEmpty()) commentary else state.commentary,
                            liveTeam1Score = details.team1Score ?: state.liveTeam1Score,
                            liveTeam2Score = details.team2Score ?: state.liveTeam2Score,
                            liveTeam1Overs = details.team1Overs ?: state.liveTeam1Overs,
                            liveTeam2Overs = details.team2Overs ?: state.liveTeam2Overs,
                            liveCurrentRunRate = details.currentRunRate ?: state.liveCurrentRunRate,
                            liveRequiredRunRate = details.requiredRunRate ?: state.liveRequiredRunRate,
                        )
                    }

                    checkMilestonesAndAchievements(details.batsmen, details.bowler)
                } else {
                    // isLive is stale false — poll commentary only; hits memory LRU → Room before scraping
                    val commentary = commentaryManager.getBestCommentaryForMatch(
                        matchTitle = match.matchTitle,
                        cricbuzzId = match.id,
                        cricbuzzUrl = CricBuzzUrls.liveScoresUrl(match.id),
                        keyNames = emptyList(),
                        isMatchComplete = match.isCompleted,
                    )
                    if (commentary.isNotEmpty()) {
                        _uiState.update { it.copy(commentary = commentary) }
                    }
                }
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
            delay(if (match.isLive) 8000L else 30_000L)
        }
    }

    private fun checkMilestonesAndAchievements(batsmen: List<ScrapedBatsman>, bowler: ScrapedBowler?) {
        for (b in batsmen) {
            val runs = b.runs.toIntOrNull() ?: 0
            if (runs >= 50) tryUnlock(AchievementKind.RUNS_50)
            if (runs >= 100) tryUnlock(AchievementKind.RUNS_100)
            val milestoneType = when {
                runs >= 200 -> MilestoneType.DOUBLE_HUNDRED
                runs >= 100 -> MilestoneType.HUNDRED
                runs >= 50 -> MilestoneType.FIFTY
                else -> null
            } ?: continue
            val key = "${b.name}_${milestoneType.runs}"
            if (firedMilestones.add(key)) {
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
                    delay(15_000)
                    _uiState.update { s ->
                        if (s.batsmanMilestone?.playerName == playerName) s.copy(batsmanMilestone = null) else s
                    }
                }
            }
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
