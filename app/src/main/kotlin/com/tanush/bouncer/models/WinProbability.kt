package com.tanush.bouncer.models

import androidx.compose.runtime.Immutable

/**
 * A win probability together with **which side it belongs to**.
 *
 * The type exists because the previous API did not carry that. `calculateHeuristicProbability()`
 * was documented as "for Team 1" but computed from whichever team was batting, so whenever
 * `teams[1]` was at the crease the number rendered against team 1 was actually team 2's. Two
 * consumers disagreed about which it was: HeroMatchCard and MatchCardV2 rendered it as team 1's,
 * while PremiumAiStatisticsTab described it as the batting side's. Both cannot be right, and the
 * ambiguity was not resolvable from a bare Double.
 *
 * Making the side explicit removes the class of bug rather than fixing one instance of it: a
 * caller must now say which orientation it wants via [forTeam1] or read [teamIndex] itself.
 */
@Immutable
data class WinProbability(
    /** Index into [CricketMatch.teams] that [probability] refers to, or null when undetermined. */
    val teamIndex: Int?,
    /** Probability that the team at [teamIndex] wins, 0..100. */
    val probability: Double,
    val confidence: Confidence,
) {
    enum class Confidence {
        /** No usable signal — the UI should show no probability at all, not 50/50. */
        NONE,

        /** A real but weak read, e.g. a first innings with most of the match still to come. */
        LOW,

        /** A live chase with a target, wickets, and a known ball budget. */
        HIGH,
    }

    /** Oriented to `teams[0]`. Returns an even 50.0 when the side is undetermined. */
    val forTeam1: Double
        get() = when (teamIndex) {
            0 -> probability
            1 -> 100.0 - probability
            else -> EVEN
        }

    val isDetermined: Boolean get() = confidence != Confidence.NONE

    companion object {
        const val EVEN = 50.0

        /**
         * Used when the situation genuinely cannot be read — an unknown format, a Test match, or
         * missing score data. Deliberately distinct from "we computed 50%": [isDetermined] is
         * false, so callers can decline to render a bar rather than draw a misleading even split.
         */
        val UNDETERMINED = WinProbability(null, EVEN, Confidence.NONE)

        fun forTeam(teamIndex: Int, probability: Double, confidence: Confidence) =
            WinProbability(teamIndex, probability, confidence)
    }
}
