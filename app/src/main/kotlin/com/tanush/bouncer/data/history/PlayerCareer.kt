package com.tanush.bouncer.data.history

import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.models.FormatStats
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate

/** One player in `s/p/names/<letter>.json`: `["V Kohli", regId, team, matches]`. */
data class HistoryNameRow(val name: String, val id: String, val team: String, val matches: Int)

/** One entry of a player's `m` list: `[id, date, format, team, opponent, ...figures]`, newest first. */
data class CareerMatch(val id: String, val date: LocalDate?, val format: String, val team: String, val opponent: String) {
    /** A list row from the player's record alone, for a match the search index no longer holds (an archive row replaced it). */
    fun toRow(): PastMatchRow = PastMatchRow(
        id = id, date = date, format = format, women = false, team1 = team, team2 = opponent,
        winner = -1, result = null, event = null, venue = null, source = PastSource.CRICSHEET,
    )
}

/** A player's Cricsheet record (`s/p/<regId>.json`): figures by format, sides played for, and every match. */
data class PlayerCareer(
    val name: String,
    val formats: Map<String, FormatStats>,
    /** Side to matches played for it, most first. */
    val teams: List<Pair<String, Int>>,
    val matches: List<CareerMatch>,
    /** Whether the record carries 4s, 6s and five-wicket hauls; one built before they were added does not. */
    val hasBoundaries: Boolean = false,
)

/** Turns the site's compact player records into the models the player page already renders. */
object CareerStats {
    // Columns of a format's integers (`history-data.md`): 16, then 4 more on records built with boundary counts.
    private const val MATCHES = 0
    private const val INNINGS = 1
    private const val NOT_OUTS = 2
    private const val RUNS = 3
    private const val BALLS = 4
    private const val HIGH_SCORE = 5
    private const val HIGH_SCORE_NOT_OUT = 6
    private const val FIFTIES = 7
    private const val HUNDREDS = 8
    private const val BALLS_BOWLED = 9
    private const val RUNS_CONCEDED = 10
    private const val WICKETS = 11
    private const val BEST_WICKETS = 12
    private const val BEST_RUNS = 13
    private const val CATCHES = 14
    private const val FOURS = 16
    private const val SIXES = 17
    private const val FIVE_WICKETS = 19
    private const val COLUMNS = 16
    private const val COLUMNS_WITH_BOUNDARIES = 20
    private const val MATCH_COLUMNS = 5
    private const val BALLS_PER_OVER = 6.0
    private const val PERCENT = 100.0

    /**
     * A record built before the site counted boundaries has only 16 columns, so `fours`, `sixes` and
     * `fiveWickets` stay 0 and the page is told not to show them (`hasBoundaries = false`).
     */
    fun formatStats(c: List<Int>): FormatStats? {
        if (c.size < COLUMNS) return null
        val dismissals = c[INNINGS] - c[NOT_OUTS]
        val bowled = c[BALLS_BOWLED]
        return FormatStats(
            matches = c[MATCHES],
            innings = c[INNINGS],
            runs = c[RUNS],
            highScore = c[HIGH_SCORE].toString() + if (c[HIGH_SCORE_NOT_OUT] == 1) "*" else "",
            average = if (dismissals > 0) c[RUNS].toDouble() / dismissals else 0.0,
            strikeRate = if (c[BALLS] > 0) c[RUNS] * PERCENT / c[BALLS] else 0.0,
            hundreds = c[HUNDREDS],
            fifties = c[FIFTIES],
            fours = c.getOrElse(FOURS) { 0 },
            sixes = c.getOrElse(SIXES) { 0 },
            fiveWickets = c.getOrElse(FIVE_WICKETS) { 0 },
            wickets = c[WICKETS],
            bowlingAverage = if (c[WICKETS] > 0) c[RUNS_CONCEDED].toDouble() / c[WICKETS] else 0.0,
            economy = if (bowled > 0) c[RUNS_CONCEDED] / (bowled / BALLS_PER_OVER) else 0.0,
            bestBowling = if (c[BEST_WICKETS] > 0) "${c[BEST_WICKETS]}/${c[BEST_RUNS]}" else "-",
            catches = c[CATCHES],
        )
    }

    fun parseCareer(json: String): PlayerCareer? = runCatching {
        val root = AppJson.parseToJsonElement(json) as JsonObject
        val columns = (root["f"] as? JsonObject).orEmpty().mapValues { (_, cells) ->
            (cells as JsonArray).map { (it as JsonPrimitive).intOrNull ?: 0 }
        }
        val formats = columns.mapNotNull { (format, c) -> formatStats(c)?.let { format to it } }.toMap()
        val hasBoundaries = columns.isNotEmpty() && columns.values.all { it.size >= COLUMNS_WITH_BOUNDARIES }
        val teams = (root["tm"] as? JsonObject).orEmpty()
            .map { (team, count) -> team to ((count as? JsonPrimitive)?.intOrNull ?: 0) }
            .sortedByDescending { it.second }
        val matches = (root["m"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonArray)?.let(::careerMatch) }
        PlayerCareer((root["n"] as JsonPrimitive).content, formats, teams, matches, hasBoundaries)
    }.getOrNull()

    fun parseNames(json: String): List<HistoryNameRow>? = runCatching {
        (AppJson.parseToJsonElement(json) as JsonArray).mapNotNull { row ->
            val cells = (row as? JsonArray)?.map { (it as JsonPrimitive).content } ?: return@mapNotNull null
            if (cells.size < 4) return@mapNotNull null
            HistoryNameRow(cells[0], cells[1], cells[2], cells[3].toIntOrNull() ?: 0)
        }
    }.getOrNull()

    private fun careerMatch(row: JsonArray): CareerMatch? {
        if (row.size < MATCH_COLUMNS) return null
        val text = { i: Int -> (row.getOrNull(i) as? JsonPrimitive)?.content }
        return CareerMatch(
            id = text(0) ?: return null,
            date = text(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            format = text(2).orEmpty(),
            team = text(3).orEmpty(),
            opponent = text(4).orEmpty(),
        )
    }
}
