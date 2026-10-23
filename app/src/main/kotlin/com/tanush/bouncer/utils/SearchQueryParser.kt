package com.tanush.bouncer.utils

import com.tanush.bouncer.data.SearchRepository

/**
 * A normalised query split into the words that name something and the facets that describe it.
 *
 * [text] is the query as typed (normalised, facet words kept). [words] drops the facet and "v"
 * tokens, so "ind v aus 2011 final" has words `[ind, aus]`. Facets only ever boost a ranking;
 * none of them filters, except a [pair], which needs both sides present in a match.
 */
data class ParsedQuery(
    val text: String,
    val words: List<String>,
    val year: Int?,
    /** "2011/12" for "2011 12" or "2011-12". [year] is the first year. */
    val season: String?,
    /** TEST, ODI, T20I or T20. */
    val format: String?,
    /** FINAL, SEMI or QUALIFIER (eliminators count as qualifiers). */
    val stage: String?,
    /** The raw normalised sides of "a v b". */
    val pair: Pair<String, String>?,
) {
    /** The query without its facets, or [text] when nothing but facets was typed. */
    val entityText: String get() = words.joinToString(" ").ifEmpty { text }
    val hasFacets: Boolean get() = year != null || format != null || stage != null
}

private val VERSUS_WORDS = setOf("vs", "v", "versus")
private val FORMAT_WORDS = mapOf(
    "test" to "TEST", "tests" to "TEST", "odi" to "ODI", "odis" to "ODI",
    "t20i" to "T20I", "t20is" to "T20I", "t20" to "T20", "t20s" to "T20",
)
private val STAGE_WORDS = mapOf(
    "final" to "FINAL", "semi" to "SEMI", "semis" to "SEMI", "semifinal" to "SEMI",
    "semifinals" to "SEMI", "qualifier" to "QUALIFIER", "eliminator" to "QUALIFIER",
)
private const val MIN_YEAR = 1877
private const val MAX_YEAR = 2100
private const val CENTURY = 100
private const val YEAR_LENGTH = 4

/** Splits an already-[normalizeForSearch]ed query; see [ParsedQuery]. */
fun parseQuery(normalized: String): ParsedQuery {
    val scan = FacetScan()
    val tokens = normalized.split(' ').filter { it.isNotEmpty() }
    var i = 0
    while (i < tokens.size) i += scan.take(tokens, i)
    val content = scan.content
    val split = content.indexOfFirst { it in VERSUS_WORDS }
    val pair = if (split > 0 && split < content.lastIndex) {
        content.subList(0, split).joinToString(" ") to content.subList(split + 1, content.size).joinToString(" ")
    } else null
    return ParsedQuery(
        text = normalized,
        words = content.filter { it !in VERSUS_WORDS },
        year = scan.year,
        season = scan.season,
        format = scan.format,
        stage = scan.stage,
        pair = pair,
    )
}

/** Walks the tokens once; each facet is taken from the first token that names it. */
private class FacetScan {
    var year: Int? = null
    var season: String? = null
    var format: String? = null
    var stage: String? = null

    /** Entity words, with "v" kept so the pair can split on it. */
    val content = mutableListOf<String>()

    /** Consumes the facet at [index] and returns how many tokens it used, or keeps the token as a word. */
    fun take(tokens: List<String>, index: Int): Int {
        val token = tokens[index]
        return when {
            year == null && yearOf(token) != null -> takeYear(tokens, index)
            format == null && token in FORMAT_WORDS -> {
                format = FORMAT_WORDS.getValue(token)
                1
            }
            stage == null && token in STAGE_WORDS -> takeStage(tokens, index)
            else -> {
                content += token
                1
            }
        }
    }

    private fun yearOf(token: String): Int? =
        token.toIntOrNull()?.takeIf { token.length == YEAR_LENGTH && it in MIN_YEAR..MAX_YEAR }

    private fun takeYear(tokens: List<String>, index: Int): Int {
        val first = yearOf(tokens[index]) ?: return 1
        year = first
        val next = tokens.getOrNull(index + 1)
        if (next == null || next.length != 2 || next.toIntOrNull() != (first + 1) % CENTURY) return 1
        season = "$first/$next"
        return 2
    }

    private fun takeStage(tokens: List<String>, index: Int): Int {
        val token = tokens[index]
        if (token == "semi" && tokens.getOrNull(index + 1) == "final") {
            stage = "SEMI"
            return 2
        }
        stage = STAGE_WORDS.getValue(token)
        return 1
    }
}

/**
 * The team names a pair side can mean. An exact code or name wins outright ("aus" is Australia,
 * not every "Aus…" side); otherwise any team with a word starting with [side].
 */
fun resolveSide(side: String, teams: List<SearchRepository.TeamEntry>): List<String> {
    if (side.isEmpty()) return emptyList()
    val exact = teams.filter { it.normalized == side || it.normalizedAcronym == side }
    if (exact.isNotEmpty()) return exact.map { it.name }
    return teams.filter { SearchScore.of(side, it.normalized, allowFuzzy = false) >= SearchScore.WORD_PREFIX_MIN }
        .map { it.name }
}
