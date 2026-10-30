package com.tanush.bouncer.ui.widgets

import android.content.Context
import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.services.MatchCacheService

/**
 * The fixture the countdown widget counts down to: a followed team's next match, or else the next
 * match of any. Only fixtures with a known start time still ahead of [nowMs] qualify — a countdown
 * to a guess is worse than no countdown.
 */
internal fun nextFixture(upcoming: List<CricketMatch>, favouriteNames: Set<String>, nowMs: Long): CricketMatch? {
    val ahead = upcoming
        .filter { (it.startDateMs ?: 0L) > nowMs && it.teams.size >= 2 }
        .sortedBy { it.startDateMs }
    return ahead.firstOrNull { match -> match.teams.any { it.name.trim().lowercase() in favouriteNames } }
        ?: ahead.firstOrNull()
}

/**
 * Gives the countdown widget its match.
 *
 * Nothing did. `CountdownWidget.updateCountdownData` had no caller, so the preferences the widget
 * reads were never written and it showed "no upcoming match" on every device it was ever placed
 * on. The fixtures come from the lists the app already keeps; this fetches nothing.
 */
internal object CountdownFeed {

    suspend fun refresh(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val upcoming = MatchCacheService.getMatches(MatchCacheService.UPCOMING)
            ?: MatchCacheService.getOfflineMatches(MatchCacheService.UPCOMING)
            // Kept without an age limit, so a widget on a phone that has not opened the app
            // today still has the fixtures it last saw.
            ?: MatchCacheService.lastKnownUpcoming(nowMs)
        val favourites = FavouriteTeamsManager.favourites.value.map { it.trim().lowercase() }.toSet()
        val match = nextFixture(upcoming, favourites, nowMs)
        if (match == null) {
            CountdownWidget.forgetMatch(context)
            return
        }
        CountdownWidget.rememberMatch(
            context,
            ScheduledMatchData(
                matchId = match.id,
                team1Name = match.teams[0].name,
                team2Name = match.teams[1].name,
                matchTitle = match.seriesName.ifBlank { match.matchTitle },
                venue = match.venue,
                startTimeMillis = match.startDateMs ?: return,
                matchType = match.matchType,
            ),
        )
    }
}
