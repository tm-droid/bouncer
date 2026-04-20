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

    /** Deduplicate commentary — CricBuzz sends progressive updates for the same ball. */
    private fun deduplicateByBall(lines: List<String>): List<String> {
        // Pass 1: group by ball number, keep longest entry per ball
        val ballGroups = mutableMapOf<String, String>()
        val noBallLines = mutableListOf<String>()
        for (raw in lines) {
            val line = raw.replace(Regex("^\\[AI Fused\\]\\s*"), "").trim().trimEnd('/')
            if (line.isBlank()) continue
            val m = ballNumberRegex.find(line.trimStart())
            if (m != null) {
                val key = m.groupValues[1]
                val existing = ballGroups[key]
                if (existing == null || line.length > existing.length) {
                    ballGroups[key] = line
                }
            } else {
                noBallLines.add(line)
            }
        }
        val deduped = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (raw in lines) {
            val line = raw.replace(Regex("^\\[AI Fused\\]\\s*"), "").trim().trimEnd('/')
            if (line.isBlank()) continue
            val m = ballNumberRegex.find(line.trimStart())
            if (m != null) {
                val key = m.groupValues[1]
                if (key !in seen) {
                    seen.add(key)
                    ballGroups[key]?.let { deduped.add(it) }
                }
            } else {
                deduped.add(line)
            }
        }
        // Pass 2: remove near-duplicates where shorter line is prefix of longer
        val result = mutableListOf<String>()
        for (line in deduped) {
            val dominated = result.any { existing ->
                val short = if (line.length < existing.length) line else existing
                val long = if (line.length < existing.length) existing else line
                val prefixLen = (short.length * 0.8).toInt().coerceAtLeast(15)
                prefixLen <= short.length && long.lowercase().startsWith(short.lowercase().take(prefixLen))
            }
            if (!dominated || result.none { it.length >= line.length && it.lowercase().startsWith(line.lowercase().take((line.length * 0.8).toInt().coerceAtLeast(15).coerceAtMost(line.length))) }) {
                result.add(line)
            }
        }
        return result
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

            if (isWicket) {
                events.add(TimelineEvent(
                    over = currentOver,
                    type = TimelineEventType.WICKET,
                    title = "Wicket!",
                    description = desc,
                ))
                continue
            }

            if (isMilestone) {
                events.add(TimelineEvent(
                    over = currentOver,
                    type = TimelineEventType.MILESTONE,
                    title = "Milestone",
                    description = desc,
                ))
            }

            if (isSix) {
                events.add(TimelineEvent(
                    over = currentOver,
                    type = TimelineEventType.SIX,
                    title = "SIX!",
                    description = desc,
                ))
                continue
            }

            if (isFour) {
                events.add(TimelineEvent(
                    over = currentOver,
                    type = TimelineEventType.FOUR,
                    title = "FOUR!",
                    description = desc,
                ))
                continue
            }

            if (isOverEnd) {
                events.add(TimelineEvent(
                    over = currentOver,
                    type = TimelineEventType.OVER_END,
                    title = "End of Over",
                    description = desc,
                ))
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
