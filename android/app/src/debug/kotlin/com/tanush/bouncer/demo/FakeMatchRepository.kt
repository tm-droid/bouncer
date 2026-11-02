package com.tanush.bouncer.demo

import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.MatchRepository
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.MatchFlowData
import com.tanush.bouncer.models.PreMatchData
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.network.CricBuzzScraper.ScrapedMatchDetails

/**
 * Zero-I/O [MatchRepository] serving [ShowcaseScript] data for the showcase video.
 * Returning null from [findCricinfoMatchUrlAndId] short-circuits the Cricinfo flow,
 * so [fetchMatchFlow] is never reached.
 */
internal class FakeMatchRepository(private val elapsed: () -> Long) : MatchRepository {

    override suspend fun fetchMatches(url: String, status: String): AppResult<List<CricketMatch>> =
        AppResult.Success(fetchLiveMatches(url, status))

    override suspend fun fetchLiveMatches(url: String, status: String): List<CricketMatch> =
        when (status) {
            "Live" -> listOf(ShowcaseScript.matchAt(elapsed()))
            "Upcoming" -> ShowcaseScript.upcomingMatches()
            else -> ShowcaseScript.recentMatches()
        }

    override suspend fun fetchBallByBallAndBatsmen(matchId: String): ScrapedMatchDetails =
        ShowcaseScript.scrapedDetailsAt(elapsed())

    override suspend fun fetchScorecard(matchId: String): Scorecard =
        ShowcaseScript.detailPayloadAt(elapsed()).scorecard ?: Scorecard()

    override suspend fun fetchLiveCommentary(matchUrl: String): List<String> =
        ShowcaseScript.commentaryLines()

    override suspend fun findCricinfoMatchUrlAndId(matchTitle: String): Pair<String, String>? = null

    override suspend fun fetchMatchFlow(matchUrl: String): MatchFlowData =
        error("unreachable in showcase: findCricinfoMatchUrlAndId returns null")

    override suspend fun fetchPreMatchData(matchUrl: String): PreMatchData? = null
}
