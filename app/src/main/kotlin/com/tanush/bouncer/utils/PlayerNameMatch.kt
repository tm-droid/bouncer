package com.tanush.bouncer.utils

import com.tanush.bouncer.data.history.HistoryMatchLinker
import com.tanush.bouncer.data.history.HistoryNameRow
import kotlin.math.abs
import kotlin.math.ln

/** A history player and how well the query names them. */
data class PlayerNameHit(val row: HistoryNameRow, val score: Double)

/**
 * Matches a query against Cricsheet's names, which are `"V Kohli"`, `"AB de Villiers"`, `"Shafali Verma"`:
 * initials and a surname, sometimes a full given name. History only, so it has no server mirror.
 *
 * - The query's last word is the surname. It matches the player's last word exactly, as a prefix
 *   (3+ letters) or within [SearchScore.editTolerance] edits.
 * - Any earlier query words are given names or initials. Their first letters must appear, in order,
 *   among the player's initials, so "virat kohli" fits "V Kohli" and not "A Kohli".
 * - Mohammad, Mohammed, Muhammad and Md are one word.
 * - An exact surname beats a prefix, which beats a typo; ties go to the player with more matches.
 */
object PlayerNameMatch {
    private const val EXACT = 1.0
    private const val PREFIX = 0.9
    private const val TYPO = 0.65
    private const val MIN_PREFIX_LENGTH = 3
    private const val MAX_LETTERS = 3
    private const val INITIALS_MAX_LENGTH = 3
    private val MOHAMMAD = setOf("mohammad", "mohammed", "muhammad", "muhammed", "mohamed", "mohamad", "mohd", "md")

    /** The surname-file letters to load for [query]: the last word's first, then the others, at most three. */
    fun letters(query: String): List<Char> =
        words(query).reversed().mapNotNull { it.firstOrNull() }.filter { it in 'a'..'z' }.distinct().take(MAX_LETTERS)

    fun search(query: String, rows: List<HistoryNameRow>, limit: Int = Int.MAX_VALUE): List<PlayerNameHit> {
        val words = words(query)
        val surname = words.lastOrNull() ?: return emptyList()
        val given = words.dropLast(1).map { it.first() }
        return rows.mapNotNull { row ->
            val score = surnameScore(surname, surnameOf(row.name))
            if (score > 0.0 && agrees(given, initialsOf(row.name))) PlayerNameHit(row, score) else null
        }
            .sortedWith(compareByDescending<PlayerNameHit> { it.score }.thenByDescending { ln(1.0 + it.row.matches) })
            .take(limit)
    }

    /** Same surname, same first initial and the same side: the test for treating two names as one person. */
    fun samePlayer(name: String, side: String, row: HistoryNameRow): Boolean {
        if (side.isBlank()) return false
        val words = words(name)
        val rowWords = words(row.name)
        return words.size >= 2 && rowWords.size >= 2 &&
            words.last() == rowWords.last() && words.first().first() == rowWords.first().first() &&
            HistoryMatchLinker.canonical(side) == HistoryMatchLinker.canonical(row.team)
    }

    internal fun surnameOf(name: String): String = words(name).lastOrNull().orEmpty()

    /** `"AB de Villiers"` gives a, b, d: a short all-capitals word is initials, any other word its first letter. */
    internal fun initialsOf(name: String): List<Char> =
        name.trim().split(Regex("\\s+")).dropLast(1).flatMap { word ->
            if (word.length in 2..INITIALS_MAX_LENGTH && word.all { it.isUpperCase() }) word.lowercase().toList()
            else listOfNotNull(normalizeForSearch(word).firstOrNull())
        }

    private fun words(text: String): List<String> =
        normalizeForSearch(text).split(' ').filter { it.isNotEmpty() }.map { if (it in MOHAMMAD) "mohammad" else it }

    /** Each query initial must be found in the player's initials, left to right. */
    private fun agrees(given: List<Char>, initials: List<Char>): Boolean {
        var at = 0
        for (c in given) {
            val found = initials.drop(at).indexOf(c)
            if (found < 0) return false
            at += found + 1
        }
        return true
    }

    private fun surnameScore(query: String, surname: String): Double {
        if (surname.isEmpty()) return 0.0
        if (query == surname) return EXACT
        if (query.length >= MIN_PREFIX_LENGTH && surname.startsWith(query)) return PREFIX
        val tolerance = SearchScore.editTolerance(query)
        if (tolerance == 0 || abs(query.length - surname.length) > tolerance) return 0.0
        val distance = SearchScore.editDistance(query, surname)
        return if (distance in 1..tolerance) TYPO * (1.0 - distance.toDouble() / query.length) else 0.0
    }
}

/** Links a Cricbuzz player to their Cricsheet record, only when the join is certain. */
object CareerJoin {
    /**
     * The one row of [rows] with [name]'s surname and first initial that played for [side], else null.
     * Two candidates (two "S Sharma"s for the same side) give no link: a career of the wrong person is
     * worse than none.
     */
    fun link(name: String, side: String, rows: List<HistoryNameRow>): HistoryNameRow? =
        rows.filter { PlayerNameMatch.samePlayer(name, side, it) }.singleOrNull()
}
