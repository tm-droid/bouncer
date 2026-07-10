package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.tanush.bouncer.models.TimelineEventType
import com.tanush.bouncer.utils.TimelineEventExtractor

// ── Commentary parsing helpers (pure; rendering lives in CommentaryTabV2.kt) ──

@Immutable
internal data class ParsedBall(
    val overNumber: Float,
    val ballText: String,
    val runs: Int,
    val isWicket: Boolean,
    val isFour: Boolean,
    val isSix: Boolean,
    val isWide: Boolean,
    val isNoBall: Boolean,
)

private val OVER_REGEX = Regex("^(\\d+\\.\\d+)\\s+(.*)", setOf(RegexOption.DOT_MATCHES_ALL))
private val RUNS_REGEX = Regex("\\b(\\d+) runs?\\b")

internal fun String.parseBall(): ParsedBall {
    val clean = replace(Regex("^\\[AI Fused\\]\\s*"), "").trimEnd('/').trim()
    val m = OVER_REGEX.find(clean)
    val overNumber = m?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
    val text = m?.groupValues?.get(2) ?: clean
    val lower = text.lowercase()

    val ballType = TimelineEventExtractor.detectBallType(clean)
    val isWicket = ballType == TimelineEventType.WICKET
    val isSix = ballType == TimelineEventType.SIX
    val isFour = ballType == TimelineEventType.FOUR
    val isWide = lower.contains("wide")
    val isNoBall = lower.contains("no ball") || lower.contains("no-ball")

    val runs = runsFor(lower, isSix = isSix, isFour = isFour, isWicket = isWicket)

    return ParsedBall(overNumber, text, runs, isWicket, isFour, isSix, isWide, isNoBall)
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

internal fun filterCommentary(commentary: List<String>, selectedFilter: String): List<String> {
    val cleaned = TimelineEventExtractor.deduplicateByBall(
        commentary
            .map { it.replace(Regex("^\\[AI Fused\\]\\s*"), "").trimEnd('/').trim() }
            .filter { it.isNotBlank() }
    )
    if (selectedFilter == "All") return cleaned
    return cleaned.filter { line ->
        when (selectedFilter) {
            "Wickets" -> TimelineEventExtractor.detectBallType(line) == TimelineEventType.WICKET
            "Boundaries" -> TimelineEventExtractor.detectBallType(line).let {
                it == TimelineEventType.SIX || it == TimelineEventType.FOUR
            }
            "Milestones" -> TimelineEventExtractor.detectBallType(line) == TimelineEventType.MILESTONE
            else -> true
        }
    }
}

internal fun buildShareText(ball: ParsedBall): String =
    if (ball.overNumber > 0f) "Over ${"%.1f".format(ball.overNumber)}: ${ball.ballText}"
    else ball.ballText

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
