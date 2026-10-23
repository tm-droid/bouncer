package com.tanush.bouncer.data.history

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The historical-match site's files (`appwrite/tools/history`, schema 1). Keys are short because
 * the files are the payload; `.claude/rules/history-data.md` has the full layout.
 */
@Serializable
data class HistoryMatch(
    val v: Int,
    val id: String,
    val d: String? = null,
    val f: String? = null,
    val g: String = "male",
    val tt: String? = null,
    val t: List<String>,
    val e: HistoryEvent? = null,
    val ven: String? = null,
    val city: String? = null,
    val o: HistoryOutcome = HistoryOutcome(),
    /** Players of the match, as indexes into [p]. */
    val pom: List<Int> = emptyList(),
    /** Legal balls in an over; 6 unless the match file says otherwise (The Hundred is 5). */
    val bpo: Int = 6,
    val p: List<List<String>> = emptyList(),
    val xi: List<List<Int>> = emptyList(),
    val inn: List<HistoryInnings> = emptyList(),
    /** Weather on the day, `[tmax, rain, humidity, wind, cloud]` (see [PastWeather]); empty until fetched. */
    val w: List<Double> = emptyList(),
) {
    fun name(index: Int): String = p.getOrNull(index)?.firstOrNull() ?: "?"
}

/** The competition: name and stage ("Final"). */
@Serializable
data class HistoryEvent(
    val n: String? = null,
    val st: String? = null,
)

@Serializable
data class HistoryOutcome(
    val w: Int = -1,
    /** The margin, as Cricsheet gives it: `runs`, `wickets`, or `innings` with `runs`. */
    val by: Map<String, Int> = emptyMap(),
    val r: String? = null,
    val m: String? = null,
    /** The side that won the tie-break (super over or bowl-out), -1 when there was none. */
    val el: Int = -1,
)

@Serializable
data class HistoryInnings(
    val t: Int,
    val so: Int = 0,
    /** 1 when the innings was declared. */
    val dec: Int = 0,
    /** Deliveries: `[over, ball, batter, bowler, nonStriker, batRuns, extraRuns, bits, wickets?]`. */
    val b: List<JsonArray> = emptyList(),
    val sc: HistoryScorecard = HistoryScorecard(),
    /** Per over `[runs, wickets]`, including extras, in bowling order. */
    val ov: List<List<Int>> = emptyList(),
)

@Serializable
data class HistoryScorecard(
    /** `[person, runs, balls, fours, sixes, howOut-or-null]`. */
    val bat: List<JsonArray> = emptyList(),
    /** `[person, legalBalls, maidens, runs, wickets, wides, noBalls]`. */
    val bowl: List<List<Int>> = emptyList(),
    /** `[byes, legByes, wides, noBalls, penalty]`. */
    val ext: List<Int> = emptyList(),
    /** `[runs, wickets, legalBalls]`. */
    val tot: List<Int> = emptyList(),
    /** `[runs, wicketNumber, person, "over.ball"]`. */
    val fow: List<JsonArray> = emptyList(),
)

/** One delivery, decoded from its array. */
data class HistoryDelivery(
    val over: Int,
    val ball: Int,
    val batter: Int,
    val bowler: Int,
    val batRuns: Int,
    val extraRuns: Int,
    val bits: Int,
    /** Each wicket: kind and the dismissed person's index. */
    val wickets: List<Pair<String, Int>>,
) {
    val isWide: Boolean get() = bits and WIDE != 0
    val isNoBall: Boolean get() = bits and NOBALL != 0
    val isBye: Boolean get() = bits and BYE != 0
    val isLegBye: Boolean get() = bits and LEGBYE != 0
    val isNonBoundary: Boolean get() = bits and NON_BOUNDARY != 0

    companion object {
        // Bits of the extras field; `X` in appwrite/tools/history/src/match.js.
        const val WIDE = 1
        const val NOBALL = 2
        const val BYE = 4
        const val LEGBYE = 8
        const val NON_BOUNDARY = 32

        /** Null for an array too short to be a delivery: a malformed row is skipped, not guessed. */
        fun of(row: JsonArray): HistoryDelivery? {
            if (row.size < DELIVERY_FIELDS) return null
            val ints = (0 until DELIVERY_FIELDS).map { row[it].asInt() ?: return null }
            val wickets = (row.getOrNull(DELIVERY_FIELDS) as? JsonArray).orEmpty().mapNotNull { w ->
                val wicket = w as? JsonArray ?: return@mapNotNull null
                val kind = (wicket.getOrNull(0) as? JsonPrimitive)?.content ?: return@mapNotNull null
                val out = wicket.getOrNull(1)?.asInt() ?: return@mapNotNull null
                kind to out
            }
            return HistoryDelivery(
                over = ints[0],
                ball = ints[1],
                batter = ints[2],
                bowler = ints[3],
                batRuns = ints[5],
                extraRuns = ints[6],
                bits = ints[7],
                wickets = wickets,
            )
        }

        private const val DELIVERY_FIELDS = 8
    }
}

/**
 * One row of a month index: `[id, date, format, gender, team1, team2, winnerIdx, result, event, venue]`.
 * `idx/search.json` has the same rows without the venue.
 */
data class HistoryIndexRow(
    val id: String,
    val date: String,
    val format: String?,
    val gender: String,
    val team1: String,
    val team2: String,
    /** 0 or 1 for the winning side, -1 for none. */
    val winner: Int = -1,
    /** `tie`, `draw`, `no result`, or null when there was a winner. */
    val result: String? = null,
    val event: String? = null,
    val venue: String? = null,
) {
    companion object {
        fun of(row: JsonArray): HistoryIndexRow? {
            val text = { i: Int -> (row.getOrNull(i) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content }
            return HistoryIndexRow(
                id = text(0) ?: return null,
                date = text(1) ?: return null,
                format = text(2),
                gender = text(3) ?: "male",
                team1 = text(4) ?: return null,
                team2 = text(5) ?: return null,
                winner = text(6)?.toIntOrNull() ?: -1,
                result = text(7),
                event = text(8),
                venue = text(9),
            )
        }
    }
}

private fun JsonElement.asInt(): Int? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.intOrNull
