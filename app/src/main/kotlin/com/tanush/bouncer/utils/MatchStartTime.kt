package com.tanush.bouncer.utils

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * Reads Cricbuzz's upcoming-match status — `Match starts at Sep 12, 23:00 GMT` — into the reader's zone.
 *
 * The status was shown verbatim, so every reader outside the UK did time-zone arithmetic to find out
 * when a match starts. It carries no year: the year chosen is the one that puts the start nearest to
 * [now], so a list read on 31 December does not place a 2 January match eleven months in the past.
 *
 * Returns null for any other text; callers show that text as written.
 */
fun parseMatchStart(status: String, now: ZonedDateTime): LocalDateTime? {
    val match = START_PATTERN.matchEntire(status.trim()) ?: return null
    val (monthName, day, hour, minute) = match.destructured
    val month = MONTHS[monthName.lowercase(Locale.ROOT)] ?: return null
    val utcNow = now.withZoneSameInstant(ZoneOffset.UTC)
    val start = (utcNow.year - 1..utcNow.year + 1)
        .mapNotNull { year ->
            runCatching {
                ZonedDateTime.of(year, month.value, day.toInt(), hour.toInt(), minute.toInt(), 0, 0, ZoneOffset.UTC)
            }.getOrNull()
        }
        .minByOrNull { abs(Duration.between(utcNow, it).toMinutes()) }
        ?: return null
    return start.withZoneSameInstant(now.zone).toLocalDateTime()
}

/** Calendar days from [today] to the day [start] falls on: 0 today, 1 tomorrow. */
fun calendarDaysUntil(start: LocalDateTime, today: LocalDate): Long =
    ChronoUnit.DAYS.between(today, start.toLocalDate())

private val START_PATTERN = Regex("""Match starts at ([A-Za-z]{3}) (\d{1,2}), (\d{1,2}):(\d{2}) GMT""")

private val MONTHS: Map<String, Month> =
    listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        .withIndex()
        .associate { (index, name) -> name to Month.of(index + 1) }
