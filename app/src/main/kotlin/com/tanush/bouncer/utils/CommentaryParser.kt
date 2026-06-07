package com.tanush.bouncer.utils

enum class CommentaryEventType {
    REGULAR, DOT_BALL, SINGLE, DOUBLE, THREE, FOUR, SIX,
    WICKET, WIDE, NO_BALL, LEG_BYE, OVER_END, INNINGS_END, MILESTONE, REVIEW
}

private data class EventRule(
    val type: CommentaryEventType,
    val predicate: (String, Int?) -> Boolean,
)

private fun String.containsAny(vararg words: String) = words.any { contains(it) }

private val EVENT_RULES: List<EventRule> = listOf(
    // Explicit "dot ball" keyword beats everything (e.g. "Dot ball, well bowled").
    EventRule(CommentaryEventType.DOT_BALL)    { t, _ -> t.contains("dot ball") },
    // INNINGS_END before WICKET: "all out" would otherwise match the WICKET "out" keyword.
    EventRule(CommentaryEventType.INNINGS_END) { t, _ ->
        t.contains("all out") || (t.contains("innings") && t.contains("end"))
    },
    EventRule(CommentaryEventType.WICKET)      { t, _ -> t.containsAny("wicket", "out", "caught", "bowled", "lbw", "run out", "stumped") },
    EventRule(CommentaryEventType.SIX)         { t, r -> t.contains("six") || r == 6 },
    EventRule(CommentaryEventType.FOUR)        { t, r -> t.contains("four") || r == 4 },
    EventRule(CommentaryEventType.MILESTONE)   { t, _ -> t.containsAny("century", "hundred", "fifty", "milestone") },
    EventRule(CommentaryEventType.WIDE)        { t, _ -> t.contains("wide") },
    EventRule(CommentaryEventType.NO_BALL)     { t, _ -> t.contains("no ball") || t.contains("no-ball") },
    EventRule(CommentaryEventType.LEG_BYE)     { t, _ -> t.contains("leg bye") },
    EventRule(CommentaryEventType.REVIEW)      { t, _ -> t.contains("review") || t.contains("drs") },
    EventRule(CommentaryEventType.OVER_END)    { t, _ -> t.contains("end of over") },
    // runs=0 fallback comes AFTER WICKET so wicket text with zero runs → WICKET, not DOT_BALL.
    EventRule(CommentaryEventType.DOT_BALL)    { _, r -> r == 0 },
    EventRule(CommentaryEventType.SINGLE)      { _, r -> r == 1 },
    EventRule(CommentaryEventType.DOUBLE)      { _, r -> r == 2 },
    EventRule(CommentaryEventType.THREE)       { _, r -> r == 3 },
)

fun parseCommentaryType(text: String, runs: Int?): CommentaryEventType {
    val lower = text.lowercase()
    return EVENT_RULES.firstOrNull { it.predicate(lower, runs) }?.type
        ?: CommentaryEventType.REGULAR
}
