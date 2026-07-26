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
    const val HUNDRED_INNINGS_BALLS = 100
    const val T10_INNINGS_BALLS = 60
    // Test has no fixed innings length. There is deliberately no constant for it — inningsBalls()
    // returns null, which forces callers to handle "no budget" instead of reading a 0 sentinel as
    // a real ball count.

    // --- Par scoring rates (runs per over sustained across a full innings, all wickets intact) ---
    // Approximate contemporary averages. These calibrate the win-probability heuristic: a chase
    // requiring exactly the par rate with ten wickets in hand sits at even money.
    const val T20_PAR_RUN_RATE = 8.3
    const val ODI_PAR_RUN_RATE = 5.6
    const val HUNDRED_PAR_RUN_RATE = 8.6
    const val T10_PAR_RUN_RATE = 10.5

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

    // --- Phase momentum thresholds, as multiples of the format's par run rate ---
    // These reproduce the T20 run totals that were previously hardcoded in OverviewMatchPhases
    // (60/30 powerplay, 80/40 middle, 70/30 death) while scaling correctly to other formats.
    // They differ per phase because scoring is not uniform across an innings: a powerplay is
    // expected to beat par, the middle overs to sit near it, and the death overs to far exceed it.
    const val PHASE_POWERPLAY_STRONG_FACTOR = 1.20
    const val PHASE_POWERPLAY_WEAK_FACTOR = 0.60
    const val PHASE_MIDDLE_STRONG_FACTOR = 1.05
    const val PHASE_MIDDLE_WEAK_FACTOR = 0.55
    const val PHASE_DEATH_STRONG_FACTOR = 1.70
    const val PHASE_DEATH_WEAK_FACTOR = 0.72

    // --- Commentary / timeline defaults ---
    const val DEDUP_MIN_LENGTH = 15
    const val DEDUP_PREFIX_RATIO = 0.4

    // --- MatchFormat enum ---
    enum class MatchFormat { T20, T10, HUNDRED, ODI, TEST, UNKNOWN }

    /**
     * Balls in one innings for [format], or null when the format has no fixed budget (Test) or
     * is unrecognised.
     *
     * Null is the whole point: callers that need a ball budget must handle "I don't know" rather
     * than fall back to T20's 120. That fallback is what made a Test past ball 120 compute
     * `ballsRemaining = 1` and carry a permanent late-innings pressure penalty.
     */
    fun inningsBalls(format: MatchFormat): Int? = when (format) {
        MatchFormat.T20 -> T20_INNINGS_BALLS
        MatchFormat.T10 -> T10_INNINGS_BALLS
        MatchFormat.HUNDRED -> HUNDRED_INNINGS_BALLS
        MatchFormat.ODI -> ODI_INNINGS_BALLS
        MatchFormat.TEST, MatchFormat.UNKNOWN -> null
    }

    /** Par scoring rate for [format], or null when the format has no meaningful par rate. */
    fun parRunRate(format: MatchFormat): Double? = when (format) {
        MatchFormat.T20 -> T20_PAR_RUN_RATE
        MatchFormat.T10 -> T10_PAR_RUN_RATE
        MatchFormat.HUNDRED -> HUNDRED_PAR_RUN_RATE
        MatchFormat.ODI -> ODI_PAR_RUN_RATE
        MatchFormat.TEST, MatchFormat.UNKNOWN -> null
    }

    // Order matters: the longest / most specific tokens are tested first, so "T20I" does not
    // fall through to a broader rule and "The Hundred" is not read as a century.
    private val FORMAT_PATTERNS: List<Pair<Regex, MatchFormat>> = listOf(
        """\b(test|first[- ]class|4[- ]day|5[- ]day)\b""" to MatchFormat.TEST,
        """\b(the\s+hundred|hundred|100[- ]ball)\b""" to MatchFormat.HUNDRED,
        """\bt10\b""" to MatchFormat.T10,
        """\b(t20|twenty20|t20i)\b""" to MatchFormat.T20,
        """\b(odi|one[- ]day|50[- ]over|list\s*a)\b""" to MatchFormat.ODI,
    ).map { (pattern, format) -> pattern.toRegex(RegexOption.IGNORE_CASE) to format }

    /**
     * Derive match format from the match type / title text.
     *
     * Returns UNKNOWN rather than guessing. The previous inline version of this
     * (`contains("ODI") -> 300; contains("T20") -> 120; else -> 120`) silently treated Tests,
     * The Hundred, and rain-shortened games as T20.
     */
    fun formatFromMatchType(vararg text: String?): MatchFormat {
        val haystack = text.filterNotNull().joinToString(" ")
        if (haystack.isBlank()) return MatchFormat.UNKNOWN
        return FORMAT_PATTERNS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(haystack) }
            ?.second
            ?: MatchFormat.UNKNOWN
    }

    /**
     * Derive match format from total balls in an innings.
     * Returns UNKNOWN for 0 or unrecognized values (e.g. Test matches).
     */
    fun formatFromInningsBalls(totalBalls: Int): MatchFormat = when (totalBalls) {
        T20_INNINGS_BALLS -> MatchFormat.T20
        T10_INNINGS_BALLS -> MatchFormat.T10
        HUNDRED_INNINGS_BALLS -> MatchFormat.HUNDRED
        ODI_INNINGS_BALLS -> MatchFormat.ODI
        else -> MatchFormat.UNKNOWN
    }

    // Matches "19.5", "19.5 Ov", "(19.5)", "19". The fractional digit is BALLS, not a decimal —
    // 19.5 is 119 balls, not 19.83 overs.
    private val OVERS_NOTATION = """(\d+)(?:\.(\d))?""".toRegex()

    /**
     * Parse cricket over notation into a ball count, or null when [overs] carries no valid figure.
     *
     * Replaces `overs.split(".")` with `toIntOrNull() ?: 0`, which silently produced a wrong
     * ball count for any decorated string: "19.5 Ov" parsed its ball digit as 0, yielding 114
     * balls instead of 119.
     */
    fun ballsFromOvers(overs: String?): Int? {
        if (overs.isNullOrBlank()) return null
        val match = OVERS_NOTATION.find(overs) ?: return null
        val completed = match.groupValues[1].toIntOrNull() ?: return null
        val balls = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        // A ball digit of 6+ is not valid over notation — reject rather than silently accept.
        if (balls >= BALLS_PER_OVER) return null
        return completed * BALLS_PER_OVER + balls
    }

    /**
     * Return the powerplay end ball (exclusive) for the given format.
     *
     * Formats without a defined powerplay (Test, unknown) fall back to the T20 window. That is a
     * presentation fallback only — callers deriving a ball *budget* must use [inningsBalls], which
     * returns null instead of guessing.
     */
    fun powerplayEndBall(format: MatchFormat): Int = when (format) {
        MatchFormat.ODI -> ODI_POWERPLAY_END_BALL
        MatchFormat.T20, MatchFormat.T10, MatchFormat.HUNDRED,
        MatchFormat.TEST, MatchFormat.UNKNOWN -> T20_POWERPLAY_END_BALL
    }

    /**
     * Return the death-overs start ball (inclusive) for the given format.
     *
     * Same fallback caveat as [powerplayEndBall].
     */
    fun deathStartBall(format: MatchFormat): Int = when (format) {
        MatchFormat.ODI -> ODI_DEATH_START_BALL
        MatchFormat.T20, MatchFormat.T10, MatchFormat.HUNDRED,
        MatchFormat.TEST, MatchFormat.UNKNOWN -> T20_DEATH_START_BALL
    }

    /** Last over of the powerplay for [format] — 6 for T20, 10 for ODI. */
    fun powerplayEndOver(format: MatchFormat): Int = powerplayEndBall(format) / BALLS_PER_OVER

    /** First over of the death phase for [format] — 16 for T20, 40 for ODI. */
    fun deathStartOver(format: MatchFormat): Int = deathStartBall(format) / BALLS_PER_OVER
}
