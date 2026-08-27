package com.tanush.bouncer.utils

import com.tanush.bouncer.analysis.CricketConstants
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

    /**
     * Cricbuzz's outcome clause: the field after "<Bowler> to <Batter>," and before the description.
     *
     * The clause is the only STRUCTURAL statement of what a delivery was; everything after it is
     * prose, and prose about a delivery routinely contains the words for other deliveries. An
     * on-field OUT the batter successfully reviewed reads "…, no run, … has been given OUT LBW, but
     * he reviews … and Sooriyabandara survives" — every dismissal pattern below fires on that, and
     * two such lines were the entire contents of the commentary tab's Wickets filter on match
     * 163017. This also drives the "Wicket!" push notification, so the false positive rings a phone.
     *
     * Mirrors `outcomeClause` / `classifyEvent` in `match-sync/src/llm.js`.
     */
    private val ballOutcomeClause = Regex("""^[^,]+,\s*([^,]+)""")
    private val trailingPunctuation = Regex("[.!]+$")

    /** A clause that states an outcome in runs is a positive statement that nobody was dismissed. */
    private val scoringClause =
        Regex("""^(no run|\d{1,3} runs?|four|six)$""", RegexOption.IGNORE_CASE)
    private val dismissalClause = Regex("""^(out\b|that'?s out\b)""", RegexOption.IGNORE_CASE)

    private fun outcomeClause(line: String): String =
        ballOutcomeClause.find(line)?.groupValues?.get(1)?.trim()?.replace(trailingPunctuation, "").orEmpty()

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

    // Hoisted out of deduplicateByBall. `whitespaceRun` was rebuilt once per line and the two
    // leading-token patterns once per INNER-loop iteration of an O(n²) pass — roughly 1500 Regex
    // compilations for a 40-line commentary block, on every render.
    private val whitespaceRun = Regex("[\\s\\p{Zs}]+")

    private fun normalize(raw: String): String =
        raw.replace(aiFusedPrefix, "").trim().trimEnd('/')

    /** Deduplicate commentary by ball number — keeps the longest entry per delivery.
     *  CricBuzz sends progressive updates for the same ball; this keeps only the most complete.
     *  Internal so OverviewTabV2 and CommentaryTabV2 can reuse it. */
    internal fun deduplicateByBall(lines: List<String>): List<String> =
        deduplicateByBall(lines, textOf = { it }, withText = { _, text -> text })

    /**
     * As above, over items that carry a commentary line plus metadata that must survive the pass.
     *
     * The generic form exists because this function *rewrites* the text it dedupes — it strips the
     * `[AI Fused]` prefix, collapses whitespace and trims a trailing slash — so its output strings
     * are not its input strings and cannot be matched back to their source item afterwards. The
     * commentary tab needs the server's event verdict to survive alongside the line, and the only
     * alternatives were to re-implement [normalize] at the call site or to key a lookup on text
     * this function is in the middle of changing. Both are a drift waiting to happen.
     */
    internal fun <T> deduplicateByBall(
        items: List<T>,
        textOf: (T) -> String,
        withText: (T, String) -> T,
    ): List<T> {
        val normalized = items.mapNotNull { item ->
            val text = normalize(textOf(item)).replace(' ', ' ').replace(whitespaceRun, " ").trim()
            if (text.isBlank()) null else withText(item, text)
        }
        return dropNearDuplicates(keepLongestPerBall(normalized, textOf), textOf)
    }

    /** True when [line] announces a dismissal and neither its outcome clause nor a veto denies it. */
    fun isWicketLine(line: String): Boolean {
        val clean = normalize(line)
        val clause = outcomeClause(clean)
        if (dismissalClause.containsMatchIn(clause)) return true
        if (scoringClause.matches(clause)) return false
        if (wicketVetoPattern.containsMatchIn(clean)) return false
        return wicketPatterns.any { it.containsMatchIn(clean) }
    }

    fun detectBallType(line: String): TimelineEventType? {
        val clean = normalize(line)
        if (isWicketLine(clean)) return TimelineEventType.WICKET
        val milestone = milestonePatterns.firstOrNull { it.containsMatchIn(clean) }?.let {
            TimelineEventType.MILESTONE
        }
        // The clause outranks the prose for the same reason it does for a dismissal: a line whose
        // clause reads "no run" is a dot ball however many boundaries its description reminisces
        // about. A clause that states runs settles the delivery, so the prose is not consulted.
        val clause = outcomeClause(clean)
        return when {
            clause.equals("six", ignoreCase = true) -> TimelineEventType.SIX
            clause.equals("four", ignoreCase = true) -> TimelineEventType.FOUR
            scoringClause.matches(clause) -> milestone
            sixPattern.containsMatchIn(clean) -> TimelineEventType.SIX
            fourPattern.containsMatchIn(clean) -> TimelineEventType.FOUR
            else -> milestone
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

// ── deduplicateByBall's two passes ───────────────────────────────────────────
//
// Top-level rather than members so the object stays under detekt's TooManyFunctions threshold —
// the same reason MatchDetailViewModel keeps isMatchOver and scrapeCommentary outside the class.
// They are file-private, so this is a layout choice and not a widening of the API.

// Hoisted out of the dedup pass: these were rebuilt once per INNER-loop iteration of an O(n²)
// pass — roughly 1500 Regex compilations for a 40-line commentary block, on every render.
private val leadingBallTokens = Regex("^[\\d.\\s]+")
private val leadingNumber = Regex("^\\d+\\s+")

/** Form used for near-duplicate comparison: lowercased, with any leading ball number stripped. */
private fun comparisonForm(line: String): String =
    line.lowercase().replace(leadingBallTokens, "").replace(leadingNumber, "")

/** Pass 1: group by ball number, keep longest (most complete progressive update). */
private fun <T> keepLongestPerBall(normalized: List<T>, textOf: (T) -> String): List<T> {
    val bestByBall = longestPerBall(normalized, textOf)
    val seen = mutableSetOf<String>()
    return normalized.mapNotNull { item ->
        val ball = ballNumberOf(textOf(item))
        // A line with no ball number dedupes against its own text instead.
        val key = ball ?: textOf(item).lowercase()
        when {
            !seen.add(key) -> null
            ball != null -> bestByBall.getValue(ball)
            else -> item
        }
    }
}

/** The most complete update seen for each ball number. */
private fun <T> longestPerBall(items: List<T>, textOf: (T) -> String): Map<String, T> {
    val best = mutableMapOf<String, T>()
    for (item in items) {
        val ball = ballNumberOf(textOf(item)) ?: continue
        val existing = best[ball]
        if (existing == null || textOf(item).length > textOf(existing).length) best[ball] = item
    }
    return best
}

/** Pass 2: prefix dedup — keep only the longer of near-duplicate pairs. */
private fun <T> dropNearDuplicates(items: List<T>, textOf: (T) -> String): List<T> {
    // Comparison forms built ONCE per line, not once per comparison.
    val compare = items.map { comparisonForm(textOf(it)) }
    val toRemove = mutableSetOf<Int>()
    for (i in compare.indices) {
        if (i !in toRemove) markDuplicatesOf(i, compare, toRemove)
    }
    return items.filterIndexed { idx, _ -> idx !in toRemove }
}

/** Compare [i] against every later survivor, dropping whichever of the pair is shorter. */
private fun markDuplicatesOf(i: Int, compare: List<String>, toRemove: MutableSet<Int>) {
    for (j in i + 1 until compare.size) {
        if (j in toRemove) continue
        if (isNearDuplicate(compare[i], compare[j])) {
            toRemove.add(if (compare[i].length <= compare[j].length) i else j)
        }
    }
}

/** True when the shorter of the two is a long-enough prefix of the longer. */
private fun isNearDuplicate(a: String, b: String): Boolean {
    val shorter = if (a.length <= b.length) a else b
    val longer = if (a.length <= b.length) b else a
    if (shorter.length < CricketConstants.DEDUP_MIN_LENGTH) return false
    val prefixLen = (shorter.length * CricketConstants.DEDUP_PREFIX_RATIO).toInt()
        .coerceAtLeast(CricketConstants.DEDUP_MIN_LENGTH)
    return longer.startsWith(shorter.take(prefixLen))
}
