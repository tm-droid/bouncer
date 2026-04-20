package com.tanush.bouncer.network

/**
 * Centralized URL constants for CricBuzz scraping endpoints.
 * Avoids duplication of URL strings across ViewModels and scrapers.
 */
object CricBuzzUrls {
    internal const val BASE = "https://www.cricbuzz.com"

    const val LIVE_SCORES = "$BASE/cricket-match/live-scores"
    const val RECENT_MATCHES = "$BASE/cricket-match/live-scores/recent-matches"
    const val UPCOMING_MATCHES = "$BASE/cricket-match/live-scores/upcoming-matches"

    fun liveScoresUrl(matchId: String) = "$BASE/live-cricket-scores/$matchId"
    fun scorecardUrl(matchId: String) = "$BASE/live-cricket-scorecard/$matchId"

    /**
     * Returns the appropriate match list URL for the given status tab.
     */
    fun urlForStatus(status: String): String = when (status) {
        "Live" -> LIVE_SCORES
        "Recent" -> RECENT_MATCHES
        "Upcoming" -> UPCOMING_MATCHES
        else -> LIVE_SCORES
    }
}
