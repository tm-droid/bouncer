package com.tanush.bouncer.network

import com.tanush.bouncer.models.Series

/**
 * Series classification from a competition name.
 *
 * Mirrors `appwrite/functions/poll-live-matches/src/series.js` — the server writes the same two
 * fields into the `active-series` bucket file, so the two must agree. That agreement is pinned by
 * `SeriesClassificationParityTest`, which reads the regexes out of the real JS file; the three
 * patterns below are `internal` rather than private so it can compare them one for one. A comment
 * saying two files must agree is not a check, which is the whole lesson of `llm.js`.
 */

// The apostrophe is optional: Cricbuzz emits both "Women's" and "Womens".
internal val WOMENS = """\bwomen(?:'?s)?\b|\bwpl\b""".toRegex(RegexOption.IGNORE_CASE)
// WPL is listed because "Women's Premier League" is often abbreviated past the word "League".
internal val LEAGUE =
    """\b(?:IPL|PSL|BBL|CPL|WPL)\b|League|T20""".toRegex(RegexOption.IGNORE_CASE)
internal val DOMESTIC = """Domestic|Ranji|Sheffield""".toRegex(RegexOption.IGNORE_CASE)

/**
 * Whether a series is women's cricket.
 *
 * This is INDEPENDENT of format. The category chain is first-match-wins, so testing Women after T20
 * made "ICC Women's T20 World Cup" a LEAGUE and left WOMEN all but unreachable — but reordering
 * would only move the error, stripping that competition of its T20 classification instead. Women's
 * cricket is not a format, so the two live on separate axes.
 */
internal fun isWomensSeries(name: String): Boolean = WOMENS.containsMatchIn(name)

/** Format/level classification. Deliberately says nothing about whether it is women's cricket. */
internal fun seriesCategoryOf(name: String): Series.SeriesCategory = when {
    LEAGUE.containsMatchIn(name) -> Series.SeriesCategory.LEAGUE
    DOMESTIC.containsMatchIn(name) -> Series.SeriesCategory.DOMESTIC
    else -> Series.SeriesCategory.INTERNATIONAL
}
