package com.tanush.bouncer.ui.components.v2

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.Text
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
import kotlinx.coroutines.delay

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

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val ABSOLUTE_FROM_MS = 12 * HOUR_MS

/** How far off an unstarted match's start is, in the words the countdown uses. */
internal sealed interface StartLabel {
    data object Now : StartLabel
    data class InMinutes(val minutes: Int) : StartLabel
    data class InHoursMinutes(val hours: Int, val minutes: Int) : StartLabel
    data object Absolute : StartLabel
}

/**
 * Under a minute ahead, or any start already past, is [StartLabel.Now]: a not-yet-live match whose start
 * has gone by is delayed or about to be flipped live, and "in 0 min" or a negative count would be wrong.
 * Minutes round down, so a start 59m59s away reads "in 59 min", never a premature "in 1 hr".
 */
internal fun startLabelOf(startMs: Long, nowMs: Long): StartLabel {
    val ahead = startMs - nowMs
    return when {
        ahead < MINUTE_MS -> StartLabel.Now
        ahead < HOUR_MS -> StartLabel.InMinutes((ahead / MINUTE_MS).toInt())
        ahead < ABSOLUTE_FROM_MS -> StartLabel.InHoursMinutes(
            hours = (ahead / HOUR_MS).toInt(),
            minutes = (ahead % HOUR_MS / MINUTE_MS).toInt(),
        )
        else -> StartLabel.Absolute
    }
}

/**
 * Countdown to [startMs]: "Starting now", "in 42 min", "in 2h 15m", then "Tomorrow, 7:00 AM" from 12 h out.
 * Re-evaluates on each minute boundary; no animation and no live region, so TalkBack is not interrupted.
 */
@Composable
internal fun MatchStartLabel(
    startMs: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val nowMs by produceState(System.currentTimeMillis()) {
        while (true) {
            val now = System.currentTimeMillis()
            value = now
            delay(MINUTE_MS - now % MINUTE_MS)
        }
    }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val text = when (val label = startLabelOf(startMs, nowMs)) {
        StartLabel.Now -> stringResource(R.string.start_now)
        is StartLabel.InMinutes -> pluralStringResource(R.plurals.start_in_min, label.minutes, label.minutes)
        is StartLabel.InHoursMinutes ->
            if (label.minutes == 0) pluralStringResource(R.plurals.start_in_hr, label.hours, label.hours)
            else stringResource(R.string.start_in_hr_min, label.hours, label.minutes)
        StartLabel.Absolute -> remember(startMs, locale, nowMs / (60 * MINUTE_MS)) {
            val zone = ZoneId.systemDefault()
            val start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startMs), zone)
            val clockPattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
            "${dayLabel(start.toLocalDate(), LocalDate.now(zone), locale)}, " +
                DateTimeFormatter.ofPattern(clockPattern, locale).format(start)
        }
    }
    Text(text = text, style = style, color = color, modifier = modifier, maxLines = 1)
}
