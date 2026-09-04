package com.tanush.bouncer.liveactivity

import com.tanush.bouncer.analysis.CricketConstants
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
 * surface is a handful of strings plus [LiveUpdateProgress]. Keeping that derivation here, pure
 * and Android-free, is what makes it testable: this project has no Robolectric, so a test can
 * never assert on a real `Notification`.
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
    /** Both sides' scores, innings order. */
    val title: String,
    /** Batting pair, or the bowler, or the status — whichever is the most specific thing known. */
    val text: String,
    /** Status-bar chip text. Null when there is no score short enough to be worth a chip. */
    val shortCriticalText: String?,
    val progress: LiveUpdateProgress,
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
 * Character budget for the secondary line. The bowler's figures are appended only when the pair
 * plus the bowler fits inside it, so the line is either complete or deliberately shorter — never
 * silently ellipsised by the platform mid-name.
 */
private const val TEXT_BUDGET = 48

private const val SIDE_SEPARATOR = "  ·  "
private const val PART_SEPARATOR = " · "

/**
 * Build the Live Update for one match.
 *
 * [match] is the cached list entry and may be null: a subscription can outlive the cache, and a
 * notification built from the payload alone is still correct, just without the series name and
 * with an unknown format (which the progress bar handles by falling back to wickets).
 */
fun buildLiveUpdateContent(
    matchId: String,
    match: CricketMatch?,
    payload: MatchLiveDetailPayload,
): LiveUpdateContent {
    val battingSlot = battingSlot(payload)
    val format = CricketConstants.formatFromMatchType(match?.matchType, match?.matchTitle, payload.status)
    val liveScore = liveInningsScore(payload.score)

    return LiveUpdateContent(
        matchId = matchId,
        subText = subText(match),
        title = scoreTitle(payload),
        text = secondaryLine(payload),
        shortCriticalText = liveScore?.takeIf { it.length <= SHORT_CRITICAL_MAX },
        progress = progressFor(
            format = format,
            overs = liveOvers(payload.score) ?: battingSlot?.let { slotOvers(payload, it) },
            wickets = liveScore?.let(::wicketsIn),
            fallOfWicketOvers = currentInningsFallOfWicketOvers(payload),
        ),
        accentTeamName = battingSlot?.let { slotName(payload, it) }
            ?: match?.teams?.firstOrNull()?.name
            ?: "",
        logoTeamName = battingSlot?.let { slot -> matchTeamNameForSlot(payload, match, slot) },
        isComplete = isComplete(match, payload.status),
    )
}

/** "The Ashes · Test", or whichever half of it is known. */
private fun subText(match: CricketMatch?): String? = listOfNotNull(
    match?.matchTitle?.takeIf { it.isNotBlank() },
    match?.matchType?.takeIf { it.isNotBlank() },
).takeIf { it.isNotEmpty() }?.joinToString(PART_SEPARATOR)

/**
 * "ENG 348  ·  AUS 266 & 234/1" — both sides, innings order, whatever the payload actually has.
 *
 * Falls back to the live score line and then to the status, because a promoted notification with
 * an empty title is worse than a vague one.
 */
private fun scoreTitle(payload: MatchLiveDetailPayload): String {
    val sides = listOfNotNull(
        sideLabel(payload.team1Name, payload.team1Score),
        sideLabel(payload.team2Name, payload.team2Score),
    )
    if (sides.isNotEmpty()) return sides.joinToString(SIDE_SEPARATOR)
    return payload.score?.takeIf { it.isNotBlank() }
        ?: payload.status?.takeIf { it.isNotBlank() }
        ?: ""
}

private fun sideLabel(name: String?, score: String?): String? =
    listOf(name.orEmpty().trim(), score.orEmpty().trim())
        .filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ")

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

/** "Warner 95(159)*" — the asterisk is the scorecard's own mark for the striker. */
private fun batsmanLabel(batsman: ScrapedBatsman): String {
    val striker = if (batsman.isStriker) "*" else ""
    return "${batsman.name} ${batsman.runs}(${batsman.balls})$striker"
}

/** "Cummins 2/45" — wickets first, as figures are always quoted. */
private fun bowlerLabel(bowler: ScrapedBowler): String = "${bowler.name} ${bowler.wickets}/${bowler.runs}"

/**
 * A match is over when the list snapshot says so or the live status prose does.
 *
 * `match.isLive` is not consulted: the cached list entry is a snapshot that goes stale, and the
 * live status is the fresher of the two signals.
 */
private fun isComplete(match: CricketMatch?, liveStatus: String?): Boolean =
    match?.isCompleted == true || MatchStatusClassifier.classify(liveStatus) == StatusSignal.TERMINAL
