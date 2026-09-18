package com.tanush.bouncer.models

/**
 * The single source of truth for deriving a match's coarse state from its status prose.
 *
 * Cricbuzz and Cricinfo report state only as free English text, so classification is unavoidably
 * string-based. What is avoidable is the failure mode this replaces: an ordered `contains` chain
 * where the *first* match wins, so an early in-progress marker beat a later terminal one.
 * That produced, among others:
 *
 *  - "Match drawn, England 2nd innings 340/6"     -> Live   (matched "innings" before "drawn")
 *  - "India needed 12 more runs"                  -> Live   (matched "need")
 *  - "India won the toss and elected to bat"      -> Completed (matched bare "won")
 *
 * Two rules fix that class of bug:
 *
 *  1. **Precedence, not position.** Every rule is evaluated and the highest-precedence signal
 *     wins, wherever it appears in the string. A terminal marker anywhere means the match is
 *     over, regardless of what in-progress prose surrounds it.
 *  2. **Word boundaries.** Every pattern is anchored, so "tea" cannot fire inside "team",
 *     "rain" cannot fire inside "training", and "draw" cannot fire inside "drawing". This
 *     removes the substring class of false positive outright and retires the ad-hoc
 *     `&& !s.contains("team")` style guards that were chasing individual instances of it.
 *
 * Adding a newly-observed phrasing is a data change to one of the three lists below — which is
 * the point, because these phrasings drift whenever the upstream sites reword their output.
 */
enum class StatusSignal {
    /** The match is over. Outranks everything — a result string often quotes in-progress prose. */
    TERMINAL,

    /** The match is underway, including breaks, delays, and the toss. */
    IN_PROGRESS,

    /** The match has not started. */
    PRE_MATCH,
}

object MatchStatusClassifier {

    // Terminal markers. Deliberately NOT bare "won": "won the toss" is the start of a match, not
    // the end of one. Only the result-bearing forms of "won" count.
    //
    // `internal` rather than private so `MatchStatusParityTest` can compare this list pattern for
    // pattern against `TERMINAL_STATUS_PATTERNS` in match-sync's cricbuzz.js, which answers the
    // same question server-side. A count or a fixture set would let one side gain a rule silently.
    internal val TERMINAL = listOf(
        """\bwon\s+by\b""",
        """\bwon\s+the\s+(match|series|game)\b""",
        """\bbeat\b""",
        """\bdrawn\b""",
        """\bdraw\b""",
        """\btied\b""",
        """\bno\s+result\b""",
        """\babandon(ed)?\b""",
        """\bcomplete[d]?\b""",
        """\bwins\b""",
        """\bwin\s+by\b""",
        """\bconcede[ds]?\b""",
        """\bforfeit(ed)?\b""",
        """\belected\s+not\s+to\s+bat\b""",
    ).map { it.toRegex(RegexOption.IGNORE_CASE) }

    // In-progress markers. Breaks and delays live here on purpose: a rain delay is a live match
    // that is not currently being played, not a finished one.
    internal val IN_PROGRESS = listOf(
        """\blive\b""",
        """\bin\s+progress\b""",
        """\binn?in?gs\b""",
        """\bneed(s|ed|ing)?\b""",
        """\brequire(s|d)?\b""",
        """\bchas(e|ing)\b""",
        """\btarget\b""",
        """\bopted\s+to\b""",
        """\belected\s+to\b""",
        """\bchose\s+to\b""",
        """\btoss\b""",
        """\bbatting\b""",
        """\bbowling\b""",
        """\bstumps\b""",
        """\brain\b""",
        """\bbad\s+light\b""",
        """\blunch\b""",
        """\btea\b""",
        """\bdrinks\b""",
        """\btimeout\b""",
        """\bstrategic\b""",
        """\bbreak\b""",
        """\bsession\b""",
        """\bday\s+\d""",
        """\bovers?\s+remaining\b""",
        """\bplay\s+(resumed|suspended|stopped|in\s+progress)\b""",
    ).map { it.toRegex(RegexOption.IGNORE_CASE) }

    internal val PRE_MATCH = listOf(
        """\bpreview\b""",
        """\bupcoming\b""",
        """\bscheduled\b""",
        """\bstarts?\s+at\b""",
        """\bmatch\s+starts\b""",
        """\byet\s+to\s+(begin|start)\b""",
        """\bnot\s+started\b""",
        """\btbc\b""",
        """\btba\b""",
    ).map { it.toRegex(RegexOption.IGNORE_CASE) }

    /**
     * Returns the highest-precedence signal present in [status], or null when nothing matches.
     *
     * Null is a real answer, not a failure: callers decide what an unrecognised status means for
     * them. It must never be silently folded into a concrete state here — that is how unknown
     * phrasings become invisible.
     */
    fun classify(status: String?): StatusSignal? {
        if (status.isNullOrBlank()) return null
        return when {
            TERMINAL.any { it.containsMatchIn(status) } -> StatusSignal.TERMINAL
            IN_PROGRESS.any { it.containsMatchIn(status) } -> StatusSignal.IN_PROGRESS
            PRE_MATCH.any { it.containsMatchIn(status) } -> StatusSignal.PRE_MATCH
            else -> null
        }
    }
}
