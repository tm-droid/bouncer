package com.tanush.bouncer.data

import com.tanush.bouncer.data.remote.SharedMatchDetailClient
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.MatchFlowData
import com.tanush.bouncer.models.PreMatchData
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.network.CricBuzzScraper
import com.tanush.bouncer.network.CricBuzzScraper.ScrapedMatchDetails
import com.tanush.bouncer.network.CricInfoScraper

/**
 * Default [MatchRepository] backed by the Cricbuzz + Cricinfo Jsoup scrapers.
 *
 * Scrapers are injected via default constructor params so tests can substitute
 * fakes without a DI framework.
 */
class CricBuzzMatchRepository(
    private val scraper: CricBuzzScraper = CricBuzzScraper(),
    private val cricinfo: CricInfoScraper = CricInfoScraper(),
) : MatchRepository {

    override suspend fun fetchSharedDetail(matchId: String) = SharedMatchDetailClient.get(matchId)

    override suspend fun fetchMatches(url: String, status: String): AppResult<List<CricketMatch>> =
        AppResult.runCatchingSuspend { scraper.fetchLiveMatches(url, status) }

    override suspend fun fetchLiveMatches(url: String, status: String): List<CricketMatch> =
        scraper.fetchLiveMatches(url, status)

    override suspend fun fetchBallByBallAndBatsmen(matchId: String): ScrapedMatchDetails =
        SharedMatchDetailClient.get(matchId).detailSnapshot()?.payload?.let { p ->
            ScrapedMatchDetails(p.recentBalls, p.batsmen, p.bowler, null, p.score,
                p.team1Score, p.team2Score, p.team1Overs, p.team2Overs, p.currentRunRate, p.requiredRunRate)
        } ?: ScrapedMatchDetails(emptyList(), emptyList(), null, null)

    override suspend fun fetchScorecard(matchId: String): Scorecard =
        SharedMatchDetailClient.get(matchId).availableScorecard() ?: Scorecard()

    override suspend fun fetchLiveCommentary(matchUrl: String): List<String> =
        SharedMatchDetailClient.get(matchUrl.substringAfter("/live-cricket-scores/").substringBefore('/')).rawCommentary()

    override suspend fun findCricinfoMatchUrlAndId(matchTitle: String): Pair<String, String>? =
        cricinfo.findClosestLiveMatchUrlAndId(matchTitle)

    override suspend fun fetchMatchFlow(matchUrl: String): MatchFlowData =
        cricinfo.fetchMatchFlow(matchUrl)

    override suspend fun fetchPreMatchData(matchUrl: String): PreMatchData? =
        cricinfo.fetchPreMatchData(matchUrl)
}
