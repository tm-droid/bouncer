package com.tanush.bouncer.utils

import java.text.Normalizer

/**
 * Text normalisation and relevance scoring for search.
 *
 * Pure, so it can be unit-tested directly. [normalizeForSearch] is mirrored by `normalizeForSearch`
 * in `appwrite/functions/api/src/player-search/playerIndex.js`, and both are pinned against one shared
 * fixture (`SearchNormalizationParityTest`): the device re-scores the server's player hits, so the
 * two must agree on what a name *is* before they can agree on how well it matches.
 */

private val COMBINING_MARKS = """\p{M}+""".toRegex()

/** Characters deleted outright rather than turned into a word break: "St. Kitts", "U.A.E", "Women's". */
private val JOINING_PUNCTUATION = """[.'’`]""".toRegex()
private val NON_ALPHANUMERIC = """[^\p{L}\p{N}]+""".toRegex()

/**
 * Accent-folded, lowercased, punctuation-free, single-spaced.
 *
 * Search used to compare `trim().lowercase()`, so "St. Kitts" never matched "St Kitts", "Müller"
 * never matched "muller", and "Sunrisers-Hyderabad" split differently from the name it named.
 */
fun normalizeForSearch(text: String): String {
    val folded = COMBINING_MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
    return NON_ALPHANUMERIC.replace(JOINING_PUNCTUATION.replace(folded.lowercase(), ""), " ").trim()
}

object SearchScore {
    // Score bands. Each tier's floor sits above the next tier's ceiling, so the kind of match
    // always dominates and coverage only orders candidates within a kind.
    const val PREFIX_MIN = 0.95        // target starts with query      → [0.95, 1.00]
    const val WORD_PREFIX_MIN = 0.85   // some word starts with query   → [0.85, 0.95)
    const val SUBSTRING_MIN = 0.70     // query appears somewhere       → [0.70, 0.85)
    const val FUZZY_MAX = 0.65         // within edit distance          → [0.00, 0.65]

    /**
     * A query that is a team's curated code ("rcb", "csk"). Inside the prefix band because it is
     * as certain as typing the name, and above every prefix hit shorter than a full name, so "mi"
     * ranks Mumbai Indians above "MI Cape Town" rather than below it.
     */
    const val ACRONYM = 0.97

    private const val SHORT_QUERY_MAX = 3
    private const val MEDIUM_QUERY_MAX = 7

    /**
     * Agreement between an already-normalised [query] and [target], 0–1.
     *
     * The whole query is tried as one string first (prefix, word prefix, substring), which is what
     * a partly-typed name looks like. Only when that misses does a multi-word query fall back to
     * matching word by word, which is what makes "kohli virat" and "virat kolhi" find "virat kohli"
     * — the old scorer compared the whole query against single words and gave both zero.
     *
     * [allowFuzzy] switches off edit-distance matching only; callers disable it when the query is
     * itself a real entity name rather than something worth correcting ("india" is two edits from
     * "indies").
     */
    fun of(query: String, target: String, allowFuzzy: Boolean = true): Double {
        if (query.isEmpty() || target.isEmpty()) return 0.0
        val coverage = (query.length.toDouble() / target.length).coerceIn(0.0, 1.0)

        if (target.startsWith(query)) {
            // An exact equality has coverage 1.0 and so lands on exactly 1.0.
            return PREFIX_MIN + (1.0 - PREFIX_MIN) * coverage
        }

        val words = target.split(' ')
        if (words.any { it.startsWith(query) }) {
            return WORD_PREFIX_MIN + (PREFIX_MIN - WORD_PREFIX_MIN) * coverage
        }

        val index = target.indexOf(query)
        if (index >= 0) {
            // A match late in the string is weaker evidence than one near the front.
            val position = 1.0 - index.toDouble() / target.length
            return SUBSTRING_MIN + (WORD_PREFIX_MIN - SUBSTRING_MIN) * coverage * position
        }

        val tokens = query.split(' ')
        if (tokens.size > 1) return tokenScore(tokens, words, coverage, allowFuzzy)
        return if (allowFuzzy) fuzzyWordScore(query, words) else 0.0
    }

    /**
     * Every query word must find a target word it prefixes, or one within its edit tolerance.
     *
     * All prefixes, in any order, is word-prefix evidence. Any typo drops the whole match to the
     * fuzzy band, scaled by how much of the query needed no correction — so one misspelt surname
     * after a correct first name ("virat kolhi") stays well above noise, where scoring the typo
     * alone would not.
     */
    private fun tokenScore(
        tokens: List<String>,
        words: List<String>,
        coverage: Double,
        allowFuzzy: Boolean,
    ): Double {
        var anyFuzzy = false
        var weighted = 0.0
        var totalLength = 0
        for (token in tokens) {
            totalLength += token.length
            if (words.any { it.startsWith(token) }) {
                weighted += token.length
                continue
            }
            if (!allowFuzzy) return 0.0
            val distance = words.minOf { editDistance(token, it) }
            if (distance > editTolerance(token)) return 0.0
            anyFuzzy = true
            weighted += token.length * (1.0 - distance.toDouble() / token.length)
        }
        if (!anyFuzzy) return WORD_PREFIX_MIN + (PREFIX_MIN - WORD_PREFIX_MIN) * coverage
        return (FUZZY_MAX * weighted / totalLength).coerceIn(0.0, FUZZY_MAX)
    }

    /**
     * Edit-distance tier for a one-word query.
     *
     * The tolerance steps up with the query length ([editTolerance]): a short query is a different
     * word after one edit, and `"in"` used to fuzzy-match `"won"`.
     */
    private fun fuzzyWordScore(query: String, words: List<String>): Double {
        val minDistance = words.minOfOrNull { editDistance(query, it) } ?: return 0.0
        if (minDistance > editTolerance(query)) return 0.0
        val ratio = 1.0 - minDistance.toDouble() / query.length
        return (ratio * FUZZY_MAX).coerceIn(0.0, FUZZY_MAX)
    }

    /**
     * Permitted edit distance for [query]: none up to 3 characters, one up to 7, two from 8.
     * Mirrored by `editTolerance` in `playerIndex.js` and pinned by the shared fixture.
     */
    fun editTolerance(query: String): Int = when {
        query.length <= SHORT_QUERY_MAX -> 0
        query.length <= MEDIUM_QUERY_MAX -> 1
        else -> 2
    }

    /**
     * Optimal-string-alignment distance: Levenshtein plus adjacent transposition as one edit.
     *
     * The commonest typo in a name is two swapped letters — "kholi", "kolhi" for "kohli" — and plain
     * Levenshtein charges that two edits, which on a five-letter name is the whole tolerance and a
     * score below every threshold that matters.
     */
    fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prevPrev = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                val best = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                curr[j] = if (isTransposition(a, b, i, j)) minOf(best, prevPrev[j - 2] + 1) else best
            }
            val recycled = prevPrev
            prevPrev = prev
            prev = curr
            curr = recycled
        }
        return prev[b.length]
    }

    /** `a[i-2..i-1]` is `b[j-2..j-1]` with its two characters swapped. */
    private fun isTransposition(a: String, b: String, i: Int, j: Int): Boolean =
        i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]
}

private const val MIN_HIGHLIGHT_LENGTH = 2

/**
 * Character ranges of [text] to emphasise for [query]: each query word where it starts a word of
 * [text], else its first occurrence anywhere.
 *
 * Matched case-insensitively on the original text rather than on [normalizeForSearch]'s output,
 * because normalisation deletes characters and its offsets would not line up with what is drawn.
 * A word the text only matches after normalisation (an accent, a typo) is simply not emphasised —
 * a missing highlight is harmless, a misplaced one is not.
 */
fun matchRanges(text: String, query: String): List<IntRange> {
    val lower = text.lowercase()
    // lowercase() can change length for a handful of scripts; offsets would then be wrong.
    if (lower.length != text.length) return emptyList()
    val ranges = query.lowercase().split(' ', '.', '-', '\'')
        .filter { it.length >= MIN_HIGHLIGHT_LENGTH }
        .distinct()
        .mapNotNull { token ->
            val atWordStart = Regex("""(?<![\p{L}\p{N}])${Regex.escape(token)}""").find(lower)
            val start = atWordStart?.range?.first ?: lower.indexOf(token).takeIf { it >= 0 }
            start?.let { it until it + token.length }
        }
        .sortedBy { it.first }
    // Merge overlaps so a span is never emphasised twice.
    return ranges.fold(mutableListOf()) { merged, range ->
        val last = merged.lastOrNull()
        if (last != null && range.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            merged += range
        }
        merged
    }
}
