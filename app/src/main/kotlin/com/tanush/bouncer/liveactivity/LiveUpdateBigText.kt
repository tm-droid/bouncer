package com.tanush.bouncer.liveactivity

/**
 * The scoreboard flattened into one run of text, for the promotable `BigTextStyle` card.
 *
 * That template offers a single TextView — one size, one colour, left aligned — so this is as much
 * structure as a promoted Live Update can carry. The custom-layout card renders the same
 * [LiveUpdateScoreboard] into real views instead; both read from that one structure so a change to
 * what the card says lands on both.
 */

/**
 * Lines of [LiveUpdateContent.bigText].
 *
 * `BigTextStyle` gives no guarantee past a few lines and ellipsises the overflow rather than
 * scrolling it, so the block is built in descending order of value and truncated here. Losing the
 * status line is survivable; losing it silently mid-word is not.
 */
private const val BIG_TEXT_LINES = 6

internal fun bigTextOf(scoreboard: LiveUpdateScoreboard): String = listOfNotNull(
    sideLabel(scoreboard.opponentName, scoreboard.opponentScore),
    *scoreboard.batters.map(::spacedPlayerLine).toTypedArray(),
    scoreboard.bowler?.let(::spacedPlayerLine),
    recentBallsLine(scoreboard.recentBalls),
    scoreboard.statusLine,
).take(BIG_TEXT_LINES).joinToString("\n")

/** "Jan Nicol Loftie-Eaton 5 (7)*" — one line each, since there is a line to spare here. */
private fun spacedPlayerLine(line: LiveUpdateScoreboard.PlayerLine): String =
    "${line.name} ${line.figure}${if (line.isStriker) "*" else ""}"

/**
 * "Recent: W 1 4 1 0 0", or null when the payload carries no balls.
 *
 * The codes are passed through exactly as `parseRecentBallsNext` produced them. Their vocabulary is
 * not enumerated anywhere, so translating them here would mean guessing at what an unseen code
 * means and presenting the guess as a fact.
 */
private fun recentBallsLine(recentBalls: List<String>): String? =
    recentBalls.takeIf { it.isNotEmpty() }?.joinToString(" ", prefix = "Recent: ")

internal fun sideLabel(name: String?, score: String?): String? =
    listOf(name.orEmpty().trim(), score.orEmpty().trim())
        .filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ")
