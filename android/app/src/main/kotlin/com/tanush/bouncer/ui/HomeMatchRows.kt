package com.tanush.bouncer.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.v2.MatchCardV2
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.screens.dayLabel
import com.tanush.bouncer.ui.screens.groupByDay
import java.time.LocalDate
import java.time.ZoneId

// Home's match rows: one list, or one list per day under a heading.

/**
 * Upcoming and Recent are read by day, under a heading per day, the way the All matches screen's
 * "By date" tab read; Upcoming soonest first, Recent newest first. All and Live stay one list.
 */
internal enum class DayOrder { NONE, ASCENDING, DESCENDING }

internal fun dayOrderFor(status: String): DayOrder = when (tabForStatus(status)) {
    "Upcoming" -> DayOrder.ASCENDING
    "Recent" -> DayOrder.DESCENDING
    else -> DayOrder.NONE
}

/** What every row needs to know beyond its match: the favourite sides and the followed matches. */
internal class RowContext(
    val favouriteNames: Set<String>,
    val following: Set<String>,
    val onMatchClick: (CricketMatch) -> Unit,
)

internal fun LazyListScope.matchRows(rows: List<CricketMatch>, group: String, ctx: RowContext) {
    items(rows, key = { "match_${group}_${it.id}" }, contentType = { "match" }) { match ->
        MatchCardV2(
            match = match,
            onClick = { ctx.onMatchClick(match) },
            modifier = Modifier.fillMaxWidth().animateItem(),
            isFollowing = match.id in ctx.following,
            followable = true,
            isFavouriteTeam = match.hasFavouriteTeam(ctx.favouriteNames),
            shareCrests = true,
        )
    }
}

/**
 * One heading per local day over that day's matches. A match with no start time cannot be placed
 * on a day, so it follows the dated ones under no heading rather than being dropped.
 */
internal fun LazyListScope.dayGroupedRows(rows: List<CricketMatch>, newestFirst: Boolean, ctx: RowContext) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val days = groupByDay(rows, zone).let { if (newestFirst) it.reversed() else it }
    days.forEach { (day, dayMatches) ->
        item(key = "day_$day", contentType = "day") { SectionHeaderV2(title = dayLabel(day, today)) }
        val ordered = if (newestFirst) dayMatches.reversed() else dayMatches
        matchRows(ordered, day.toString(), ctx)
    }
    matchRows(rows.filter { it.startDateMs == null }, "undated", ctx)
}
