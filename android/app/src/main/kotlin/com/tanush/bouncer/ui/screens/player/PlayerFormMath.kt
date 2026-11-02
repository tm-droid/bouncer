package com.tanush.bouncer.ui.screens.player

import com.tanush.bouncer.models.FormatStats
import com.tanush.bouncer.models.PlayerProfile

/**
 * Batting form maths for the player detail screen.
 *
 * Extracted from a `private fun` inside `PlayerDetailScreenV2.kt`, where it could not be tested,
 * and corrected on three counts. See [formRating] and [careerRunsPerInnings].
 */

private const val NEUTRAL_RATING = 0.5f
private const val RISING_RATIO = 1.15
private const val DECLINING_RATIO = 0.85
private const val TREND_WINDOW = 3

/**
 * Career runs per innings, weighted by innings across formats.
 *
 * Two corrections in one figure:
 *  - **Dimension.** `FormatStats.average` is runs per *dismissal*, but the recent-form figure it was
 *    compared against is runs per *innings* (every entry in `recentForm` counts, dismissed or not).
 *    Dividing one by the other was dimensionally wrong and systematically flattered the player,
 *    since runs-per-dismissal is the larger number. Both sides are now runs per innings.
 *  - **Weighting.** The old code took an unweighted `.average()` over the per-format figures, so a
 *    two-innings T10 record counted as much as a ninety-innings Test record. Pooling runs and
 *    innings weights each format by how much cricket it actually represents.
 *
 * Null when no format reports any innings, i.e. there is nothing to compare against.
 */
internal fun careerRunsPerInnings(stats: Collection<FormatStats>): Double? {
    val counted = stats.filter { it.innings > 0 }
    if (counted.isEmpty()) return null
    val innings = counted.sumOf { it.innings }
    return counted.sumOf { it.runs }.toDouble() / innings
}

/** Strike rate weighted by innings, for the same reason [careerRunsPerInnings] is. */
internal fun weightedStrikeRate(stats: Collection<FormatStats>): Double? {
    val counted = stats.filter { it.innings > 0 && it.strikeRate > 0 }
    if (counted.isEmpty()) return null
    val innings = counted.sumOf { it.innings }
    return counted.sumOf { it.strikeRate * it.innings } / innings
}

/**
 * Form as a 0–1 ring value: how the recent scoring rate compares to the career one.
 *
 * The old expression was `(recent / career).coerceIn(0f, 1f)`, which **saturates**: anyone scoring
 * at or above their career rate rendered an identical full ring, so a batter averaging 60 against a
 * career 40 looked exactly like one averaging 41. Every in-form player looked equally in form.
 *
 * `r / (1 + r)` is monotone over the whole domain and needs no clamp — parity is 0.5, twice the
 * career rate is 0.67, half of it is 0.33 — so the ring keeps discriminating above parity.
 *
 * With no career figure to compare against there is no evidence either way, so the rating is
 * neutral rather than a confident zero.
 */
internal fun formRating(recentRunsPerInnings: Double, careerRunsPerInnings: Double?): Float {
    val career = careerRunsPerInnings?.takeIf { it > 0.0 } ?: return NEUTRAL_RATING
    val ratio = (recentRunsPerInnings / career).coerceAtLeast(0.0)
    return (ratio / (1.0 + ratio)).toFloat()
}

/** Direction of travel within the recent innings themselves. */
internal fun formTrend(recentScores: List<Int>): String {
    if (recentScores.isEmpty()) return "Stable"
    val overall = recentScores.average()
    val recent = recentScores.take(TREND_WINDOW).average()
    return when {
        recent > overall * RISING_RATIO -> "Rising"
        recent < overall * DECLINING_RATIO -> "Declining"
        else -> "Stable"
    }
}

/** A player's recent batting, summed up against their career. */
internal data class BatterForm(
    val name: String,
    val recentScores: List<Int>,
    val average: Double,
    val strikeRate: Double,
    /** 0..1, recent runs per innings against the career's; 0.5 is level. */
    val formRating: Float,
    val trend: String,
)

/** Null when nothing was scraped into [PlayerProfile.recentForm], which is what hides the section. */
internal fun buildBatterForm(profile: PlayerProfile): BatterForm? {
    val recentScores = profile.recentForm.map { it.runs }
    if (recentScores.isEmpty()) return null
    val recentAverage = recentScores.average()
    val career = careerRunsPerInnings(profile.stats.values)
    return BatterForm(
        name = profile.name,
        recentScores = recentScores,
        average = recentAverage,
        strikeRate = weightedStrikeRate(profile.stats.values) ?: 0.0,
        formRating = formRating(recentAverage, career),
        trend = formTrend(recentScores),
    )
}
