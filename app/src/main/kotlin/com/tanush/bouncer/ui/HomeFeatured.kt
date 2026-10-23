package com.tanush.bouncer.ui

import com.tanush.bouncer.models.CricketMatch
import java.util.concurrent.TimeUnit

enum class FeaturedKind { Live, Upcoming }

data class Featured(val match: CricketMatch, val kind: FeaturedKind)

data class FeaturedSplit(val featured: Featured?, val rows: List<CricketMatch>)

/**
 * Flip once the hero card can draw an Upcoming match (unit U9). Until then an Upcoming pick would
 * render as a live card, so [featuredFor] is asked not to make one.
 */
internal const val HERO_SHOWS_UPCOMING = false

private val UPCOMING_WINDOW_MS = TimeUnit.HOURS.toMillis(24)

/**
 * Picks the hero: a followed team's live match (best-ranked favourite first), else the soonest
 * followed match starting within 24 h, else any live match. The rows put followed teams on top.
 * [favouritesOrdered] must already be trimmed and lower-cased, most-wanted team first.
 */
fun featuredFor(
    matches: List<CricketMatch>,
    favouritesOrdered: List<String>,
    nowMs: Long,
    allowUpcoming: Boolean = true,
): FeaturedSplit {
    val names = favouritesOrdered.toSet()
    val followed = matches.filter { it.hasFavouriteTeam(names) }
    val featured = followed.filter { it.isLive }.minByOrNull { it.bestRank(favouritesOrdered) }
        ?.let { Featured(it, FeaturedKind.Live) }
        ?: soonestFollowed(followed, nowMs).takeIf { allowUpcoming }?.let { Featured(it, FeaturedKind.Upcoming) }
        ?: matches.firstOrNull { it.isLive }?.let { Featured(it, FeaturedKind.Live) }
    val (followedRows, others) = matches
        .filterNot { it.id == featured?.match?.id }
        .partition { it.hasFavouriteTeam(names) }
    return FeaturedSplit(featured, followedRows + others)
}

private fun soonestFollowed(followed: List<CricketMatch>, nowMs: Long): CricketMatch? =
    followed
        .filter { it.isUpcoming && (it.startDateMs ?: Long.MAX_VALUE) <= nowMs + UPCOMING_WINDOW_MS }
        .minByOrNull { it.startDateMs ?: Long.MAX_VALUE }

private fun CricketMatch.bestRank(favouritesOrdered: List<String>): Int =
    teams.minOf { t -> favouritesOrdered.indexOf(t.name.trim().lowercase()).let { if (it < 0) Int.MAX_VALUE else it } }
