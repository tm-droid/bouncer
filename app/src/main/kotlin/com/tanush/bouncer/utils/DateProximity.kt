package com.tanush.bouncer.utils

import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.math.abs

/**
 * Calendar-correct date distance.
 *
 * Replaces `(deltaYears * 365 + deltaMonths * 30 + deltaDays)`, which is wrong in both directions
 * at a month boundary: 2024-01-31 and 2024-02-01 came to 0 and read as the SAME day, while
 * 2024-02-28 and 2024-03-01 came to 3 and read as not adjacent. Leap years and 31-day months make
 * the error worse, not better.
 */

private val ISO_DATE_PREFIX = """^(\d{4}-\d{2}-\d{2})""".toRegex()

/**
 * Parse a leading `YYYY-MM-DD`, or null when the string does not start with a real date.
 *
 * Strict, unlike the blind `take(10)` it replaces — that fed whatever ten characters it found to
 * an integer split and relied on an exception to notice.
 */
fun parseIsoDatePrefix(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    val match = ISO_DATE_PREFIX.find(value.trim()) ?: return null
    return try {
        LocalDate.parse(match.groupValues[1])
    } catch (e: DateTimeParseException) {
        null // e.g. 2024-02-31
    }
}

/** Whole days between two ISO date strings, or null when either cannot be parsed. */
fun daysBetween(first: String?, second: String?): Long? {
    val a = parseIsoDatePrefix(first) ?: return null
    val b = parseIsoDatePrefix(second) ?: return null
    return abs(a.toEpochDay() - b.toEpochDay())
}
