package com.tanush.bouncer.ui.components.v2

import com.tanush.bouncer.models.CricketMatch

/** One innings from a score string. [wickets] is null when the string omits it. */
data class InningsScore(
    val runs: Int,
    val wickets: Int? = null,
    val declared: Boolean = false,
    val followOn: Boolean = false,
)

enum class HeroSession { Stumps, Lunch, Tea, Drinks, Other }

/** [session] is null when the status names a day with nothing after it. */
data class TestDay(val day: Int, val session: HeroSession?)

private val INNINGS_TOKEN = Regex("""^(\d+)(?:/(\d+))?\s*(declared|dec|d)?\b""")
private val DAY_STATUS = Regex("""\bDay\s*(\d+)\s*(?:[:\-,]\s*(.*))?""", RegexOption.IGNORE_CASE)

/**
 * Splits `"312 & 145/3"`, `"312/9d & 145/3"`, `"312 f/o & 100/2"` or `"312/9 dec"` into innings.
 * A part that does not start with a run total is dropped.
 */
fun splitInningsScore(raw: String): List<InningsScore> =
    raw.replace(Regex("""\([^)]*\)"""), " ").split("&").mapNotNull { part ->
        val lower = part.trim().lowercase()
        val followOn = lower.contains("f/o")
        val token = INNINGS_TOKEN.find(lower.replace("f/o", "").trim()) ?: return@mapNotNull null
        InningsScore(
            runs = token.groupValues[1].toIntOrNull() ?: return@mapNotNull null,
            wickets = token.groupValues[2].toIntOrNull(),
            declared = token.groupValues[3].isNotEmpty(),
            followOn = followOn,
        )
    }

/** Lead or trail from each side's innings totals; null before anyone has scored. */
fun testLeadLine(match: CricketMatch): HeroLine? {
    val (first, second) = match.teams.take(2).takeIf { it.size == 2 } ?: return null
    if (first.score.isBlank() && second.score.isBlank()) return null
    val diff = splitInningsScore(first.score).sumOf { it.runs } - splitInningsScore(second.score).sumOf { it.runs }
    return when {
        diff > 0 -> HeroLine.Lead(match.teams[0].name, diff)
        diff < 0 -> HeroLine.Trail(match.teams[0].name, -diff)
        else -> HeroLine.Level
    }
}

private val STATED_LEAD = Regex("""lead by|trail by|\bneeds?\b""", RegexOption.IGNORE_CASE)

/**
 * The hero's Test line, or null when the status text already states a lead, trail or target: the
 * status ("Day 2: Stumps - India lead by 207") is shown in full, so the sentence appears once.
 */
fun testHeroLine(match: CricketMatch): HeroLine? =
    if (STATED_LEAD.containsMatchIn(match.currentStatus)) null else testLeadLine(match)

/** `"Day 2: Stumps"` → day 2, Stumps. Null when the status names no day. */
fun testDayFor(match: CricketMatch): TestDay? {
    val found = listOf(match.currentStatus, match.status).firstNotNullOfOrNull { DAY_STATUS.find(it) } ?: return null
    val day = found.groupValues[1].toIntOrNull() ?: return null
    val tail = found.groupValues[2].trim().lowercase()
    val session = when {
        tail.isEmpty() -> null
        tail.startsWith("stump") -> HeroSession.Stumps
        tail.startsWith("lunch") -> HeroSession.Lunch
        tail.startsWith("tea") -> HeroSession.Tea
        tail.startsWith("drink") -> HeroSession.Drinks
        else -> HeroSession.Other
    }
    return TestDay(day, session)
}
