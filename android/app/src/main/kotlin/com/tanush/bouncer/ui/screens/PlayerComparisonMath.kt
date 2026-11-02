package com.tanush.bouncer.ui.screens

import com.tanush.bouncer.models.FormatStats
import java.util.Locale

internal data class ComparisonData(
    val label: String,
    val value1: String,
    val value2: String,
    /** 1 or 2 for the player ahead on this stat; null for a tie, which highlights neither. */
    val leader: Int?,
    /** Player 1's share of the bar, already flipped where lower is better: longer is always better. */
    val share1: Float,
)

// ── Build comparison data ───────────────────────────────────────────────────────

/** Two players level on a stat (both 0 when a format has no data) is not a win for player 1. */
private fun leaderOf(order: Int): Int? = when {
    order > 0 -> 1
    order < 0 -> 2
    else -> null
}

private fun higherIsBetter(label: String, v1: Number, v2: Number): ComparisonData {
    val d1 = v1.toDouble()
    val d2 = v2.toDouble()
    val str1 = formatStat(v1)
    val str2 = formatStat(v2)
    val share = if (d1 + d2 > 0) (d1 / (d1 + d2)).toFloat() else EVEN_SHARE
    return ComparisonData(label, str1, str2, leader = leaderOf(d1.compareTo(d2)), share1 = share)
}

private fun lowerIsBetter(label: String, v1: Number, v2: Number): ComparisonData {
    val d1 = v1.toDouble()
    val d2 = v2.toDouble()
    val str1 = formatStat(v1)
    val str2 = formatStat(v2)
    // lower is better, but 0 means no data so treat as worse
    val order = when {
        d1 == 0.0 && d2 > 0.0 -> -1
        d2 == 0.0 && d1 > 0.0 -> 1
        else -> d2.compareTo(d1)
    }
    return ComparisonData(label, str1, str2, leader = leaderOf(order), share1 = lowerIsBetterShare(d1, d2, order))
}

internal fun buildComparisonRows(s1: FormatStats, s2: FormatStats): List<ComparisonData> {
    return listOf(
        higherIsBetter("Matches", s1.matches, s2.matches),
        higherIsBetter("Innings", s1.innings, s2.innings),
        higherIsBetter("Runs", s1.runs, s2.runs),
        higherIsBetter("Average", s1.average, s2.average),
        higherIsBetter("Strike Rate", s1.strikeRate, s2.strikeRate),
        higherIsBetter("100s", s1.hundreds, s2.hundreds),
        higherIsBetter("50s", s1.fifties, s2.fifties),
        higherIsBetter("Wickets", s1.wickets, s2.wickets),
        lowerIsBetter("Bowl Avg", s1.bowlingAverage, s2.bowlingAverage),
        lowerIsBetter("Economy", s1.economy, s2.economy),
    )
}

private fun formatStat(value: Number): String {
    return when (value) {
        is Int -> value.toString()
        is Double -> if (value == 0.0) "0" else String.format(Locale.US, "%.1f", value)
        else -> value.toString()
    }
}

/** Player 1's bar share where lower wins: the inverse share, or all-or-nothing when one has no figure. */
private fun lowerIsBetterShare(d1: Double, d2: Double, order: Int): Float = when {
    d1 > 0.0 && d2 > 0.0 -> (d2 / (d1 + d2)).toFloat()
    order > 0 -> 1f
    order < 0 -> 0f
    else -> EVEN_SHARE
}

private const val EVEN_SHARE = 0.5f
