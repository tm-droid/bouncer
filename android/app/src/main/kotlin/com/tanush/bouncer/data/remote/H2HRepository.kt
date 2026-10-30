package com.tanush.bouncer.data.remote

import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.data.history.ArchiveClient
import com.tanush.bouncer.data.history.HistoryClient
import com.tanush.bouncer.data.history.HistoryMatchLinker
import com.tanush.bouncer.data.history.HistoryTeams
import com.tanush.bouncer.data.history.StaticSite
import com.tanush.bouncer.data.history.archiveKey
import com.tanush.bouncer.ui.components.FormatStats
import com.tanush.bouncer.ui.components.HeadToHeadHistory
import com.tanush.bouncer.ui.components.HeadToHeadStats
import com.tanush.bouncer.ui.components.HistoricalMatch
import com.tanush.bouncer.utils.BoundedLruCache
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

data class H2HPair(
    val stats: HeadToHeadStats,
    val history: HeadToHeadHistory,
    /** True when the record was counted from Bouncer's own archive, because Cricsheet holds none for a side. */
    val fromArchive: Boolean = false,
)

private val WOMEN_SUFFIX = Regex("""\s+Women(?:'s)?$""", RegexOption.IGNORE_CASE)

/** A side's name as Cricsheet writes it: a women's side is the plain name, the gender apart. */
internal fun archiveSideName(name: String): String = name.trim().replace(WOMEN_SUFFIX, "")

/** True when either side is named as a women's side. */
internal fun isWomensPair(team1: String, team2: String): Boolean =
    WOMEN_SUFFIX.containsMatchIn(team1.trim()) || WOMEN_SUFFIX.containsMatchIn(team2.trim())

private const val WOMEN_KEY = "__w"

/** `s/h2h/<a>__<b>[__w].json` from two `idx/teams.json` keys: slugs sorted, the women's mark last. */
internal fun h2hPath(key1: String, key2: String): String {
    val women = key1.endsWith(WOMEN_KEY)
    val (a, b) = listOf(key1.removeSuffix(WOMEN_KEY), key2.removeSuffix(WOMEN_KEY)).sorted()
    return "s/h2h/${a}__$b${if (women) WOMEN_KEY else ""}.json"
}

/**
 * Head-to-head from Cricsheet's `s/h2h` pair files (`.claude/rules/history-data.md`), for every
 * format: `{t, g, f:{format:[played, wins of t[0], wins of t[1], ties, draws, no results]}, m:[[id,
 * date, format, winnerIdx, result]]}`, `m` newest first. Null when Cricsheet has no file for the
 * pair, which is every pair of sides it holds nothing on (Afghanistan after Nov 2024 included).
 */
object H2HRepository {
    private const val MAX_AGE_SECONDS = 24 * 60 * 60
    private const val MAX_STALE_SECONDS = 7 * 24 * 60 * 60
    private const val CACHE_SIZE = 4

    // A search types the same pair many times; a miss is remembered too (an empty string).
    private val files = BoundedLruCache<String, String>(CACHE_SIZE)

    /**
     * The pair's record in [matchType]'s format ("ODI", "Test", "T20"), with every format listed
     * beside it. A null [matchType] picks the format with the most meetings; an unrecognised one,
     * which Cricsheet has no pair counters for, gives null.
     */
    suspend fun getH2H(team1: String, team2: String, matchType: String? = null): H2HPair? {
        val women = isWomensPair(team1, team2)
        val known = listOf(team1, team2).map { HistoryTeams.keyFor(archiveSideName(it), women) }
        // A side Cricsheet has no record of (Afghanistan): the archive's own pair file instead.
        val fromArchive = known.any { it == null }
        if (fromArchive && (women || HistoryTeams.load() == null)) return null
        val (key1, key2) = pairKeys(team1, team2, known)
        if (key1 == key2) return null
        val path = h2hPath(key1, key2)
        val base = if (fromArchive) ArchiveClient.BASE_URL else HistoryClient.BASE_URL
        val json = files[base + path] ?: fetch(base, path).also { files[base + path] = it }
        return json.takeIf { it.isNotEmpty() }?.let { cricsheetH2H(it, team1, team2, matchType) }?.copy(fromArchive = fromArchive)
    }

    private fun pairKeys(team1: String, team2: String, known: List<String?>): Pair<String, String> =
        (known[0] ?: archiveKey(archiveSideName(team1))) to (known[1] ?: archiveKey(archiveSideName(team2)))

    private suspend fun fetch(base: String, path: String): String =
        StaticSite.fetch(base, path, MAX_AGE_SECONDS, MAX_STALE_SECONDS)?.decodeToString().orEmpty()
}

private class Counters(val played: Int, val wins1: Int, val wins2: Int, val ties: Int, val draws: Int, val noResults: Int)

private const val COUNTER_FIELDS = 6
private const val LAST_MEETINGS = 5

/** The Cricsheet format a Cricbuzz match type reads, among [available]; null for one with no counters. */
internal fun h2hFormat(matchType: String, available: Set<String>): String? {
    val type = matchType.uppercase()
    return when {
        "TEST" in type -> "Test".takeIf { it in available }
        "T20" in type -> listOf("T20I", "T20").firstOrNull { it in available }
        "ODI" in type -> "ODI".takeIf { it in available }
        else -> null
    }
}

/** Counters by format, busiest first, in the caller's team order. */
private fun countersOf(f: JsonObject?, swapped: Boolean): Map<String, Counters> =
    f.orEmpty().mapNotNull { (format, row) ->
        val n = (row as? JsonArray)?.map { (it as? JsonPrimitive)?.intOrNull ?: 0 }?.takeIf { it.size >= COUNTER_FIELDS }
        n?.let { format to Counters(it[0], it[if (swapped) 2 else 1], it[if (swapped) 1 else 2], it[3], it[4], it[5]) }
    }.filter { (_, c) -> c.played > 0 }.sortedByDescending { (_, c) -> c.played }.toMap()

/** Parses one pair file into the card models, in the caller's team order. Null when unreadable or empty. */
internal fun cricsheetH2H(json: String, team1: String, team2: String, matchType: String?): H2HPair? {
    val root = runCatching { AppJson.parseToJsonElement(json) as JsonObject }.getOrNull() ?: return null
    val sides = (root["t"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
    if (sides.size != 2) return null
    // The file names its own sides in its own order; the caller's team1 may be either.
    val swapped = !HistoryMatchLinker.sameOrRenamed(sides[0], archiveSideName(team1)) &&
        HistoryMatchLinker.sameOrRenamed(sides[1], archiveSideName(team1))
    val counters = countersOf(root["f"] as? JsonObject, swapped)
    val format = if (matchType == null) counters.keys.firstOrNull() else h2hFormat(matchType, counters.keys)
    val chosen = counters[format ?: return null] ?: return null

    val meetings = (root["m"] as? JsonArray).orEmpty().mapNotNull { meetingOf(it, sides, swapped, team1, team2) }
        .filter { it.first == format }
    return pairOf(chosen, counters, meetings, team1, team2)
}

private fun pairOf(
    chosen: Counters,
    counters: Map<String, Counters>,
    meetings: List<Triple<String, HistoricalMatch, String>>,
    team1: String,
    team2: String,
): H2HPair {
    val recent = meetings.take(LAST_MEETINGS).map { it.second }
    val stats = HeadToHeadStats(
        totalMatches = chosen.played,
        team1Wins = chosen.wins1,
        team2Wins = chosen.wins2,
        draws = chosen.draws,
        team1HomeWins = 0,
        team2HomeWins = 0,
        lastFiveResults = meetings.take(LAST_MEETINGS).map { it.third },
        // Pair files carry no scores, so these stay blank and the card leaves the records out.
        team1HighestScore = "",
        team2HighestScore = "",
        team1LowestScore = "",
        team2LowestScore = "",
        lastWinner = recent.firstOrNull()?.winner,
        noResults = chosen.noResults,
        ties = chosen.ties,
    )
    val history = HeadToHeadHistory(
        team1Name = team1,
        team2Name = team2,
        totalMatches = chosen.played,
        team1Wins = chosen.wins1,
        team2Wins = chosen.wins2,
        noResults = chosen.noResults,
        recentMatches = recent,
        formatStats = counters.mapValues { (f, c) -> FormatStats(f, c.played, c.wins1, c.wins2) },
    )
    return H2HPair(stats, history)
}

/** A meeting as (format, card chip, "W1"/"W2"/"D" in the caller's order). */
private fun meetingOf(
    row: kotlinx.serialization.json.JsonElement,
    sides: List<String>,
    swapped: Boolean,
    team1: String,
    team2: String,
): Triple<String, HistoricalMatch, String>? {
    val cells = row as? JsonArray ?: return null
    val format = (cells.getOrNull(2) as? JsonPrimitive)?.content ?: return null
    val winner = (cells.getOrNull(3) as? JsonPrimitive)?.intOrNull ?: return null
    // Index into the file's order -> the caller's.
    val side = if (winner in sides.indices) winner xor (if (swapped) 1 else 0) else -1
    val name = listOf(team1, team2).getOrNull(side)
    val chip = HistoricalMatch(
        date = (cells.getOrNull(1) as? JsonPrimitive)?.content.orEmpty(),
        venue = "",
        winner = name,
        team1Score = "-",
        team2Score = "-",
        format = format,
        margin = (cells.getOrNull(4) as? JsonPrimitive)?.takeIf { it.isString }?.content,
    )
    return Triple(format, chip, when (side) { 0 -> "W1"; 1 -> "W2"; else -> "D" })
}
