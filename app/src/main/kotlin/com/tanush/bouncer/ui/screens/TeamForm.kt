package com.tanush.bouncer.ui.screens

import com.tanush.bouncer.models.CricketMatch

enum class FormResult(val letter: String) { WON("W"), LOST("L"), DRAWN("D"), TIED("T"), NO_RESULT("NR") }

private val WON_BY = Regex("""^(.+?)\s+won\b""", RegexOption.IGNORE_CASE)

/**
 * [team]'s result in a finished [match], read from Cricbuzz's own status line ("India won by 5
 * wkts", "Match drawn"). Null when the line names a winner that is neither side as the app spells
 * it — a short code, a sponsor name — because a guessed W/L is worse than a missing one.
 */
internal fun formResult(match: CricketMatch, team: String): FormResult? {
    if (!match.isCompleted || match.teams.none { sameTeamName(it.name, team) }) return null
    val status = match.currentStatus.ifBlank { match.status }.trim()
    val winner = WON_BY.find(status)?.groupValues?.get(1)?.trim() ?: return undecidedResult(status.lowercase())
    return when {
        sameTeamName(winner, team) -> FormResult.WON
        match.teams.any { sameTeamName(it.name, winner) } -> FormResult.LOST
        else -> null
    }
}

/**
 * Case, spacing and punctuation blind: Cricbuzz spells one side "KwaZulu-Natal Inland - Tuskers" in
 * the team row and "KwaZulu - Natal Inland - Tuskers" in the status line.
 */
internal fun sameTeamName(a: String, b: String): Boolean {
    fun key(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()
    return key(a).let { it.isNotEmpty() && it == key(b) }
}

private fun undecidedResult(lower: String): FormResult? = when {
    "drawn" in lower || lower.startsWith("draw") -> FormResult.DRAWN
    "tied" in lower -> FormResult.TIED
    "no result" in lower || "abandoned" in lower -> FormResult.NO_RESULT
    else -> null
}

/** [team]'s finished matches, newest first — `startDateMs` descending, undated ones last. */
internal fun resultsFor(matches: List<CricketMatch>, team: String): List<CricketMatch> =
    matches.filter { m -> m.isCompleted && m.teams.any { it.name.equals(team, ignoreCase = true) } }
        .distinctBy { it.id }
        .sortedByDescending { it.startDateMs ?: Long.MIN_VALUE }
