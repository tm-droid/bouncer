package com.tanush.bouncer.utils

/**
 * Competition acronyms, expanded to the words the stored series names actually contain.
 *
 * Every series corpus the app has — the `series_cache` table, the `active-series` bucket file and
 * the live Cricbuzz scrape — stores full names: "Indian Premier League 2026", "Caribbean Premier
 * League 2026". Nothing anywhere stores "IPL". So a user typing the name everyone actually uses
 * matched nothing at all, on every path: the Room lookup, the FTS index it used to go through, and
 * the network fallback's `name.contains(query)` alike.
 *
 * Expansions are deliberately the *distinctive* part of the name rather than the whole thing.
 * "Indian Premier" rather than "Indian Premier League 2026", because the corpus appends a season
 * and occasionally a sponsor, and a substring match against the full string would then miss.
 */
private val COMPETITION_ALIASES: Map<String, List<String>> = mapOf(
    // Franchise T20
    "ipl" to listOf("Indian Premier"),
    "bbl" to listOf("Big Bash"),
    "wbbl" to listOf("Women's Big Bash"),
    "psl" to listOf("Pakistan Super"),
    "cpl" to listOf("Caribbean Premier"),
    "sa20" to listOf("SA20"),
    "lpl" to listOf("Lanka Premier"),
    "tnpl" to listOf("Tamil Nadu Premier"),
    "ilt20" to listOf("ILT20", "International League"),
    "mlc" to listOf("Major League"),
    "the hundred" to listOf("Hundred"),
    "wpl" to listOf("Women's Premier"),

    // International tournaments. "World Cup" is intentionally the expansion for every WC variant:
    // the format lives elsewhere in the name ("ICC Men's T20 World Cup"), so narrowing to
    // "T20 World Cup" would miss a corpus that writes it as "ICC T20 World Cup".
    "wc" to listOf("World Cup"),
    "t20 wc" to listOf("World Cup"),
    "t20wc" to listOf("World Cup"),
    "odi wc" to listOf("World Cup"),
    "cwc" to listOf("World Cup"),
    "ct" to listOf("Champions Trophy"),
    "wtc" to listOf("World Test Championship"),
    "acc" to listOf("Asia Cup"),
)

/**
 * Terms to search the series corpus for, given a raw user query.
 *
 * Always includes the query itself, so this only ever widens a search — a query that already
 * matched still matches. Returns lowercase-insensitive terms; callers do the matching.
 */
fun competitionAliasesFor(query: String): List<String> {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) return emptyList()
    val expansions = COMPETITION_ALIASES[normalized].orEmpty()
    return (listOf(query.trim()) + expansions).distinct()
}

private val NORMALIZED_ALIASES: Map<String, List<String>> =
    COMPETITION_ALIASES.entries.associate { (key, values) ->
        normalizeForSearch(key) to values.map(::normalizeForSearch)
    }

/**
 * [competitionAliasesFor] for an already-normalised query, expanded word by word as well as whole.
 *
 * The whole-query form only fired when the query *was* the acronym, so "ipl 2026" — how anyone
 * looks for a season — expanded to nothing and matched nothing. Here each word that is an acronym
 * is replaced in place ("indian premier 2026"), and the multi-word scorer matches that against
 * "indian premier league 2026". The query itself always comes first, so this only widens.
 */
fun expandedSearchQueries(normalizedQuery: String): List<String> {
    if (normalizedQuery.isEmpty()) return emptyList()
    val whole = NORMALIZED_ALIASES[normalizedQuery].orEmpty()
    val tokens = normalizedQuery.split(' ')
    val perToken = if (tokens.size < 2 || tokens.none { it in NORMALIZED_ALIASES }) {
        emptyList()
    } else {
        tokens.fold(listOf("")) { prefixes, token ->
            val options = listOf(token) + NORMALIZED_ALIASES[token].orEmpty()
            prefixes.flatMap { prefix -> options.map { "$prefix $it".trim() } }
        }
    }
    return (listOf(normalizedQuery) + whole + perToken).distinct()
}
