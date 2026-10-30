package com.tanush.bouncer.network

import com.google.gson.JsonObject
import com.tanush.bouncer.managers.TeamAcronymManager
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch

/**
 * The team fields the match list reads out of Cricbuzz's flight payload: the score of a side that
 * has batted more than once, its id, and the assembled team.
 *
 * Its own file because CricBuzzScraper.kt is at detekt's file-level TooManyFunctions threshold, and
 * because `match-sync/src/cricbuzz.js` holds the same rule for the detail payload — the two are
 * compared by MultiInningsScoreParityTest.
 */

/** A side bats at most twice, and Cricbuzz keys the two innings on that side's score object. */
private val INNINGS_KEYS = listOf("inngs1", "inngs2")

/** Ten wickets ends an innings, so a completed innings is written as runs alone in a Test. */
private const val ALL_OUT_WICKETS = 10

/** The formats in which a side bats twice — the only ones whose score reads "266 & 83/2". */
private val MULTI_DAY_FORMATS = setOf("TEST", "FC")

/** Cricbuzz's own `matchFormat` code, so a null or an unknown code is treated as limited-overs. */
internal fun isMultiDayFormat(matchFormat: String?): Boolean =
    matchFormat?.uppercase() in MULTI_DAY_FORMATS

/** One side's score across every innings it has batted, plus the overs of its newest innings. */
internal data class TeamInningsScore(val score: String, val overs: String) {
    companion object {
        val EMPTY = TeamInningsScore("", "")
    }
}

/**
 * "266 & 83/2" — every innings this side has batted, oldest first.
 *
 * Cricbuzz's `matchScore.team1Score` object holds `inngs1` and `inngs2`; reading only the newer one
 * rendered a Test's "266 & 83-2" as "83/2" on every card, header, widget and notification, because
 * the first innings was dropped before it ever reached the model.
 *
 * A completed innings drops its wicket count in the multi-day formats only, which is how Cricbuzz
 * and Cricinfo both write a Test score; limited-overs scores keep the shape they have always had.
 */
internal fun teamInningsScore(scoreObj: JsonObject, key: String, multiDay: Boolean): TeamInningsScore {
    if (!scoreObj.has(key)) return TeamInningsScore.EMPTY
    val side = scoreObj.getAsJsonObject(key) ?: return TeamInningsScore.EMPTY
    val innings = INNINGS_KEYS.mapNotNull { k -> if (side.has(k)) side.getAsJsonObject(k) else null }
    val score = innings.mapNotNull { inn ->
        val runs = inn.get("runs")?.asString ?: return@mapNotNull null
        val wickets = inn.get("wickets")?.asString?.toIntOrNull() ?: 0
        if (multiDay && wickets >= ALL_OUT_WICKETS) runs else "$runs/$wickets"
    }.joinToString(" & ")
    return TeamInningsScore(score, innings.lastOrNull()?.get("overs")?.asString ?: "")
}

/**
 * Cricbuzz's id for one side of a fixture, or "" when the payload does not name it.
 *
 * Carried on `CricketMatch.Team` because the live detail payload is oriented by team id — its team
 * names are short codes ("EZONE", "RSAA") that match no full name. See MatchLiveDetailAlignment.
 */
internal fun teamIdOf(info: JsonObject, key: String): String =
    info.getAsJsonObject(key)?.get("teamId")?.asString ?: ""

/**
 * Cricbuzz's image id for one side of a fixture, or "" when the payload does not carry one.
 *
 * `matchInfo.team1/2` carries `imageId` alongside `teamId` and `teamName` on the live, upcoming and
 * recent lists alike. It was read off the wire and dropped for as long as the parser existed, so
 * every crest came from the bundled map instead — which is why a team Cricbuzz added after the map
 * was generated showed no crest in the app while showing one on Cricbuzz.
 */
internal fun teamImageIdOf(info: JsonObject, key: String): String =
    info.getAsJsonObject(key)?.get("imageId")?.asString ?: ""

/**
 * The crest for this side: Cricbuzz's own image id where the payload carries one, the bundled map
 * otherwise.
 *
 * The payload wins because the map is a snapshot. It cannot hold a team added since it was built,
 * and it points at a stale image for any team whose id has changed since — two failures that look
 * identical on screen and neither of which the map can fix on its own.
 */
private fun teamLogoUrl(name: String, imageId: String): String? =
    imageId.takeIf { it.isNotBlank() }?.let { cricbuzzTeamLogoUrl(it) }
        ?: TeamLogoManager.getLogoUrl(name)

/** One side of a fixture as the app models it, with the branding the managers hold for its name. */
internal fun cricketTeam(
    name: String,
    id: String,
    score: String,
    overs: String,
    isBatting: Boolean,
    imageId: String = "",
): CricketMatch.Team = CricketMatch.Team(
    name, score, "", isBatting, teamLogoUrl(name, imageId),
    TeamAcronymManager.getAcronym(name), overs, id,
)
