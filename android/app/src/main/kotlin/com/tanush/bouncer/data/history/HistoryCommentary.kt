package com.tanush.bouncer.data.history

import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.models.TimelineEventType
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * A Cricsheet match's deliveries as commentary-feed lines ("Z Khan to KC Sangakkara, FOUR").
 *
 * Cricsheet has no written commentary, only what happened on each ball, so every line is built
 * from that and nothing else. Each line also carries the structured verdict the tab reads before
 * any text: `runs`, `extra`, `event` and `innings` are set from the delivery itself. A stored
 * Cricbuzz line has to have those parsed out of its prose.
 *
 * Newest first, the feed's order. `sortKey` is innings then delivery position, so it orders
 * correctly; it is not a timestamp and never meets one, since a feed is either this or the table.
 */
object HistoryCommentary {
    private const val INNINGS_STRIDE = 100_000.0

    fun entries(match: HistoryMatch): List<CommentaryEntry> {
        val lines = mutableListOf<CommentaryEntry>()
        match.inn.forEachIndexed { inningsIndex, innings ->
            if (innings.so != 0) return@forEachIndexed
            val howOut = innings.sc.bat.associate { row ->
                val person = (row.getOrNull(0) as? JsonPrimitive)?.intOrNull ?: -1
                person to (row.getOrNull(HOW_OUT) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
            }
            innings.b.forEachIndexed { position, row ->
                val d = HistoryDelivery.of(row) ?: return@forEachIndexed
                lines += CommentaryEntry(
                    text = textOf(match, d, howOut),
                    event = eventOf(d),
                    ball = "${d.over}.${d.ball}",
                    sortKey = inningsIndex * INNINGS_STRIDE + position,
                    runs = d.batRuns + d.extraRuns,
                    extra = extraOf(d),
                    innings = inningsIndex + 1,
                )
            }
        }
        return lines.asReversed()
    }

    internal fun textOf(match: HistoryMatch, d: HistoryDelivery, howOut: Map<Int, String?>): String {
        val wicket = d.wickets.firstOrNull()
        val outcome = when {
            wicket != null -> "OUT! " + (howOut[wicket.second] ?: wicket.first)
            else -> extrasText(d) ?: runsText(d.batRuns, d.isNonBoundary)
        }
        return "${match.name(d.bowler)} to ${match.name(d.batter)}, $outcome"
    }

    private fun extrasText(d: HistoryDelivery): String? = when {
        d.isWide -> if (d.extraRuns > 1) "wide, ${d.extraRuns} runs" else "wide"
        d.isNoBall -> if (d.batRuns > 0) "no ball, ${runsText(d.batRuns, d.isNonBoundary)}" else "no ball"
        d.isBye -> count(d.extraRuns, "bye")
        d.isLegBye -> count(d.extraRuns, "leg bye")
        else -> null
    }

    private fun count(n: Int, unit: String): String = if (n == 1) "1 $unit" else "$n ${unit}s"

    private fun runsText(runs: Int, nonBoundary: Boolean): String = when {
        runs == 0 -> "no run"
        runs == 4 && !nonBoundary -> "FOUR"
        runs == 6 -> "SIX"
        runs == 1 -> "1 run"
        else -> "$runs runs"
    }

    private fun eventOf(d: HistoryDelivery): TimelineEventType? = when {
        d.wickets.isNotEmpty() -> TimelineEventType.WICKET
        d.batRuns == 6 -> TimelineEventType.SIX
        d.batRuns == 4 && !d.isNonBoundary -> TimelineEventType.FOUR
        else -> null
    }

    private fun extraOf(d: HistoryDelivery): String? = when {
        d.isWide -> "WIDE"
        d.isNoBall -> "NOBALL"
        d.isBye -> "BYE"
        d.isLegBye -> "LEGBYE"
        else -> null
    }

    private const val HOW_OUT = 5
}
