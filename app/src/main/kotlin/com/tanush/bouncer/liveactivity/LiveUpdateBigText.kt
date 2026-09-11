package com.tanush.bouncer.liveactivity

import com.tanush.bouncer.data.remote.LiveSituation
import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler

/**
 * The expanded half of a Live Update: the scoreboard `BigTextStyle` has room for and the collapsed
 * card does not.
 *
 * Separate from [LiveUpdateContent] only because that file owns the collapsed surfaces and their
 * budgets; the two are built from the same payload in the same pass.
 */

/**
 * Lines of [LiveUpdateContent.bigText].
 *
 * `BigTextStyle` gives no guarantee past a few lines and ellipsises the overflow rather than
 * scrolling it, so the block is built in descending order of value and truncated here. Losing the
 * status line is survivable; losing it silently mid-word is not.
 */
private const val BIG_TEXT_LINES = 6

/** How many of `recentBalls` to show. One over's worth, which is what the references show. */
private const val RECENT_BALLS = 6

/** Cricbuzz writes a session prefix onto the status: "Day 4: 1st Session - Kent need 371 runs". */
private const val STATUS_PREFIX_SEPARATOR = " - "

/**
 * The expanded card: a scoreboard, in descending order of what a follower wants next.
 *
 * Capped at [BIG_TEXT_LINES] from the bottom, so the lines that survive a short template are the
 * opposition total and the batters rather than whatever happened to be parsed last.
 */
internal fun bigText(payload: MatchLiveDetailPayload, battingSlot: Int?): String = listOfNotNull(
    oppositionLine(payload, battingSlot),
    *battingLines(payload).toTypedArray(),
    payload.bowler?.let(::bowlerSpellLabel),
    recentBallsLine(payload.recentBalls),
    statusLine(payload),
).take(BIG_TEXT_LINES).joinToString("\n")

/** "ENG 348" — the side not batting, with both its innings when it has two. */
private fun oppositionLine(payload: MatchLiveDetailPayload, battingSlot: Int?): String? {
    val slot = battingSlot ?: return null
    val other = if (slot == 0) 1 else 0
    return sideLabel(slotName(payload, other), slotScore(payload, other))
}

/** One line per batter, striker first, as the references show them. */
private fun battingLines(payload: MatchLiveDetailPayload): List<String> =
    payload.batsmen.sortedByDescending { it.isStriker }.map(::batsmanSpacedLabel)

/**
 * "Chris Benjamin 7 (19)*" — the expanded form, with the balls faced spaced off the runs.
 *
 * The collapsed line uses [batsmanLabel]'s tighter "7(19)*" because it has to fit two batters and a
 * bowler into one row; here there is a line each.
 */
private fun batsmanSpacedLabel(batsman: ScrapedBatsman): String {
    val striker = if (batsman.isStriker) "*" else ""
    return "${batsman.name} ${batsman.runs} (${batsman.balls})$striker"
}

/** "Jack Morley 0/3 (1.4)" — the same figures plus the spell, which the collapsed line has no room for. */
private fun bowlerSpellLabel(bowler: ScrapedBowler): String {
    val overs = bowler.overs.trim().takeIf { it.isNotEmpty() } ?: return bowlerLabel(bowler)
    return "${bowlerLabel(bowler)} ($overs)"
}

/**
 * "Recent: B4 0 0 1 2 1", or null when the payload carries no balls.
 *
 * The codes are passed through exactly as `parseRecentBallsNext` produced them. Their vocabulary is
 * not enumerated anywhere, so translating them here would mean guessing at what an unseen code
 * means and presenting the guess as a fact.
 */
private fun recentBallsLine(recentBalls: List<String>): String? = recentBalls
    .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
    .takeIf { it.isNotEmpty() }
    ?.takeLast(RECENT_BALLS)
    ?.joinToString(" ", prefix = "Recent: ")

/**
 * The one line that says where the match actually stands.
 *
 * A chase is stated from [LiveSituation]'s numbers, which are exact. Otherwise Cricbuzz's own prose
 * is used with its session prefix stripped: "Day 4: 1st Session - Kent need 371 runs" is two facts,
 * and only the second belongs on a card this short. `substringAfterLast` returns the whole string
 * when the separator is absent, so "Innings Break" passes through untouched.
 */
private fun statusLine(payload: MatchLiveDetailPayload): String? {
    chaseLine(payload.situation)?.let { return it }
    return payload.status?.trim()
        ?.substringAfterLast(STATUS_PREFIX_SEPARATOR)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

/** "Need 66 runs from 10.3 overs · RRR 6.60", from the numbers rather than from prose. */
private fun chaseLine(situation: LiveSituation?): String? {
    val needed = situation?.runsNeeded?.takeIf { it > 0 } ?: return null
    val overs = situation.oversRemaining?.takeIf { it > 0.0 }?.let { "from ${trimOvers(it)} overs" }
    val rate = situation.requiredRunRate?.takeIf { it > 0.0 }?.let { "RRR ${"%.2f".format(it)}" }
    val head = listOfNotNull("Need $needed runs", overs).joinToString(" ")
    return listOfNotNull(head, rate).joinToString(PART_SEPARATOR)
}

/** 10.0 -> "10", 10.3 -> "10.3". An over figure with a trailing ".0" reads like a decimal. */
private fun trimOvers(overs: Double): String =
    if (overs % 1.0 == 0.0) overs.toInt().toString() else overs.toString()


internal fun sideLabel(name: String?, score: String?): String? =
    listOf(name.orEmpty().trim(), score.orEmpty().trim())
        .filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ")
