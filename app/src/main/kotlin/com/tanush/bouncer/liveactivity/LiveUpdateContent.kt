package com.tanush.bouncer.liveactivity

import com.tanush.bouncer.data.remote.LiveSituation
import com.tanush.bouncer.data.remote.MatchLiveDetailPayload
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.MatchStatusClassifier
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import com.tanush.bouncer.models.StatusSignal

/**
 * Everything a Live Update renders, as plain data.
 *
 * A promoted notification may not use `RemoteViews`, so there is no layout to bind — the whole
 * surface is a handful of strings. Keeping that derivation here, pure and Android-free, is what
 * makes it testable: this project has no Robolectric, so a test can never assert on a real
 * `Notification`.
 *
 * The shape follows `BigTextStyle`, which is the richest template promotion allows. [title] and
 * [text] are the collapsed card; [bigText] is the expanded one and is the only place with room for
 * a scoreboard.
 *
 * The payload's `team1`/`team2` slots are oriented to the **first-innings batting side**, which is
 * the order a scoreboard wants anyway, so nothing here re-orients them. Alignment onto a screen's
 * `teams` order (`MatchLiveDetailAlignment`) exists because that screen has a fixed team order to
 * honour; a notification has none.
 */
data class LiveUpdateContent(
    val matchId: String,
    /** Small header line beside the app name: series and format. */
    val subText: String?,
    /** The batting side's score — the one number the card exists for. */
    val title: String,
    /** Collapsed second line: the batting pair, or the bowler, or the status. */
    val text: String,
    /** Expanded body, newline separated: opposition total, batters, bowler, recent balls, status. */
    val bigText: String,
    /** Status-bar chip text. Null when there is no score short enough to be worth a chip. */
    val shortCriticalText: String?,
    /** Team whose brand colour tints the notification. Blank when unknown. */
    val accentTeamName: String,
    /** Team whose logo becomes the large icon, or null when it cannot be resolved. */
    val logoTeamName: String?,
    val isComplete: Boolean,
)

/**
 * The platform suggests a maximum of 7 characters for the status-bar chip and gives no guarantee
 * about how much of a longer string survives. A score that would be cut is not worth showing at
 * all — a truncated "234/1" reading "234/" is worse than the icon alone.
 */
private const val SHORT_CRITICAL_MAX = 7

/**
 * Character budget for the collapsed secondary line. The bowler's figures are appended only when
 * the pair plus the bowler fits inside it, so the line is either complete or deliberately shorter —
 * never silently ellipsised by the platform mid-name.
 */
private const val TEXT_BUDGET = 48

internal const val PART_SEPARATOR = " · "

/** Wider than [PART_SEPARATOR]: it separates two whole sides rather than two facts about one. */
private const val SIDE_SEPARATOR = "  ·  "

/**
 * Build the Live Update for one match.
 *
 * [match] is the cached list entry and may be null: a subscription can outlive the cache. Since the
 * payload gained [LiveSituation] that costs far less than it used to — the format and the batting
 * side both come off the payload now, and only the series name still depends on the cache.
 */
fun buildLiveUpdateContent(
    matchId: String,
    match: CricketMatch?,
    payload: MatchLiveDetailPayload,
): LiveUpdateContent {
    val battingSlot = battingSlot(payload)
    val liveScore = liveInningsScore(payload.score)

    return LiveUpdateContent(
        matchId = matchId,
        subText = subText(match, payload.situation),
        title = scoreTitle(payload, battingSlot),
        text = secondaryLine(payload),
        bigText = bigText(payload, battingSlot),
        shortCriticalText = liveScore?.takeIf { it.length <= SHORT_CRITICAL_MAX },
        accentTeamName = battingSlot?.let { slotName(payload, it) }
            ?: payload.situation?.battingTeamName?.takeIf { it.isNotBlank() }
            ?: match?.teams?.firstOrNull()?.name
            ?: "",
        logoTeamName = battingSlot?.let { slot -> matchTeamNameForSlot(payload, match, slot) },
        isComplete = isComplete(match, payload.status),
    )
}

/**
 * "The Ashes · Test", or whichever half of it is known.
 *
 * The format half falls back to the payload's own word so the line survives a cold cache — which,
 * until the `"live"`/`"Live"` key was fixed, was every single build of the card.
 */
private fun subText(match: CricketMatch?, situation: LiveSituation?): String? = listOfNotNull(
    match?.matchTitle?.takeIf { it.isNotBlank() },
    match?.matchType?.takeIf { it.isNotBlank() } ?: formatLabel(situation?.matchFormat),
).takeIf { it.isNotEmpty() }?.joinToString(PART_SEPARATOR)

/** "TEST" -> "Test". Cricbuzz shouts its format words; a header line should not. */
private fun formatLabel(matchFormat: String?): String? = matchFormat?.trim()?.takeIf { it.isNotEmpty() }?.let {
    if (it.length <= SHORT_FORMAT_MAX) it.uppercase() else it.lowercase().replaceFirstChar(Char::uppercase)
}

/** "ODI", "T20" and "T10" read as codes; "TEST" and "HUNDRED" read as words. */
private const val SHORT_FORMAT_MAX = 3

/**
 * "AUS 266 & 234/1 (56.4)" — the side batting, its whole contribution, and the over it is on.
 *
 * The opposition used to share this line, which left no room for the over figure and made the
 * headline number whichever side happened to be `team1`. It now has its own line in the expanded
 * block, where a scoreboard belongs.
 */
private fun scoreTitle(payload: MatchLiveDetailPayload, battingSlot: Int?): String =
    battingSideTitle(payload, battingSlot) ?: fallbackTitle(payload)

/**
 * The **side aggregate** is used rather than `score`, because a Test side's score is two innings
 * and `score` is only the one being batted. Writing "AUS 234/1" for a side that also made 266 is
 * the same class of bug `teamInningsScore` exists to prevent.
 */
private fun battingSideTitle(payload: MatchLiveDetailPayload, battingSlot: Int?): String? {
    val aggregate = battingSlot?.let { slotScore(payload, it) }
        ?: liveInningsScore(payload.score)
        ?: return null
    val name = battingSlot?.let { slotName(payload, it) }
        ?: payload.situation?.battingTeamName?.takeIf { it.isNotBlank() }
    val overs = liveOvers(payload.score)?.let { " ($it)" }.orEmpty()
    return listOfNotNull(name, aggregate).joinToString(" ") + overs
}

/** No readable score. A promoted notification with an empty title is worse than a vague one. */
private fun fallbackTitle(payload: MatchLiveDetailPayload): String = listOfNotNull(
    sideLabel(payload.team1Name, payload.team1Score),
    sideLabel(payload.team2Name, payload.team2Score),
).takeIf { it.isNotEmpty() }?.joinToString(SIDE_SEPARATOR)
    ?: payload.status?.trim()?.takeIf { it.isNotEmpty() }
    ?: ""

/** "Warner 95(159)*" — the asterisk is the scorecard's own mark for the striker. */
internal fun batsmanLabel(batsman: ScrapedBatsman): String {
    val striker = if (batsman.isStriker) "*" else ""
    return "${batsman.name} ${batsman.runs}(${batsman.balls})$striker"
}

/** "Cummins 2/45" — wickets first, as figures are always quoted. */
internal fun bowlerLabel(bowler: ScrapedBowler): String = "${bowler.name} ${bowler.wickets}/${bowler.runs}"

/**
 * The batting pair, with the bowler appended when both fit; otherwise the bowler alone, or the
 * status. An innings break has no batsmen, and a pre-toss page has neither.
 */
private fun secondaryLine(payload: MatchLiveDetailPayload): String {
    val batting = payload.batsmen.sortedByDescending { it.isStriker }.map(::batsmanLabel)
    val bowling = payload.bowler?.let(::bowlerLabel)
    if (batting.isEmpty()) return bowling ?: payload.status?.trim().orEmpty()

    val pair = batting.joinToString(PART_SEPARATOR)
    if (bowling == null) return pair
    val withBowler = pair + PART_SEPARATOR + bowling
    return if (withBowler.length <= TEXT_BUDGET) withBowler else pair
}

/**
 * A match is over when the list snapshot says so or the live status prose does.
 *
 * `match.isLive` is not consulted: the cached list entry is a snapshot that goes stale, and the
 * live status is the fresher of the two signals.
 */
private fun isComplete(match: CricketMatch?, liveStatus: String?): Boolean =
    match?.isCompleted == true || MatchStatusClassifier.classify(liveStatus) == StatusSignal.TERMINAL
