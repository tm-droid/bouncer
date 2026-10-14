package com.tanush.bouncer.data.history

import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.utils.normalizeTeamName
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Which Cricsheet match a Cricbuzz match is, or null.
 *
 * Cricsheet carries no Cricbuzz id, so the join is by date and sides. It is strict on purpose:
 * `HighlightFuzzyMatcher` linked 154 of 400 highlights on a test device and every link was wrong
 * (`.claude/rules/data-and-storage.md`). A wrong ball-by-ball is worse than none, so:
 *
 * - the dates must be within a day (Cricsheet records the local date, Cricbuzz an epoch);
 * - the gender must agree;
 * - both sides must be the same name once normalised, or one side exactly and the other sharing a
 *   significant word (a renamed franchise: "Kings XI Punjab" / "Punjab Kings"), and only when that
 *   leaves exactly one candidate;
 * - a qualifier ("A", "U19", "Emerging") on one side and not the other vetoes, because "India A"
 *   playing on the same day as "India" is real;
 * - two candidates link to nothing.
 */
object HistoryMatchLinker {
    private const val DATE_TOLERANCE_DAYS = 1L
    // Not "XI": it is part of real names ("Kings XI Punjab", "Prime Minister's XI").
    private val QUALIFIERS = setOf("a", "u19", "u 19", "under 19", "emerging", "academy", "development")
    private val NOISE = setOf("cricket", "team", "national", "the", "of", "and", "club", "county", "women", "w", "xi")

    /** The months whose index can hold this match: its own, and a neighbour when it is a day from the edge. */
    fun monthsFor(match: CricketMatch): List<String> {
        val date = dateOf(match) ?: return emptyList()
        return listOf(date.minusDays(DATE_TOLERANCE_DAYS), date, date.plusDays(DATE_TOLERANCE_DAYS))
            .map { "%04d-%02d".format(it.year, it.monthValue) }
            .distinct()
    }

    fun link(match: CricketMatch, rows: List<HistoryIndexRow>): String? {
        val date = dateOf(match) ?: return null
        val sides = match.teams.map { it.name }.takeIf { it.size == 2 } ?: return null
        val gender = if (match.gender.equals("Women", ignoreCase = true)) "female" else "male"
        val sameDay = rows.filter { row ->
            row.gender == gender && withinTolerance(row.date, date)
        }

        val exact = sameDay.filter { sidesEqual(it, sides) { a, b -> canonical(a) == canonical(b) } }
        if (exact.size == 1) return exact.single().id
        if (exact.size > 1) return null

        val renamed = sameDay.filter { row ->
            sidesEqual(row, sides, ::sameOrRenamed) &&
                listOf(row.team1, row.team2).any { team -> sides.any { canonical(it) == canonical(team) } }
        }
        return renamed.singleOrNull()?.id
    }

    private fun dateOf(match: CricketMatch): LocalDate? =
        match.startDateMs?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }

    private fun withinTolerance(rowDate: String, date: LocalDate): Boolean {
        val d = runCatching { LocalDate.parse(rowDate) }.getOrNull() ?: return false
        return !d.isBefore(date.minusDays(DATE_TOLERANCE_DAYS)) && !d.isAfter(date.plusDays(DATE_TOLERANCE_DAYS))
    }

    /** Both sides pair up, in either order. */
    private fun sidesEqual(row: HistoryIndexRow, sides: List<String>, same: (String, String) -> Boolean): Boolean =
        (same(row.team1, sides[0]) && same(row.team2, sides[1])) ||
            (same(row.team1, sides[1]) && same(row.team2, sides[0]))

    /** Normalised, with the women's marker dropped: gender is compared on its own. */
    internal fun canonical(name: String): String =
        normalizeTeamName(name).split(" ").filter { it != "women" && it != "w" }.joinToString(" ")

    private fun qualifiers(name: String): Set<String> {
        val words = canonical(name).split(" ")
        return QUALIFIERS.filterTo(mutableSetOf()) { q -> q.split(" ").all { it in words } }
    }

    private fun sameOrRenamed(a: String, b: String): Boolean {
        if (canonical(a) == canonical(b)) return true
        if (qualifiers(a) != qualifiers(b)) return false
        val wa = canonical(a).split(" ").filter { it.isNotBlank() && it !in NOISE && it !in QUALIFIERS }.toSet()
        val wb = canonical(b).split(" ").filter { it.isNotBlank() && it !in NOISE && it !in QUALIFIERS }.toSet()
        return wa.intersect(wb).isNotEmpty()
    }
}
