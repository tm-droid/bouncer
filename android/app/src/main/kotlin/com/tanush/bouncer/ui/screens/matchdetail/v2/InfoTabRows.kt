package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.R
import com.tanush.bouncer.data.history.ArchivedInfo
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.MatchContext

// The Info tab's rows as (label, value) pairs, built off the composition so they can be tested.

/** What the match is and when and where, as (label, value) rows. A row with nothing to say is left out. */
internal fun infoDetailRows(match: CricketMatch): List<Pair<Int, String>> = buildList {
    add(R.string.match_info_series to match.matchTitle)
    add(R.string.match_info_format to match.matchType)
    add(R.string.match_info_date to match.date)
    if (match.matchTime.isNotBlank()) add(R.string.match_info_time to match.matchTime)
    val day = extractDayInfo(match.currentStatus, match.matchType)
    if (day.isNotBlank()) add(R.string.match_info_day to day)
    // The toss as text only when there is no structured toss for the card below.
    val toss = extractTossInfo(match.info, match.currentStatus)
    if (match.tossWinner == null && toss.isNotBlank()) add(R.string.match_info_toss to toss)
    add(R.string.match_info_venue to match.venue)
}

/**
 * The rows Cricinfo's facts add to the match-info card, in order, as (label, value). A list the
 * page left empty adds no row.
 */
internal fun officialRows(officials: MatchContext.Officials, showUmpires: Boolean): List<Pair<Int, String>> = buildList {
    if (showUmpires && officials.umpires.isNotEmpty()) add(R.string.match_info_umpires to officials.umpires.joinToString(", "))
    if (officials.tvUmpires.isNotEmpty()) add(R.string.match_info_tv_umpire to officials.tvUmpires.joinToString(", "))
    if (officials.referees.isNotEmpty()) add(R.string.match_info_referee to officials.referees.joinToString(", "))
    officials.reviews?.takeIf { it.isNotBlank() }?.let { add(R.string.match_info_reviews to it) }
    // Debutants are on Squads now, beside the elevens they are part of.
}

/** Referee, third umpire and player of the match from a backfilled card; the on-field umpires are on the match. */
internal fun archivedOfficialRows(info: ArchivedInfo): List<Pair<Int, String>> = buildList {
    info.thirdUmpire?.let { add(R.string.match_info_tv_umpire to it) }
    info.referee?.let { add(R.string.match_info_referee to it) }
    if (info.playersOfTheMatch.isNotEmpty()) add(R.string.match_info_player_of_match to info.playersOfTheMatch.joinToString(", "))
}

internal fun extractTossInfo(info: String, status: String): String {
    // Look for "X opted to bat/bowl" patterns
    val tossRegex = Regex("(\\w[\\w\\s]+?)\\s+(opted to|elected to|won the toss and|chose to)\\s+(bat|bowl|field)", RegexOption.IGNORE_CASE)
    val infoMatch = tossRegex.find(info) ?: tossRegex.find(status)
    return infoMatch?.value ?: ""
}

internal fun extractDayInfo(status: String, matchType: String): String {
    if (!matchType.lowercase().contains("test")) return ""
    val dayMatch = Regex("Day\\s+(\\d+)", RegexOption.IGNORE_CASE).find(status)
    return dayMatch?.value ?: ""
}
