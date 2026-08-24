package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.models.TimelineEventType
import com.tanush.bouncer.utils.BallMetric
import com.tanush.bouncer.utils.TimelineEventExtractor
import com.tanush.bouncer.utils.ballMetricOfLine
import java.util.Locale

// ── Commentary parsing helpers (pure; rendering lives in CommentaryTabV2.kt) ──

@Immutable
internal data class ParsedBall(
    /**
     * What the pill shows: `"18.5"`, or The Hundred's bare `"72"`. Null means this line is not a
     * delivery at all.
     *
     * Separate from [overNumber] because The Hundred has no overs — it counts 1..100 — so there is
     * no over-shaped number to put here, and inventing one is what
     * [com.tanush.bouncer.utils.BallMetric] exists to prevent.
     */
    val ballLabel: String?,
    val overNumber: Float,
    val ballText: String,
    val runs: Int,
    val isWicket: Boolean,
    val isFour: Boolean,
    val isSix: Boolean,
    val isWide: Boolean,
    val isNoBall: Boolean,
)

/**
 * The stored ball label, in both shapes `match-sync` writes: `"40.4 …"` and `"40.4: …"`.
 *
 * The colon is the form that actually reaches the device — the prompt asks the model to "begin
 * with the ball number followed by a colon" — and an earlier `^(\d+\.\d+)\s+` required whitespace
 * there, so every server line parsed to `overNumber = 0f`. That is what suppressed the 4/6/W
 * ResultPill (gated on `overNumber > 0f`) and pushed every delivery into the un-grouped NonBallLine
 * bucket. Digit shape aligned with BallNotation.BALL_PREFIX, the canonical one.
 */
private val OVER_REGEX = Regex("^(\\d{1,3}\\.\\d)\\s*:?\\s+(.*)", setOf(RegexOption.DOT_MATCHES_ALL))
private val RUNS_REGEX = Regex("\\b(\\d+) runs?\\b")
private val AI_FUSED_PREFIX = Regex("^\\[AI Fused\\]\\s*")
private val BOUNDARY_EVENTS = setOf(TimelineEventType.SIX, TimelineEventType.FOUR)

/**
 * What happened on this delivery: the server's verdict if it gave one, else read from the text.
 *
 * The order is the whole fix. `event` is classified by `bouncer-match-sync` from the RAW Cricbuzz
 * text before the line is rewritten into broadcast prose, so it survives a rewrite that turns
 * "FOUR" into "races away to the rope". [TimelineEventExtractor.detectBallType] stays as the
 * fallback for the two cases with no server verdict — an on-device scraped line, which still holds
 * Cricbuzz's own uppercase tokens, and a row written before the `event` column existed.
 *
 * An ordinary delivery arrives as [TimelineEventType.OTHER], not null, and so does NOT fall
 * through to the text. That is deliberate: the fallback exists for lines nobody classified, not
 * for lines classified as nothing, and running it on a dot ball's AI prose is what would let
 * "India need six from four" back into the Boundaries tab.
 */
internal fun CommentaryEntry.eventType(): TimelineEventType? =
    event ?: TimelineEventExtractor.detectBallType(text)

/**
 * The delivery's label and over, from the row's `ball` column ahead of anything in the prose.
 *
 * The prefix is present only on lines the AI chain rewrote: `match-sync` writes raw Cricbuzz text
 * with no prefix whenever that chain produces nothing, and in production that is most lines. The
 * column is also the only thing that can carry The Hundred, which has no over to write.
 *
 * @param ball the row's `ball` column, or null.
 * @param prosePrefix the `40.4` captured from the head of the text, or null.
 */
private fun resolveBallLabel(ball: String?, prosePrefix: String?): Pair<String?, Float> {
    val metric = ball?.let { ballMetricOfLine(it) }
    val overNumber = when (metric) {
        is BallMetric.OverBall -> metric.over + metric.ball / 10f
        else -> prosePrefix?.toFloatOrNull() ?: 0f
    }
    val label = when {
        metric is BallMetric.BallCount -> metric.ball.toString()
        overNumber > 0f -> String.format(Locale.US, "%.1f", overNumber)
        else -> null
    }
    return label to overNumber
}

internal fun CommentaryEntry.parseBall(): ParsedBall {
    val clean = text.replace(AI_FUSED_PREFIX, "").trimEnd('/').trim()
    val m = OVER_REGEX.find(clean)
    val (ballLabel, overNumber) = resolveBallLabel(ball, m?.groupValues?.get(1))
    val ballText = m?.groupValues?.get(2) ?: clean
    val lower = ballText.lowercase()

    val ballType = eventType()
    val isWicket = ballType == TimelineEventType.WICKET
    val isSix = ballType == TimelineEventType.SIX
    val isFour = ballType == TimelineEventType.FOUR
    val isWide = lower.contains("wide")
    val isNoBall = lower.contains("no ball") || lower.contains("no-ball")

    val runs = runsFor(lower, isSix = isSix, isFour = isFour, isWicket = isWicket)

    return ParsedBall(ballLabel, overNumber, ballText, runs, isWicket, isFour, isSix, isWide, isNoBall)
}

private fun runsFor(lower: String, isSix: Boolean, isFour: Boolean, isWicket: Boolean): Int = when {
    isSix -> 6
    isFour -> 4
    isWicket -> 0
    lower.contains("no run") -> 0
    else -> RUNS_REGEX.find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 0
}

internal fun ParsedBall.toBallString(): String = when {
    isWicket -> "W"
    isSix -> "6"
    isFour -> "4"
    isWide -> "WD"
    isNoBall -> "NB"
    runs == 0 -> "."
    else -> runs.toString()
}

internal fun filterCommentary(
    commentary: List<CommentaryEntry>,
    selectedFilter: String,
): List<CommentaryEntry> {
    // The generic overload carries `event` through a pass that rewrites the text; the per-line
    // cleanup that used to run here first is exactly what deduplicateByBall's own normalize does.
    val cleaned = TimelineEventExtractor.deduplicateByBall(
        commentary,
        textOf = { it.text },
        withText = { entry, text -> entry.copy(text = text) },
    )
    if (selectedFilter == "All") return cleaned
    return cleaned.filter { entry ->
        when (selectedFilter) {
            "Wickets" -> entry.eventType() == TimelineEventType.WICKET
            "Boundaries" -> entry.eventType() in BOUNDARY_EVENTS
            // Read from the text, never from `event`: match-sync deliberately does not classify
            // milestones (they are not reliably marked in Cricbuzz's commText), so consulting a
            // verdict that is FOUR or WICKET here could only ever hide a fifty, never find one.
            // The words themselves survive the rewrite — a century is a fact, not a phrasing.
            "Milestones" ->
                TimelineEventExtractor.detectBallType(entry.text) == TimelineEventType.MILESTONE
            else -> true
        }
    }
}

internal fun buildShareText(ball: ParsedBall): String = when {
    // "Over" is only meaningful for over-based formats; The Hundred counts deliveries.
    ball.overNumber > 0f -> "Over ${ball.ballLabel}: ${ball.ballText}"
    ball.ballLabel != null -> "Ball ${ball.ballLabel}: ${ball.ballText}"
    else -> ball.ballText
}

internal fun parseBoldTags(text: String): AnnotatedString {
    return buildAnnotatedString {
        val regex = Regex("<b>(.*?)</b>")
        var lastIndex = 0
        regex.findAll(text).forEach { match ->
            append(text.substring(lastIndex, match.range.first))
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < text.length) append(text.substring(lastIndex))
    }
}
