package com.tanush.bouncer.utils

import io.sentry.Sentry

/**
 * Types of commentary events
 */
enum class CommentaryEventType {
    REGULAR,
    DOT_BALL,
    SINGLE,
    DOUBLE,
    THREE,
    FOUR,
    SIX,
    WICKET,
    WIDE,
    NO_BALL,
    LEG_BYE,
    OVER_END,
    INNINGS_END,
    MILESTONE,
    REVIEW
}

/**
 * Parse commentary text and optional run count to determine event type
 */
fun parseCommentaryType(text: String, runs: Int?): CommentaryEventType {
    try {
        val lowerText = text.lowercase()

        return when {
            lowerText.contains("wicket") || lowerText.contains("out") ||
            lowerText.contains("caught") || lowerText.contains("bowled") ||
            lowerText.contains("lbw") || lowerText.contains("run out") ||
            lowerText.contains("stumped") -> CommentaryEventType.WICKET

            lowerText.contains("six") || runs == 6 -> CommentaryEventType.SIX
            lowerText.contains("four") || runs == 4 -> CommentaryEventType.FOUR

            lowerText.contains("century") || lowerText.contains("hundred") ||
            lowerText.contains("fifty") || lowerText.contains("milestone") -> CommentaryEventType.MILESTONE

            lowerText.contains("wide") -> CommentaryEventType.WIDE
            lowerText.contains("no ball") || lowerText.contains("no-ball") -> CommentaryEventType.NO_BALL
            lowerText.contains("leg bye") -> CommentaryEventType.LEG_BYE

            lowerText.contains("review") || lowerText.contains("drs") -> CommentaryEventType.REVIEW
            lowerText.contains("end of over") -> CommentaryEventType.OVER_END
            lowerText.contains("innings") && (lowerText.contains("end") || lowerText.contains("all out")) -> CommentaryEventType.INNINGS_END

            runs == 0 || lowerText.contains("dot ball") -> CommentaryEventType.DOT_BALL
            runs == 1 -> CommentaryEventType.SINGLE
            runs == 2 -> CommentaryEventType.DOUBLE
            runs == 3 -> CommentaryEventType.THREE

            else -> CommentaryEventType.REGULAR
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}
