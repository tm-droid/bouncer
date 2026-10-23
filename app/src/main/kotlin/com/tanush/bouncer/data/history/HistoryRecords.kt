package com.tanush.bouncer.data.history

import com.tanush.bouncer.data.AppJson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.text.Normalizer

/** One side's results in one format, from `s/team/<key>.json` `f`: `[played, won, lost, tied, drawn, no result]`. */
data class FormatRecord(
    val format: String,
    val played: Int,
    val won: Int,
    val lost: Int,
    val tied: Int,
    val drawn: Int,
)

/** One ground in `idx/venues.json`: `[key, name, city, matches]`. */
data class VenueRow(val key: String, val name: String, val city: String, val matches: Int)

/** What Cricsheet holds on a ground in one format (`s/venue/<key>.json` `f`). */
data class VenueRecord(
    val matches: Int,
    /** Null when no first innings was counted. */
    val averageFirstInnings: Int?,
    val wonBattingFirst: Int,
    val wonChasing: Int,
)

/** Resolves a Cricbuzz ground to Cricsheet's `s/venue` key. */
object VenueKey {
    /** Lowercase, accents folded, punctuation dropped: the builder's `squash`. */
    fun squash(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("[\\u0300-\\u036f]"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9 ]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** The file key the builder writes for a ground: `slug(squash(name) + " " + squash(city))`. */
    fun slug(name: String, city: String): String =
        squash("${squash(name)} ${squash(city)}").replace(' ', '-').ifEmpty { "unknown" }

    /** Cricsheet files one ground under several keys; more than this is a different ground. */
    const val MAX_FILES = 3

    /**
     * "Wankhede Stadium, Mumbai" to every row that is that ground: same name, and a city that
     * matches or is missing (Cricsheet splits one ground by whether the match had a city). A venue
     * text with no city reads as the one city the rows share. Empty when the name has two cities
     * or the city is unknown, because another ground's record is worse than none.
     */
    fun lookup(rows: List<VenueRow>, venueText: String): List<VenueRow> {
        val name = venueText.substringBefore(',').trim()
        if (squash(name).isEmpty()) return emptyList()
        val city = squash(if (venueText.contains(',')) venueText.substringAfterLast(',').trim() else "")
        val same = rows.filter { squash(it.name.substringBefore(',')) == squash(name) }
        val cities = same.map { squash(it.city) }.filter { it.isNotEmpty() }.distinct()
        val chosen = when {
            city.isNotEmpty() -> city.takeIf { it in cities }
            cities.size <= 1 -> cities.firstOrNull().orEmpty()
            else -> null
        } ?: return emptyList()
        return same.filter { squash(it.city).let { c -> c.isEmpty() || c == chosen } }
            .takeIf { it.size <= MAX_FILES }.orEmpty()
    }
}

/**
 * Team and ground records from the Cricsheet site (`s/team`, `idx/venues.json`, `s/venue`). Every
 * failure is null: the screens show what they showed before.
 */
object HistoryRecords {
    private const val MAX_AGE_SECONDS = 24 * 60 * 60
    private const val MAX_STALE_SECONDS = 7 * 24 * 60 * 60
    private const val TEAM_FIELDS = 6
    private const val VENUE_FIELDS = 5

    private val mutex = Mutex()
    @Volatile private var venues: List<VenueRow>? = null

    /** The side's results by format, busiest first; [name] is Cricbuzz's, with any "Women" suffix. */
    suspend fun team(name: String, women: Boolean): List<FormatRecord>? {
        val key = HistoryTeams.keyFor(name, women) ?: return null
        val bytes = StaticSite.fetch(HistoryClient.BASE_URL, "s/team/$key.json", MAX_AGE_SECONDS, MAX_STALE_SECONDS)
            ?: return null
        return parseTeam(bytes.decodeToString())
    }

    /** The ground's record in [format]'s Cricsheet key, for a Cricbuzz venue text. */
    suspend fun venue(venueText: String, matchType: String, international: Boolean): VenueRecord? {
        val group = venueRows()?.let { VenueKey.lookup(it, venueText) }?.takeIf { it.isNotEmpty() } ?: return null
        // One missing file would undercount the ground, so any failure is no record.
        val files = group.map { row ->
            StaticSite.fetch(HistoryClient.BASE_URL, "s/venue/${row.key}.json", MAX_AGE_SECONDS, MAX_STALE_SECONDS)
                ?.decodeToString() ?: return null
        }
        return parseVenue(files, matchType, international)
    }

    private suspend fun venueRows(): List<VenueRow>? {
        venues?.let { return it }
        return mutex.withLock {
            venues ?: StaticSite.fetch(HistoryClient.BASE_URL, "idx/venues.json", MAX_AGE_SECONDS, MAX_STALE_SECONDS)
                ?.let { parseVenues(it.decodeToString()) }?.also { venues = it }
        }
    }

    internal fun parseVenues(json: String): List<VenueRow>? = runCatching {
        (AppJson.parseToJsonElement(json) as JsonArray).mapNotNull { row ->
            val cells = row as? JsonArray ?: return@mapNotNull null
            VenueRow(
                key = (cells.getOrNull(0) as? JsonPrimitive)?.content ?: return@mapNotNull null,
                name = (cells.getOrNull(1) as? JsonPrimitive)?.content ?: return@mapNotNull null,
                city = (cells.getOrNull(2) as? JsonPrimitive)?.takeIf { it.content != "null" }?.content.orEmpty(),
                matches = (cells.getOrNull(3) as? JsonPrimitive)?.intOrNull ?: 0,
            )
        }
    }.getOrNull()

    internal fun parseTeam(json: String): List<FormatRecord>? = runCatching {
        val f = (AppJson.parseToJsonElement(json) as JsonObject)["f"] as JsonObject
        f.mapNotNull { (format, row) ->
            val n = ints(row)?.takeIf { it.size >= TEAM_FIELDS && it[0] > 0 } ?: return@mapNotNull null
            FormatRecord(format, played = n[0], won = n[1], lost = n[2], tied = n[3], drawn = n[4])
        }.sortedByDescending { it.played }
    }.getOrNull()

    internal fun parseVenue(json: String, matchType: String, international: Boolean): VenueRecord? =
        parseVenue(listOf(json), matchType, international)

    /**
     * Counters are `[matches, first-innings runs, first innings counted, won batting first, won chasing]`;
     * a split ground's files add up.
     */
    internal fun parseVenue(files: List<String>, matchType: String, international: Boolean): VenueRecord? = runCatching {
        val rows = files.mapNotNull { json ->
            val f = (AppJson.parseToJsonElement(json) as JsonObject)["f"] as JsonObject
            venueFormat(matchType, international, f.keys)?.let { ints(f[it]) }
                ?.takeIf { it.size >= VENUE_FIELDS && it[0] > 0 }
        }
        if (rows.isEmpty()) return@runCatching null
        val n = List(VENUE_FIELDS) { i -> rows.sumOf { it[i] } }
        VenueRecord(
            matches = n[0],
            averageFirstInnings = n[2].takeIf { it > 0 }?.let { n[1] / it },
            wonBattingFirst = n[3],
            wonChasing = n[4],
        )
    }.getOrNull()

    /** Cricbuzz's match type to Cricsheet's format key; a T20 league match is `T20`, an international `T20I`. */
    internal fun venueFormat(matchType: String, international: Boolean, available: Set<String>): String? {
        val type = matchType.uppercase()
        val format = when {
            "TEST" in type -> "Test"
            "T20" in type -> if (international) "T20I" else "T20"
            "ODI" in type -> "ODI"
            else -> return null
        }
        return format.takeIf { it in available }
    }

    private fun ints(e: kotlinx.serialization.json.JsonElement?): List<Int>? =
        (e as? JsonArray)?.map { (it as? JsonPrimitive)?.intOrNull ?: 0 }
}
