package com.tanush.bouncer.ui.screens

import com.tanush.bouncer.models.CricketMatch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every match with a known start, grouped by local calendar day, days ascending, matches within a
 * day by start time. A match with no start time cannot be placed on a day and is left out rather
 * than guessed — lists cached before `startDateMs` was kept carry none.
 */
internal fun groupByDay(matches: List<CricketMatch>, zone: ZoneId): List<Pair<LocalDate, List<CricketMatch>>> =
    matches
        .filter { it.startDateMs != null }
        .distinctBy { it.id }
        .sortedBy { it.startDateMs }
        .groupBy { Instant.ofEpochMilli(it.startDateMs!!).atZone(zone).toLocalDate() }
        .toList()

/** "Today", "Tomorrow", "Yesterday", or a short date — what a schedule header says. */
internal fun dayLabel(day: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String = when (day) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
}

/**
 * Index, among the rows [groupByDay] renders (one header per day plus its matches), of the first
 * day that is today or later — or of the last day when every one is past.
 */
internal fun todayOffset(matches: List<CricketMatch>, zone: ZoneId, today: LocalDate = LocalDate.now(zone)): Int {
    val days = groupByDay(matches, zone)
    val target = days.indexOfFirst { !it.first.isBefore(today) }.let { if (it == -1) days.lastIndex else it }
    return days.take(target.coerceAtLeast(0)).sumOf { 1 + it.second.size }
}
