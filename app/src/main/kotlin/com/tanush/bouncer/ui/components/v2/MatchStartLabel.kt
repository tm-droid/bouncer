package com.tanush.bouncer.ui.components.v2

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.screens.dayLabel
import com.tanush.bouncer.utils.calendarDaysUntil
import com.tanush.bouncer.utils.parseMatchStart
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * A match's status line as a reader wants it. Cricbuzz's `Match starts at Sep 12, 23:00 GMT` becomes
 * "Starts tomorrow, 7:00 AM" in the device's zone and clock format; any other status is unchanged.
 */
@Composable
internal fun matchStatusText(status: String): String {
    val start = remember(status) { parseMatchStart(status, ZonedDateTime.now()) } ?: return status
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    // Formatted once per status: the clock setting is a Settings lookup and each pattern is
    // compiled, and every upcoming card on a scrolling list recomposes this.
    val (time, day) = remember(start, locale) {
        val clockPattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        DateTimeFormatter.ofPattern(clockPattern, locale).format(start) to
            DateTimeFormatter.ofPattern("EEE d MMM", locale).format(start)
    }
    return when (calendarDaysUntil(start, LocalDate.now())) {
        0L -> stringResource(R.string.match_starts_today, time)
        1L -> stringResource(R.string.match_starts_tomorrow, time)
        else -> stringResource(R.string.match_starts_on, day, time)
    }
}

/** An upcoming match's start as the score column shows it: "19:30" over "Tomorrow". */
@Immutable
internal data class MatchStartParts(val time: String, val day: String)

/**
 * [MatchStartParts] for a match that has not started, from Cricbuzz's "Match starts at …" line or,
 * failing that, the list's start timestamp. Null when neither gives a time, or for a match whose
 * status says something else (a delay, a toss), so that line keeps the row.
 */
@Composable
internal fun rememberMatchStartParts(match: CricketMatch): MatchStartParts? {
    if (match.isLive || match.isCompleted) return null
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(match.currentStatus, match.startDateMs, locale) {
        val zone = ZoneId.systemDefault()
        val start = matchStartTime(match.currentStatus, match.startDateMs, ZonedDateTime.now(zone)) ?: return@remember null
        val clockPattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        MatchStartParts(
            time = DateTimeFormatter.ofPattern(clockPattern, locale).format(start),
            day = dayLabel(start.toLocalDate(), LocalDate.now(zone), locale),
        )
    }
}

/**
 * When an unstarted match begins, in [now]'s zone: Cricbuzz's "Match starts at …" line first, the
 * list's start timestamp when the line is blank or unparseable. A status that says anything else —
 * "Toss delayed due to rain" — wins, and this returns null so that line keeps the row.
 */
internal fun matchStartTime(status: String, startDateMs: Long?, now: ZonedDateTime): LocalDateTime? {
    parseMatchStart(status, now)?.let { return it }
    if (status.isNotBlank() && !status.trim().startsWith("Match starts", ignoreCase = true)) return null
    return startDateMs?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), now.zone) }
}
