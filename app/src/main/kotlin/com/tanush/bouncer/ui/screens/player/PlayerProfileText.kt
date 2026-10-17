package com.tanush.bouncer.ui.screens.player

import com.tanush.bouncer.models.PlayerProfile
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

internal fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

/**
 * "5 Nov 1988 · 37" from Cricbuzz's "November 05, 1988" (sometimes with a stale "(37 years)" after
 * it). The age is worked out on the device, so it is right on the player's birthday. A date in a
 * shape this does not know is shown as it came.
 */
internal fun bornLine(raw: String, today: LocalDate): String {
    val text = raw.substringBefore("(").trim()
    if (text.isEmpty()) return ""
    val date = DOB_PATTERNS.firstNotNullOfOrNull { pattern ->
        try {
            LocalDate.parse(text, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
        } catch (e: DateTimeParseException) {
            null
        }
    } ?: return text
    val age = Period.between(date, today).years
    return "${date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))} · $age"
}

/** The formats this player has figures for, in the usual order; all of them when none is known. */
internal fun formatsWithStats(profile: PlayerProfile, all: List<String>): List<String> =
    all.filter { profile.stats.containsKey(it) }.ifEmpty { all }

private val DOB_PATTERNS = listOf("MMMM dd, yyyy", "MMMM d, yyyy", "MMM d, yyyy", "d MMM yyyy", "yyyy-MM-dd")
