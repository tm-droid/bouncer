package com.tanush.bouncer.liveactivity

import com.tanush.bouncer.analysis.CricketConstants
import com.tanush.bouncer.analysis.CricketConstants.MatchFormat

/**
 * The progress bar of a Live Update, computed as a pure function of the match state.
 *
 * `NotificationCompat.ProgressStyle` is the only rich style a promoted notification may use, so
 * this bar is the one piece of graphical state the Live Update gets. It has to say something true.
 *
 * Two models, because the formats genuinely differ:
 *
 * - A limited-overs innings has a fixed ball budget, so the bar is **balls bowled out of that
 *   budget**, with the powerplay / middle / death windows as coloured segments and a point at
 *   every fall of wicket. `CricketConstants` already owns those boundaries.
 * - A Test innings has no budget at all — `CricketConstants.inningsBalls` returns null for TEST
 *   precisely so callers cannot fall back to T20's 120. Inventing a denominator (a day's 90 overs,
 *   say) would put a figure on screen that the app does not have, so the bar becomes **wickets
 *   fallen out of ten**, which is a real quantity in every format.
 *
 * Only T20 and ODI have phase windows defined in `CricketConstants`; T10 and The Hundred get a
 * single neutral segment rather than a proportional guess at where their powerplay ends.
 */

/** Ten wickets ends an innings. The denominator of the multi-day progress model. */
private const val WICKETS_PER_INNINGS = 10

/** What the [LiveUpdateProgress.Determinate.progress] figure counts. */
enum class ProgressUnit { BALLS, WICKETS }

/** How a segment of the bar is tinted. Resolved to a colour by the notifier, not here. */
enum class SegmentTint { POWERPLAY, MIDDLE, DEATH, NEUTRAL }

/** One coloured stretch of the bar, [length] expressed in the same unit as the progress figure. */
data class LiveUpdateSegment(val length: Int, val tint: SegmentTint)

sealed interface LiveUpdateProgress {

    /**
     * Overs could not be read, so there is no honest position to draw. The platform renders an
     * animated "working" bar, which is the documented way to say "no figure yet" without showing
     * an empty state.
     */
    data object Indeterminate : LiveUpdateProgress

    data class Determinate(
        val progress: Int,
        val max: Int,
        val segments: List<LiveUpdateSegment>,
        val points: List<Int>,
        val unit: ProgressUnit,
    ) : LiveUpdateProgress
}

/**
 * The bar for one innings.
 *
 * @param overs over notation for the innings in progress ("56.4"), as the payload writes it.
 * @param wickets wickets fallen in the innings in progress, or null when unknown.
 * @param fallOfWicketOvers over notation for each wicket that has fallen this innings; used to
 *   place points on the limited-overs bar and ignored by the multi-day one.
 */
fun progressFor(
    format: MatchFormat,
    overs: String?,
    wickets: Int?,
    fallOfWicketOvers: List<String> = emptyList(),
): LiveUpdateProgress {
    val budget = CricketConstants.inningsBalls(format)
        ?: return multiDayProgress(wickets)
    val balls = CricketConstants.ballsFromOvers(overs) ?: return LiveUpdateProgress.Indeterminate
    return LiveUpdateProgress.Determinate(
        progress = balls.coerceIn(0, budget),
        max = budget,
        segments = limitedOversSegments(format, budget),
        points = fallOfWicketOvers
            .mapNotNull(CricketConstants::ballsFromOvers)
            .filter { it in 1..budget }
            .distinct()
            .sorted(),
        unit = ProgressUnit.BALLS,
    )
}

/**
 * Wickets out of ten. Unknown wickets is not zero wickets — a bar pinned at the start of the
 * innings for a side four down is a wrong figure, not a missing one — so it goes indeterminate.
 */
private fun multiDayProgress(wickets: Int?): LiveUpdateProgress {
    if (wickets == null) return LiveUpdateProgress.Indeterminate
    return LiveUpdateProgress.Determinate(
        progress = wickets.coerceIn(0, WICKETS_PER_INNINGS),
        max = WICKETS_PER_INNINGS,
        segments = listOf(LiveUpdateSegment(WICKETS_PER_INNINGS, SegmentTint.NEUTRAL)),
        points = emptyList(),
        unit = ProgressUnit.WICKETS,
    )
}

/**
 * Powerplay / middle / death for the two formats whose windows `CricketConstants` defines, and a
 * single neutral stretch for the ones it does not.
 */
private fun limitedOversSegments(format: MatchFormat, budget: Int): List<LiveUpdateSegment> {
    val deathStart = when (format) {
        MatchFormat.T20 -> CricketConstants.T20_DEATH_START_BALL
        MatchFormat.ODI -> CricketConstants.ODI_DEATH_START_BALL
        else -> return listOf(LiveUpdateSegment(budget, SegmentTint.NEUTRAL))
    }
    val powerplayEnd = CricketConstants.powerplayEndBall(format)
    return listOf(
        LiveUpdateSegment(powerplayEnd, SegmentTint.POWERPLAY),
        LiveUpdateSegment(deathStart - powerplayEnd, SegmentTint.MIDDLE),
        LiveUpdateSegment(budget - deathStart, SegmentTint.DEATH),
    ).filter { it.length > 0 }
}
