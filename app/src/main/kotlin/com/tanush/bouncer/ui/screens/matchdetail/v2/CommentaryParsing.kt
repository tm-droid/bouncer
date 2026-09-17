package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.tanush.bouncer.R
import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.models.TimelineEventType
import com.tanush.bouncer.utils.BallMetric
import com.tanush.bouncer.utils.TimelineEventExtractor
import com.tanush.bouncer.utils.ballMetricOfLine
import com.tanush.bouncer.utils.ballNumberOf
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
    /**
     * Total runs off the delivery, extras included, or null when nobody knows.
     *
     * Null is not zero, and the difference is the whole point: a null draws no result pill, where
     * zero draws a dot. See [com.tanush.bouncer.data.remote.CommentaryEntry.runs].
     */
    val runs: Int?,
    /**
     * The kind of delivery, where it is not an ordinary one — the server's verdict if it gave one.
     *
     * Null means no verdict, not "ordinary": a legacy row or a scrape fallback. [isWide] and
     * [isNoBall] fall back to the prose only there.
     */
    val extra: BallExtra?,
    val isWicket: Boolean,
    val isFour: Boolean,
    val isSix: Boolean,
    val isWide: Boolean,
    val isNoBall: Boolean,
    /** The innings this delivery belongs to; see [CommentaryEntry.innings]. */
    val innings: Int?,
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
    // The server's verdicts first. The fallbacks only reach raw Cricbuzz text — a passthrough row
    // or an on-device scrape — because neither the number nor the kind survives the AI rewrite.
    val outcome = parseBallOutcome(ballText)
    val extra = parseBallExtra(this.extra) ?: outcome.extra
    val runs = this.runs ?: runsFor(ballText, outcome, isSix = isSix, isFour = isFour, isWicket = isWicket)
    // Prose is consulted for the kind only when nothing classified the ball, and it is the last
    // resort for the same reason it is for the runs: a rewrite that says "strays down leg" instead
    // of "wide" is the common case, not the exception.
    val isWide = extra == BallExtra.WIDE || (extra == null && lower.contains("wide"))
    val isNoBall = extra == BallExtra.NOBALL ||
        (extra == null && (lower.contains("no ball") || lower.contains("no-ball")))

    return ParsedBall(
        ballLabel, overNumber, ballText, runs, extra, isWicket, isFour, isSix, isWide, isNoBall,
        innings,
    )
}

private fun runsFor(
    text: String,
    outcome: BallOutcome,
    isSix: Boolean,
    isFour: Boolean,
    isWicket: Boolean,
): Int? = when {
    isSix -> 6
    isFour -> 4
    isWicket -> 0
    else -> outcome.runs
        // The one outcome the rewrite tends to keep verbatim, and the only prose signal trusted
        // here: a line that says "no run" is a dot ball. Nothing infers a SCORE from prose — that
        // inference, defaulting to 0, is what put a dot on every single.
        ?: 0.takeIf { NO_RUN_IN_PROSE.containsMatchIn(text) }
}

/** "no run", but not "no run out" — a dropped run-out chance is not a verdict on the runs. */
private val NO_RUN_IN_PROSE = Regex("\\bno run\\b(?!\\s+out)", RegexOption.IGNORE_CASE)

/**
 * The commentary tab's filter chips.
 *
 * An enum rather than the chip's own label, which is what this compared before. The labels are
 * translated now, so a `selectedFilter == "Wickets"` test passes only in English — every filter
 * would silently return the whole feed in any other locale. Same trap the pager's `when` on tab
 * titles had, and the reason it dispatches on resource ids.
 */
internal enum class CommentaryFilter(val labelRes: Int) {
    ALL(R.string.commentary_filter_all),
    WICKETS(R.string.commentary_filter_wickets),
    BOUNDARIES(R.string.commentary_filter_boundaries),
    MILESTONES(R.string.commentary_filter_milestones),
}

internal fun filterCommentary(
    commentary: List<CommentaryEntry>,
    selectedFilter: CommentaryFilter,
): List<CommentaryEntry> {
    // The generic overload carries `event` through a pass that rewrites the text; the per-line
    // cleanup that used to run here first is exactly what deduplicateByBall's own normalize does.
    //
    // `ballOf` is the `ball` COLUMN first, and the prose only where there is no column. The default
    // accessor is prose-only: it regexes a `40.4` off the head of the line, which exists solely on
    // rows the AI chain rewrote. A raw Cricbuzz passthrough row (`ai_generated = false`, the common
    // case) has no prefix, so every delivery in an over looked like one unnumbered line to both
    // dedup passes and only the longest — the wicket or the boundary — survived.
    //
    // The prose fallback is not decoration: an entry from the on-device scrape fallback has no
    // column at all and carries its number in the text, which is the case the default was written
    // for.
    val cleaned = TimelineEventExtractor.deduplicateByBall(
        commentary,
        textOf = { it.text },
        withText = { entry, text -> entry.copy(text = text) },
        ballOf = { entry -> entry.ball?.takeIf { it.isNotBlank() } ?: ballNumberOf(entry.text) },
    )
    if (selectedFilter == CommentaryFilter.ALL) return cleaned
    return cleaned.filter { entry ->
        when (selectedFilter) {
            CommentaryFilter.WICKETS -> entry.eventType() == TimelineEventType.WICKET
            CommentaryFilter.BOUNDARIES -> entry.eventType() in BOUNDARY_EVENTS
            // Read from the text, never from `event`: match-sync deliberately does not classify
            // milestones (they are not reliably marked in Cricbuzz's commText), so consulting a
            // verdict that is FOUR or WICKET here could only ever hide a fifty, never find one.
            // The words themselves survive the rewrite — a century is a fact, not a phrasing.
            CommentaryFilter.MILESTONES ->
                TimelineEventExtractor.detectBallType(entry.text) == TimelineEventType.MILESTONE
            CommentaryFilter.ALL -> true
        }
    }
}

/**
 * One rendered run of the feed: the deliveries of a single over, or a single non-delivery line.
 *
 * [overNumber] is null for a non-delivery (session notes, weather) and for a ball-count format like
 * The Hundred, which has no over to group by.
 */
@Immutable
internal data class CommentaryBlock(
    val innings: Int?,
    val overNumber: Int?,
    val balls: List<ParsedBall>,
    /** True when this block opens an innings different from the block above it. */
    val startsInnings: Boolean,
)

/**
 * Group the feed into blocks **without reordering it**.
 *
 * The feed arrives ordered by `sort_key`, i.e. by wall clock, and that order is correct. What is
 * not correct is treating it as cricket order: a Test's third-innings over 60 is genuinely newer
 * than its second-innings over 80. The tab used to reconcile the two by regrouping everything with
 * `groupBy { over }.toSortedMap(reverseOrder())`, which throws chronology away entirely — so a feed
 * holding overs 89 and 83 of one innings and 60 of the next rendered 89, 83, … 60 as though it were
 * one continuous innings running backwards, and the flat Wickets list showed a dismissal at 60.2
 * above one at 80.5 with nothing to explain why.
 *
 * A sequential walk keeps the order the server gave and states the innings change instead of hiding
 * it. Consecutive deliveries sharing an innings and an over form one block; anything else opens a
 * new one.
 */
internal fun buildCommentaryBlocks(balls: List<ParsedBall>): List<CommentaryBlock> {
    val blocks = mutableListOf<CommentaryBlock>()
    var current = mutableListOf<ParsedBall>()

    fun flush() {
        if (current.isEmpty()) return
        val head = current.first()
        val previous = blocks.lastOrNull()
        val over = head.overNumber.takeIf { it > 0f }?.toInt()
        blocks += CommentaryBlock(
            innings = head.innings,
            overNumber = over,
            balls = current.toList(),
            startsInnings = previous != null && opensNewInnings(previous, head.innings, over),
        )
        current = mutableListOf()
    }

    for (ball in balls) {
        if (current.isNotEmpty() && !sameBlock(current.first(), ball)) flush()
        current += ball
    }
    flush()
    return blocks
}

/** Two consecutive lines belong together only if both are deliveries of the same over and innings. */
internal fun sameBlock(a: ParsedBall, b: ParsedBall): Boolean {
    if (a.ballLabel == null || b.ballLabel == null) return false
    if (a.innings != b.innings) return false
    if (a.overNumber <= 0f || b.overNumber <= 0f) return false
    return a.overNumber.toInt() == b.overNumber.toInt()
}

/**
 * Whether a block opens a new innings, given the block above it.
 *
 * The `innings` column settles it whenever both rows carry one. Historical rows do not — the column
 * was written from the innings being batted at WRITE time, so an innings' tail can be stamped with
 * the next innings' number — and a scrape-fallback line has no column at all. The fallback is the
 * shape of the walk itself: the feed runs newest to oldest, so within one innings the over number
 * can only fall. An over number that RISES as we go back in time is an innings boundary.
 */
private fun opensNewInnings(previous: CommentaryBlock, innings: Int?, over: Int?): Boolean {
    if (previous.innings != null && innings != null) return previous.innings != innings
    val above = previous.overNumber ?: return false
    return over != null && over > above
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
