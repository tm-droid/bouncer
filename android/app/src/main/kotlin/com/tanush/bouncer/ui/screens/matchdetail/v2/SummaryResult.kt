package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.BowlerStats
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.ui.components.v2.RowResult
import com.tanush.bouncer.ui.components.v2.rowResultFor

internal enum class ResultKind { Won, Tied, Drawn, NoResult, Abandoned }

/**
 * What the Summary tab's result block shows. [winner] and [margin] are set only for [ResultKind.Won];
 * [margin] is null when the status line carries no "by ...". There is no player of the match: no
 * field carries one.
 */
internal data class SummaryResult(
    val kind: ResultKind,
    val winner: String?,
    val margin: String?,
    val bestBatter: BatsmanStats?,
    val bestBowler: BowlerStats?,
)

private val MARGIN = Regex("""\bby\s+(.+?)\s*(\([^)]*DLS[^)]*\))?\s*$""", RegexOption.IGNORE_CASE)

/** Null while the match is not complete, or when the status line names neither a winner nor a no-winner result. */
internal fun summaryResultOf(match: CricketMatch, scorecard: Scorecard?): SummaryResult? {
    if (!match.isCompleted) return null
    val status = match.currentStatus.ifBlank { match.status }.trim()
    val winner = match.teams.firstOrNull { rowResultFor(match, it.name) == RowResult.Won }?.name
    val kind = resultKindOf(winner, status.lowercase()) ?: return null
    val performers = scorecard?.innings.orEmpty().map { topPerformers(it) }
    return SummaryResult(
        kind = kind,
        winner = winner,
        margin = if (kind == ResultKind.Won) marginOf(status) else null,
        bestBatter = performers.mapNotNull { it.first.firstOrNull() }
            .maxByOrNull { it.runs.toIntOrNull() ?: 0 },
        bestBowler = performers.mapNotNull { it.second.firstOrNull() }
            .maxByOrNull { it.wickets.toIntOrNull() ?: 0 },
    )
}

private fun resultKindOf(winner: String?, lower: String): ResultKind? = when {
    winner != null -> ResultKind.Won
    "abandoned" in lower -> ResultKind.Abandoned
    "no result" in lower -> ResultKind.NoResult
    "tied" in lower -> ResultKind.Tied
    "drawn" in lower || lower.startsWith("draw") -> ResultKind.Drawn
    else -> null
}

/** "India won by 6 wkts (DLS)" gives "6 wkts (DLS)". */
internal fun marginOf(status: String): String? {
    val m = MARGIN.find(status) ?: return null
    val amount = m.groupValues[1].trim()
    if (amount.isEmpty()) return null
    val dls = m.groupValues[2]
    return if (dls.isEmpty()) amount else "$amount $dls"
}
