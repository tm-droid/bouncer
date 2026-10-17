package com.tanush.bouncer.utils

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A date range without the repetition: "7–20 Aug 2026", "7 Aug – 20 Sep 2026",
 * "28 Dec 2026 – 4 Jan 2027". A missing or equal end is the start alone.
 */
fun formatDateRange(start: LocalDate, end: LocalDate?, locale: Locale = Locale.ENGLISH): String {
    val full = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    if (end == null || end == start) return start.format(full)
    val tail = end.format(full)
    return when {
        start.year != end.year -> "${start.format(full)} – $tail"
        start.month != end.month -> "${start.format(DateTimeFormatter.ofPattern("d MMM", locale))} – $tail"
        else -> "${start.dayOfMonth}–$tail"
    }
}

/**
 * [formatDateRange] over Cricbuzz's epoch-millisecond strings. A value that is not a number is shown
 * as it came — "TBC" is real data — and a blank start shows nothing.
 */
fun formatEpochDateRange(startRaw: String, endRaw: String, zone: ZoneId = ZoneId.systemDefault()): String {
    fun day(raw: String): LocalDate? = raw.trim().toLongOrNull()?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    val start = day(startRaw) ?: return startRaw.trim()
    val end = day(endRaw)
    if (end == null && endRaw.isNotBlank()) return "${formatDateRange(start, null)} – ${endRaw.trim()}"
    return formatDateRange(start, end)
}
