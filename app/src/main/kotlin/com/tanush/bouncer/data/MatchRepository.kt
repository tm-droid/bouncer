package com.tanush.bouncer.data

import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.MatchFlowData
import com.tanush.bouncer.models.PreMatchData
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.network.CricBuzzScraper.ScrapedMatchDetails

/**
 * Abstraction over the match data sources (Cricbuzz / Cricinfo scrapers).
 *
 * ViewModels depend on this interface rather than instantiating scrapers directly,
 * which keeps them testable and lets cross-cutting concerns (caching, logging,
 * rate limiting) live in one place. No DI framework — implementations are
 * constructed manually and passed in via default constructor params.
 *
 * Methods come in two flavours:
 *  - [fetchMatches] returns an [AppResult] for call sites that fold over success/error.
 *  - The remaining methods return raw values and may throw; callers that already
 *    wrap them in their own try/catch + Sentry breadcrumb flow use these directly.
 */
interface MatchRepository {

    /** AppResult-wrapped live match list fetch (used by MainViewModel). */
    suspend fun fetchMatches(url: String, status: String): AppResult<List<CricketMatch>>

    /** Raw live match list fetch (used inside existing try/catch poll loops). */
    suspend fun fetchLiveMatches(url: String, status: String): List<CricketMatch>

    /** Ball-by-ball + batsmen/bowler snapshot for a Cricbuzz match id. */
    suspend fun fetchBallByBallAndBatsmen(matchId: String): ScrapedMatchDetails

    /** Full scorecard for a Cricbuzz match id. */
    suspend fun fetchScorecard(matchId: String): Scorecard

    /** Raw commentary lines for a Cricbuzz live-scores URL. */
    suspend fun fetchLiveCommentary(matchUrl: String): List<String>

    /** Resolve the closest Cricinfo live-match URL + id for a match title, or null. */
    suspend fun findCricinfoMatchUrlAndId(matchTitle: String): Pair<String, String>?

    /** Cricinfo match flow (DRS reviews, powerplays, impact subs). */
    suspend fun fetchMatchFlow(matchUrl: String): MatchFlowData

    /** Cricinfo pre-match data (pitch report + team news), or null. */
    suspend fun fetchPreMatchData(matchUrl: String): PreMatchData?
}
