package com.tanush.bouncer.utils

/**
 * Team-name matching and abbreviation.
 *
 * Pure, no Android dependencies, so it can be unit-tested directly. Exists because three places
 * had grown their own answer to "is this string this team?" and none of them agreed.
 */

/** Which side a result string credits. */
enum class WinnerSide { TEAM1, TEAM2 }

/** Words that carry no identifying information and should not drive a match. */
private val NOISE_WORDS = setOf(
    "cricket", "team", "national", "the", "of", "and", "xi", "club", "county",
)

private val NON_ALPHANUMERIC = """[^\p{L}\p{N}]+""".toRegex()

/** Lowercased, punctuation-stripped, single-spaced. */
fun normalizeTeamName(name: String): String =
    name.replace(NON_ALPHANUMERIC, " ").trim().lowercase().replace("""\s+""".toRegex(), " ")

private fun significantWords(name: String): List<String> =
    normalizeTeamName(name).split(" ").filter { it.isNotBlank() && it !in NOISE_WORDS }

/**
 * Whole-word containment.
 *
 * Substring matching is what made "India" match "West Indies". Word boundaries are the whole
 * point: every word of [needle] must appear as a word in [haystack].
 */
private fun containsAsWords(haystack: String, needle: String): Boolean {
    val needleWords = significantWords(needle)
    if (needleWords.isEmpty()) return false
    val haystackWords = normalizeTeamName(haystack).split(" ").toSet()
    return needleWords.all { it in haystackWords }
}

/**
 * Abbreviation from significant-word initials, e.g. "Royal Challengers Bengaluru" -> "RCB".
 *
 * Replaces `substring(0, 3)`, which produced "ROY" for that team, gave both Sunrisers franchises
 * "SUN", and threw on any name shorter than three characters.
 */
fun acronymFor(teamName: String, maxLength: Int = 3): String {
    val words = significantWords(teamName)
    if (words.isEmpty()) return teamName.trim().uppercase().take(maxLength)
    // A single word has no initials to take, so use its opening letters.
    if (words.size == 1) return words[0].uppercase().take(maxLength)
    return words.mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").take(maxLength)
}

/**
 * Decide which of two teams a free-text result string credits, or null when it credits neither.
 *
 * Returns at most ONE side. The previous code counted the two sides with independent `contains`
 * calls, so a single match could increment both counters and the totals could exceed the number of
 * matches played — and "West Indies" counted as a win for "India".
 *
 * Falls back to comparing acronyms, because results are often written as "IND won by 5 wickets".
 * When both teams match, the longer (more specific) name wins: for India vs West Indies, only
 * "West Indies" contains both words, so it is the unambiguous answer.
 */
fun attributeWinner(winnerText: String?, team1: String, team2: String): WinnerSide? {
    if (winnerText.isNullOrBlank()) return null

    val team1Match = matchStrength(winnerText, team1)
    val team2Match = matchStrength(winnerText, team2)
    return when {
        team1Match > team2Match -> WinnerSide.TEAM1
        team2Match > team1Match -> WinnerSide.TEAM2
        else -> null // no match at all, or genuinely ambiguous
    }
}

/**
 * How specifically [text] identifies [team]: 0 for no match, otherwise the number of significant
 * words matched, so a longer name outranks a shorter one contained within it.
 */
private fun matchStrength(text: String, team: String): Int {
    if (team.isBlank()) return 0
    if (containsAsWords(text, team)) return significantWords(team).size.coerceAtLeast(1)
    val acronym = acronymFor(team)
    if (acronym.length >= 2 && containsAsWords(text, acronym)) return 1
    return 0
}
