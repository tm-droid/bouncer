package com.tanush.bouncer.network

/**
 * Centralized URL constants for CricBuzz scraping endpoints.
 * Avoids duplication of URL strings across ViewModels and scrapers.
 */
object CricBuzzUrls {
    internal const val BASE = "https://www.cricbuzz.com"

    const val LIVE_SCORES = "$BASE/cricket-match/live-scores"
    const val RECENT_MATCHES = "$BASE/cricket-match/live-scores/recent-matches"
    // Not `live-scores/upcoming-matches`: since at least 2026-09-14 that page carries only the live
    // ticker (every match Complete or at a break) and no fixtures, so the Upcoming tab was empty.
    const val UPCOMING_MATCHES = "$BASE/cricket-schedule/upcoming-series/all"

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
