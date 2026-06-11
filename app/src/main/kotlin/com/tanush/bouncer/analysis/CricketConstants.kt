package com.tanush.bouncer.analysis

/**
 * Cricket format and algorithmic constants. Single source of truth for all match-calculation
 * magic numbers — never scatter these inline. Mirrors the pattern of data/CacheConfig.
 */
object CricketConstants {

    // --- Ball / over math ---
    const val BALLS_PER_OVER = 6

    // --- Format innings lengths (total balls in one innings) ---
    const val T20_INNINGS_BALLS = 120
    const val ODI_INNINGS_BALLS = 300
    // Test: no fixed innings length — use 0 as sentinel
    const val TEST_INNINGS_BALLS = 0

    // --- Powerplay lengths (in overs) ---
    const val T20_POWERPLAY_OVERS = 6
    const val ODI_POWERPLAY_OVERS = 10

    // --- Death-over windows (number of final overs classified as "death") ---
    const val T20_DEATH_OVERS = 4
    const val ODI_DEATH_OVERS = 10

    // --- Phase ball boundaries derived from above ---
    // T20 powerplay ends at ball 36 (6 overs × 6 balls)
    const val T20_POWERPLAY_END_BALL = T20_POWERPLAY_OVERS * BALLS_PER_OVER   // 36
    // T20 death starts at ball 97 (120 - 4 overs × 6 = 120 - 24 = 96, so ball > 96)
    const val T20_DEATH_START_BALL = T20_INNINGS_BALLS - (T20_DEATH_OVERS * BALLS_PER_OVER)  // 96
    // ODI powerplay ends at ball 60
    const val ODI_POWERPLAY_END_BALL = ODI_POWERPLAY_OVERS * BALLS_PER_OVER   // 60
    // ODI death starts at ball 240 (300 - 10 × 6 = 240)
    const val ODI_DEATH_START_BALL = ODI_INNINGS_BALLS - (ODI_DEATH_OVERS * BALLS_PER_OVER)  // 240

    // --- Win probability / key moment thresholds ---
    // A probability swing of this many percentage points is considered a "key moment"
    const val KEY_MOMENT_IMPACT_THRESHOLD = 5.0

    // --- Match intensity thresholds (used by MatchIntensityIndicator) ---
    // Run-rate thresholds
    const val INTENSITY_EXPLOSIVE_RRR = 12.0
    const val INTENSITY_INTENSE_RRR = 8.0
    const val INTENSITY_BUILDING_CRR = 6.0
    // Overs-remaining thresholds
    const val INTENSITY_EXPLOSIVE_OVERS = 5
    const val INTENSITY_INTENSE_OVERS = 15
    // Run-differential thresholds
    const val INTENSITY_EXPLOSIVE_RUN_DIFF = 30
    const val INTENSITY_INTENSE_RUN_DIFF = 50
    // Wicket-count thresholds
    const val INTENSITY_EXPLOSIVE_WICKETS = 3
    const val INTENSITY_INTENSE_WICKETS = 2
    const val INTENSITY_BUILDING_WICKETS = 1

    // --- Head-to-head / matchup minimum sample ---
    const val MATCHUP_MIN_BALLS = 6

    // --- Partnership highlight threshold ---
    const val PARTNERSHIP_HIGHLIGHT_RUNS = 50

    // --- Commentary / timeline defaults ---
    const val COMMENTARY_MAX_DESCRIPTION_CHARS = 120
    const val DEDUP_MIN_LENGTH = 15
    const val DEDUP_PREFIX_RATIO = 0.4

    // --- MatchFormat enum ---
    enum class MatchFormat { T20, ODI, TEST, UNKNOWN }

    /**
     * Derive match format from total balls in an innings.
     * Returns UNKNOWN for 0 or unrecognized values (e.g. Test matches).
     */
    fun formatFromInningsBalls(totalBalls: Int): MatchFormat = when (totalBalls) {
        T20_INNINGS_BALLS -> MatchFormat.T20
        ODI_INNINGS_BALLS -> MatchFormat.ODI
        else -> MatchFormat.UNKNOWN
    }

    /**
     * Return the powerplay end ball (exclusive) for the given format.
     */
    fun powerplayEndBall(format: MatchFormat): Int = when (format) {
        MatchFormat.T20 -> T20_POWERPLAY_END_BALL
        MatchFormat.ODI -> ODI_POWERPLAY_END_BALL
        else -> T20_POWERPLAY_END_BALL   // safe default
    }

    /**
     * Return the death-overs start ball (inclusive) for the given format.
     */
    fun deathStartBall(format: MatchFormat): Int = when (format) {
        MatchFormat.T20 -> T20_DEATH_START_BALL
        MatchFormat.ODI -> ODI_DEATH_START_BALL
        else -> T20_DEATH_START_BALL   // safe default
    }
}
