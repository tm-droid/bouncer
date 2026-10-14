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
    val ven: String? = null,
    val city: String? = null,
    val o: HistoryOutcome = HistoryOutcome(),
    val p: List<List<String>> = emptyList(),
    val xi: List<List<Int>> = emptyList(),
    val inn: List<HistoryInnings> = emptyList(),
) {
    fun name(index: Int): String = p.getOrNull(index)?.firstOrNull() ?: "?"
}

@Serializable
data class HistoryOutcome(
    val w: Int = -1,
    val r: String? = null,
    val m: String? = null,
)

@Serializable
data class HistoryInnings(
    val t: Int,
    val so: Int = 0,
    /** Deliveries: `[over, ball, batter, bowler, nonStriker, batRuns, extraRuns, bits, wickets?]`. */
    val b: List<JsonArray> = emptyList(),
    val sc: HistoryScorecard = HistoryScorecard(),
)

@Serializable
data class HistoryScorecard(
    /** `[person, runs, balls, fours, sixes, howOut-or-null]`. */
    val bat: List<JsonArray> = emptyList(),
    /** `[runs, wickets, legalBalls]`. */
    val tot: List<Int> = emptyList(),
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

/** One row of a month index: `[id, date, format, gender, team1, team2, winnerIdx, result, event, venue]`. */
data class HistoryIndexRow(
    val id: String,
    val date: String,
    val format: String?,
    val gender: String,
    val team1: String,
    val team2: String,
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
            )
        }
    }
}

private fun JsonElement.asInt(): Int? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.intOrNull
