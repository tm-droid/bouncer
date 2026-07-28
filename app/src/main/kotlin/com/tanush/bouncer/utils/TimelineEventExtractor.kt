package com.tanush.bouncer.utils

import com.tanush.bouncer.models.TimelineEvent
import com.tanush.bouncer.models.TimelineEventType

object TimelineEventExtractor {

    private val endOfOverPattern = Regex("end of over|over \\d+ complete", RegexOption.IGNORE_CASE)

    /**
     * A wicket requires an explicit dismissal marker. Fielding verbs on their own never qualify.
     *
     * The previous version matched bare `\bBOWLED\b` and `\bCATCH\b`, so ordinary descriptive
     * commentary — "bowled a beauty, no run", "a good catch attempt, dropped" — produced a WICKET
     * event, and [com.tanush.bouncer.services.LiveCommentaryManager] fires a user-facing
     * "Wicket!" push notification off exactly that. It compensated with a list of phrases to
     * strip ("MID-WICKET", "OVER THE WICKET", ...), which can only ever chase false positives
     * that have already been observed.
     *
     * The discriminator that actually holds is announcement vs description: commentary announces
     * a dismissal emphatically ("BOWLED!", "OUT!") or in scorecard notation ("c Rahul b Bumrah"),
     * and describes non-events in plain prose. Requiring the announcement form makes the
     * exclusion list unnecessary — "MID-WICKET boundary" contains no `WICKET!`.
     */
    private val wicketPatterns = listOf(
        // Terms that are only ever dismissals, in any casing.
        Regex("""\blbw\b""", RegexOption.IGNORE_CASE),
        Regex("""\brun\s+out\b""", RegexOption.IGNORE_CASE),
        Regex("""\bstumped\b""", RegexOption.IGNORE_CASE),
        Regex("""\bhit\s+wicket\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcaught\s+(behind|and\s+bowled|by)\b""", RegexOption.IGNORE_CASE),
        // Scorecard dismissal notation: "c Rahul b Bumrah", "st Pant b Chahal".
        Regex("""\b(c|st)\s+\w+\s+b\s+\w+""", RegexOption.IGNORE_CASE),
        Regex(""",\s*OUT\s*,""", RegexOption.IGNORE_CASE),
        // Announcements — the trailing emphasis is what separates these from description.
        Regex("""\b(WICKET|OUT|BOWLED|CAUGHT|GONE|TIMBER)\s*!""", RegexOption.IGNORE_CASE),
        // Explicit predicate forms.
        Regex("""\bis\s+out\b""", RegexOption.IGNORE_CASE),
        Regex("""\bout\s+(caught|bowled|lbw|stumped)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bbowled\s+(him|her|them|'?em)\b""", RegexOption.IGNORE_CASE),
    )

    /**
     * Vetoes a wicket even when a dismissal marker is present: a dropped catch, or a dismissal
     * off a no-ball or free hit, is not a wicket.
     */
    private val wicketVetoPattern =
        Regex("""\b(dropped|drops\s+(it|him|her)|no\s*[- ]?ball|free\s*hit)\b""", RegexOption.IGNORE_CASE)

    private val fourPattern = Regex("\\bFOUR\\b", RegexOption.IGNORE_CASE)
    private val sixPattern = Regex("\\bSIX\\b", RegexOption.IGNORE_CASE)

    /**
     * Milestones are announced in words. The previous version also matched the bare numbers
     * 50/100/150/200/300 anywhere in the line, so an ordinary score like "150/3 in 20 overs"
     * emitted a MILESTONE event.
     */
    private val milestonePatterns = listOf(
        Regex("""\b(fifty|half.?century|century|hundred|double\s+century|triple\s+century)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(5.?wicket|five.?wicket|fifer|hat.?trick)\b""", RegexOption.IGNORE_CASE),
    )

    private val aiFusedPrefix = Regex("^\\[AI Fused\\]\\s*")

    private fun normalize(raw: String): String =
        raw.replace(aiFusedPrefix, "").trim().trimEnd('/')

    /** Deduplicate commentary by ball number — keeps the longest entry per delivery.
     *  CricBuzz sends progressive updates for the same ball; this keeps only the most complete.
     *  Internal so OverviewTabV2 and CommentaryTabV2 can reuse it. */
    internal fun deduplicateByBall(lines: List<String>): List<String> {
        val normalized = lines.map {
            normalize(it).replace(' ', ' ').replace(Regex("[\\s\\p{Zs}]+"), " ").trim()
        }.filter { it.isNotBlank() }
        // Pass 1: group by ball number, keep longest (most complete progressive update)
        val bestByBall = mutableMapOf<String, String>()
        for (line in normalized) {
            val ball = ballNumberOf(line)
            if (ball != null) {
                val existing = bestByBall[ball]
                if (existing == null || line.length > existing.length) bestByBall[ball] = line
            }
        }
        val seen = mutableSetOf<String>()
        val pass1 = normalized.mapNotNull { line ->
            val ball = ballNumberOf(line)
            if (ball != null) {
                if (ball !in seen) { seen.add(ball); bestByBall[ball] } else null
            } else {
                val key = line.lowercase()
                if (key !in seen) { seen.add(key); line } else null
            }
        }
        // Pass 2: prefix dedup — keep only the longer of near-duplicate pairs
        val toRemove = mutableSetOf<Int>()
        for (i in pass1.indices) {
            if (i in toRemove) continue
            val ai = pass1[i].lowercase().replace(Regex("^[\\d.\\s]+"), "").replace(Regex("^\\d+\\s+"), "")
            for (j in i + 1 until pass1.size) {
                if (j in toRemove) continue
                val bj = pass1[j].lowercase().replace(Regex("^[\\d.\\s]+"), "").replace(Regex("^\\d+\\s+"), "")
                val shorter = if (ai.length <= bj.length) ai else bj
                val longer = if (ai.length <= bj.length) bj else ai
                val prefixLen = (shorter.length * 0.4).toInt().coerceAtLeast(15)
                if (shorter.length >= 15 && longer.startsWith(shorter.take(prefixLen))) {
                    toRemove.add(if (ai.length <= bj.length) i else j)
                }
            }
        }
        return pass1.filterIndexed { idx, _ -> idx !in toRemove }
    }

    /** True when [line] announces a dismissal and nothing vetoes it. */
    fun isWicketLine(line: String): Boolean {
        val clean = normalize(line)
        if (wicketVetoPattern.containsMatchIn(clean)) return false
        return wicketPatterns.any { it.containsMatchIn(clean) }
    }

    fun detectBallType(line: String): TimelineEventType? {
        val clean = normalize(line)
        return when {
            isWicketLine(clean) -> TimelineEventType.WICKET
            sixPattern.containsMatchIn(clean) -> TimelineEventType.SIX
            fourPattern.containsMatchIn(clean) -> TimelineEventType.FOUR
            milestonePatterns.any { it.containsMatchIn(clean) } -> TimelineEventType.MILESTONE
            else -> null
        }
    }

    fun extract(commentary: List<String>): List<TimelineEvent> {
        val deduped = deduplicateByBall(commentary)
        val events = mutableListOf<TimelineEvent>()
        var currentOver = 0f

        for (cleanLine in deduped) {
            if (cleanLine.isBlank()) continue

            ballNumberOf(cleanLine)?.toFloatOrNull()?.let { currentOver = it }

            val isWicket = detectBallType(cleanLine) == TimelineEventType.WICKET

            val isMilestone = milestonePatterns.any { it.containsMatchIn(cleanLine) }
            val isSix = sixPattern.containsMatchIn(cleanLine)
            val isFour = fourPattern.containsMatchIn(cleanLine)
            val isOverEnd = endOfOverPattern.containsMatchIn(cleanLine)

            val desc = cleanLine.trimEnd('/').trim().take(120)
            fun add(type: TimelineEventType, title: String) {
                events.add(TimelineEvent(over = currentOver, type = type, title = title, description = desc))
            }

            if (isWicket) {
                add(TimelineEventType.WICKET, "Wicket!")
                continue
            }
            // Milestone is non-terminal: a six/four/over-end on the same line still emits.
            if (isMilestone) add(TimelineEventType.MILESTONE, "Milestone")

            when {
                isSix -> add(TimelineEventType.SIX, "SIX!")
                isFour -> add(TimelineEventType.FOUR, "FOUR!")
                isOverEnd -> add(TimelineEventType.OVER_END, "End of Over")
            }
        }

        // Deduplicate events by over+type (keep first occurrence)
        val seenEvents = mutableSetOf<String>()
        val uniqueEvents = events.filter { e ->
            val key = "${e.over}_${e.type}"
            seenEvents.add(key)
        }

        return uniqueEvents.sortedByDescending { it.over }
    }
}
