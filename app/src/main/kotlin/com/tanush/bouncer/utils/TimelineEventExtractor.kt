package com.tanush.bouncer.utils

import com.tanush.bouncer.models.TimelineEvent
import com.tanush.bouncer.models.TimelineEventType

object TimelineEventExtractor {

    private val overPattern = Regex("^(\\d{1,2}\\.\\d)\\s")
    private val ballNumberRegex = Regex("^\\s*(\\d{1,2}\\.\\d)")
    private val endOfOverPattern = Regex("end of over|over \\d+ complete", RegexOption.IGNORE_CASE)

    private val wicketPatterns = listOf(
        Regex("\\bWICKET\\b", RegexOption.IGNORE_CASE),
        Regex("\\bOUT!", RegexOption.IGNORE_CASE),
        Regex("\\bCATCH\\b", RegexOption.IGNORE_CASE),
        Regex("\\bBOWLED\\b", RegexOption.IGNORE_CASE),
        Regex("\\bLBW\\b", RegexOption.IGNORE_CASE),
        Regex("\\bRUN OUT\\b", RegexOption.IGNORE_CASE),
        Regex("\\bSTUMPED\\b", RegexOption.IGNORE_CASE),
        Regex(", OUT,", RegexOption.IGNORE_CASE),
    )

    // Phrases to strip before wicket detection to avoid false positives
    private val wicketExclusionPhrases = listOf(
        "MID-WICKET", "MIDWICKET", "MID WICKET",
        "OVER THE WICKET", "ROUND THE WICKET", "AROUND THE WICKET",
    )

    private val fourPattern = Regex("\\bFOUR\\b", RegexOption.IGNORE_CASE)
    private val sixPattern = Regex("\\bSIX\\b", RegexOption.IGNORE_CASE)

    private val milestonePatterns = listOf(
        Regex("\\b(50|hundred|century|100|150|200|double century|triple century|300)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(half.?century|fifty)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(5.?wicket|five.?wicket|fifer|hat.?trick)\\b", RegexOption.IGNORE_CASE),
    )

    private val aiFusedPrefix = Regex("^\\[AI Fused\\]\\s*")

    private fun normalize(raw: String): String =
        raw.replace(aiFusedPrefix, "").trim().trimEnd('/')

    /** Deduplicate commentary by ball number — keeps the longest entry per delivery.
     *  CricBuzz sends progressive updates for the same ball; this keeps only the most complete.
     *  Internal so OverviewTabV2 and CommentaryTabV2 can reuse it. */
    internal fun deduplicateByBall(lines: List<String>): List<String> {
        val ballPattern = Regex("^\\s*(\\d+\\.\\d+)")
        val normalized = lines.map {
            normalize(it).replace(' ', ' ').replace(Regex("[\\s\\p{Zs}]+"), " ").trim()
        }.filter { it.isNotBlank() }
        // Pass 1: group by ball number, keep longest (most complete progressive update)
        val bestByBall = mutableMapOf<String, String>()
        for (line in normalized) {
            val m = ballPattern.find(line.trimStart())
            if (m != null) {
                val ball = m.groupValues[1]
                val existing = bestByBall[ball]
                if (existing == null || line.length > existing.length) bestByBall[ball] = line
            }
        }
        val seen = mutableSetOf<String>()
        val pass1 = normalized.mapNotNull { line ->
            val m = ballPattern.find(line.trimStart())
            if (m != null) {
                val ball = m.groupValues[1]
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

    fun detectBallType(line: String): TimelineEventType? {
        val clean = normalize(line)
        val wicketCheck = wicketExclusionPhrases.fold(clean.uppercase()) { t, p -> t.replace(p, "") }
        return when {
            wicketPatterns.any { it.containsMatchIn(wicketCheck) } -> TimelineEventType.WICKET
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

            val overMatch = overPattern.find(cleanLine)
            if (overMatch != null) {
                currentOver = overMatch.groupValues[1].toFloatOrNull() ?: currentOver
            }

            // Strip false-positive phrases before wicket check
            val wicketCheckText = wicketExclusionPhrases.fold(cleanLine.uppercase()) { text, phrase ->
                text.replace(phrase, "")
            }
            val isWicket = wicketPatterns.any { it.containsMatchIn(wicketCheckText) }

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
