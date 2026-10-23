package com.tanush.bouncer.data.history

import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.MatchStatusClassifier
import com.tanush.bouncer.models.StatusSignal
import com.tanush.bouncer.network.MatchStartTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.ZoneOffset

/** Which static site a past match came from. */
enum class PastSource {
    /** Bouncer's own archive: keyed by the Cricbuzz id, with its commentary. */
    ARCHIVE,

    /** Cricsheet: every international and major league match since 2003. */
    CRICSHEET,
}

/** Marks a [CricketMatch] that exists only on the Cricsheet site: no Appwrite row, no Cricbuzz page. */
const val CRICSHEET_PREFIX = "cs-"

val CricketMatch.isCricsheetOnly: Boolean get() = id.startsWith(CRICSHEET_PREFIX)

/**
 * One finished match from either site's index, in one shape for search and browsing.
 *
 * [result] is Cricsheet's `tie`/`draw`/`no result` (null with a winner) for [PastSource.CRICSHEET],
 * and Cricbuzz's status text ("Qatar won by 5 wkts") for [PastSource.ARCHIVE].
 */
data class PastMatchRow(
    val id: String,
    val date: LocalDate?,
    val format: String?,
    val women: Boolean,
    val team1: String,
    val team2: String,
    /** 0 or 1 for the winning side of a Cricsheet match, otherwise -1. */
    val winner: Int,
    val result: String?,
    val event: String?,
    val venue: String?,
    val source: PastSource,
) {
    /** The match as the rest of the app knows one; always finished, so it never reaches a live path. */
    fun toCricketMatch(): CricketMatch {
        val startMs = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val status = statusText()
        val competition = if (isInternationalFormat(format)) "International" else "League"
        return CricketMatch(
            id = if (source == PastSource.CRICSHEET) CRICSHEET_PREFIX + id else id,
            seriesId = "",
            matchTitle = "$team1 vs $team2",
            matchType = matchTypeOf(format),
            status = status,
            teams = listOf(team1, team2).map { name ->
                CricketMatch.Team(
                    name = name,
                    score = "",
                    status = "",
                    isBatting = false,
                    logoUrl = TeamLogoManager.getLogoUrl(name),
                    overs = "",
                )
            },
            venue = venue.orEmpty(),
            date = startMs?.let { MatchStartTime.of(it, ZoneOffset.UTC).date }.orEmpty(),
            matchTime = "",
            score = null,
            currentStatus = status,
            info = "Complete",
            category = competition,
            gender = if (women) "Women" else "Men",
            competitionType = competition,
            seriesName = event.orEmpty(),
            startDateMs = startMs,
        )
    }

    /** Terminal for the status classifier whatever the index says: a row here is a finished match. */
    private fun statusText(): String = when (source) {
        PastSource.CRICSHEET -> cricsheetStatus()
        PastSource.ARCHIVE -> result?.takeIf { MatchStatusClassifier.classify(it) == StatusSignal.TERMINAL } ?: COMPLETE
    }

    private fun cricsheetStatus(): String = when {
        winner == 0 -> "$team1 won the match"
        winner == 1 -> "$team2 won the match"
        result == "tie" -> "Match tied"
        result == "draw" -> "Match drawn"
        result == "no result" -> "No result"
        else -> COMPLETE
    }

    companion object {
        private const val COMPLETE = "Match complete"

        fun of(row: HistoryIndexRow): PastMatchRow = PastMatchRow(
            id = row.id,
            date = runCatching { LocalDate.parse(row.date) }.getOrNull(),
            format = row.format,
            women = row.gender == "female",
            team1 = row.team1,
            team2 = row.team2,
            winner = row.winner,
            result = row.result,
            event = row.event,
            venue = row.venue,
            source = PastSource.CRICSHEET,
        )

        /** The archive's `[id, date, format, gender, team1, team2, result, series, venue]`. */
        fun ofArchive(row: JsonArray): PastMatchRow? {
            val text = { i: Int -> (row.getOrNull(i) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content }
            return PastMatchRow(
                id = text(0) ?: return null,
                date = text(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                format = text(2),
                women = text(3) == "female",
                team1 = text(4) ?: return null,
                team2 = text(5) ?: return null,
                winner = -1,
                result = text(6),
                event = text(7),
                venue = text(8),
                source = PastSource.ARCHIVE,
            )
        }
    }
}

// Cricsheet's `IT20` is the older tag for a T20 international; `T20` covers both kinds since.
private fun isInternationalFormat(format: String?): Boolean =
    format?.uppercase() in setOf("TEST", "ODI", "IT20", "T20I")

/** The names the matches list uses (`CricBuzzScraper.mapMatchFormat`), for Cricsheet's and the archive's tags. */
private fun matchTypeOf(format: String?): String = when (format?.uppercase()) {
    "TEST" -> "Test"
    "ODI" -> "ODI"
    "T20", "IT20", "T20I" -> "T20"
    "MDM" -> "First Class"
    "ODM" -> "List A"
    else -> format.orEmpty()
}
