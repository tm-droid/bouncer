package com.tanush.bouncer.network

import com.google.gson.JsonObject

/**
 * How a side's score is written when it has batted more than once.
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
