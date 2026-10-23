package com.tanush.bouncer.network

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.tanush.bouncer.models.FormatStats
import com.tanush.bouncer.models.PlayerProfile
import com.tanush.bouncer.models.RecentInnings

/**
 * Player profiles out of Cricbuzz's App Router flight stream.
 *
 * Cricbuzz moved `/profiles/{id}/{slug}` off the Next.js pages router: the page no longer carries a
 * `<script id="__NEXT_DATA__">` block at all, only `self.__next_f.push(...)` chunks. Measured
 * 2026-08-22 from two networks and from inside the Appwrite container — 446 KB of markup, zero
 * occurrences of `__NEXT_DATA__`.
 *
 * That alone would have been a quiet degradation, because [PlayerProfileScraper] falls back to
 * Jsoup selectors. It was not: those selectors (`cb-font-40`, `cb-plyr-inf`, `cb-lst-itm`,
 * `cb-plyr-thead`, …) are old-site class names and match **nothing** on the current page, so the
 * fallback produced a profile with the search result's name and every other field empty. The player
 * screen was rendering a shell, not thin data.
 *
 * Everything the old parser wanted is still served, one layer deeper: a player object carrying
 * `faceImageId`/`name`/`bat`/`bowl`, and a sibling object holding `battingStats`/`bowlingStats` as
 * a header row plus one labelled row per statistic.
 */
internal object PlayerProfileRsc {

    /** Column header Cricbuzz uses for T20 internationals, against the label the app displays. */
    private const val HEADER_T20 = "T20"
    private const val LABEL_T20 = "T20I"

    /** `"$26"` and friends: React hoisted the real string elsewhere in the stream. Not prose. */
    private val BACK_REFERENCE = """^\$[0-9a-f]+$""".toRegex()

    /** `74(60)`, or `74*(60)` when not out — the score cell in the recent-form tables. */
    private val SCORE_CELL = """^(\d+)\*?\s*(?:\((\d+)\))?""".toRegex()

    private const val MAX_RECENT_INNINGS = 10

    /**
     * The profile on this page, or null when the page carries no flight player object — a 404 body,
     * a redirect, or another upstream move. Null is the caller's signal to try the next strategy,
     * never a reason to emit an empty profile.
     */
    fun parse(html: String, playerId: String, fallbackName: String): PlayerProfile? {
        val payloads = buildList { forEachNextFlightPayload(html) { add(it) } }
        val player = payloads.firstNotNullOfOrNull { findObject(it) { o -> o.has("faceImageId") && o.has("name") } }
            ?: return null
        val statsHolder = payloads.firstNotNullOfOrNull { findObject(it) { o -> o.has("battingStats") } }

        return PlayerProfile(
            id = playerId,
            name = player.stringOrNull("name") ?: fallbackName,
            country = player.stringOrNull("intlTeam").orEmpty(),
            role = player.stringOrNull("role").orEmpty(),
            // DoB carries a parenthesised running age ("November 05, 1988 (37 years)") that is stale
            // the moment it is cached; DoBFormat is the same date without it.
            dateOfBirth = player.stringOrNull("DoBFormat") ?: player.stringOrNull("DoB").orEmpty(),
            battingStyle = player.stringOrNull("bat").orEmpty(),
            bowlingStyle = player.stringOrNull("bowl").orEmpty(),
            imageUrl = player.stringOrNull("faceImageId")
                ?.let { "https://www.cricbuzz.com/a/img/v1/152x152/i1/c$it/player.jpg" },
            bio = player.stringOrNull("bio").orEmpty(),
            teams = player.stringOrNull("teams")
                ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            stats = statsHolder?.let(::formatStats).orEmpty(),
            recentForm = recentForm(player),
        )
    }

    /**
     * Batting and bowling for each format column, keyed by the label the app displays.
     *
     * Both tables are `{ headers: ["ROWHEADER", "Test", "ODI", …], values: [{values: ["Matches", …]}] }`
     * — a transposed table, so a format is a *column* and every statistic has to be read by its row
     * label rather than by position.
     */
    private fun formatStats(holder: JsonObject): Map<String, FormatStats> {
        // `battingStats: null` is served for players with no recorded stats. Gson keeps that as a
        // JsonNull member, which getAsJsonObject would hard-cast and throw on (ANDROID-5T).
        val batting = holder.memberOrNull<JsonObject>("battingStats")?.let(::readTable) ?: return emptyMap()
        val bowling = holder.memberOrNull<JsonObject>("bowlingStats")?.let(::readTable)

        return buildMap {
            for (header in batting.formats) {
                val bat = batting.column(header)
                val bowl = bowling?.column(header).orEmpty()
                put(
                    if (header == HEADER_T20) LABEL_T20 else header,
                    FormatStats(
                        matches = bat["Matches"].asInt(),
                        innings = bat["Innings"].asInt(),
                        runs = bat["Runs"].asInt(),
                        highScore = bat["Highest"] ?: "-",
                        average = bat["Average"].asDouble(),
                        strikeRate = bat["SR"].asDouble(),
                        hundreds = bat["100s"].asInt(),
                        fifties = bat["50s"].asInt(),
                        fours = bat["Fours"].asInt(),
                        sixes = bat["Sixes"].asInt(),
                        wickets = bowl["Wickets"].asInt(),
                        bowlingAverage = bowl["Avg"].asDouble(),
                        economy = bowl["Eco"].asDouble(),
                        bestBowling = bowl["BBI"] ?: "-",
                        fiveWickets = bowl["5w"].asInt(),
                    ),
                )
            }
        }
    }

    /** A transposed stats table: the format columns, and each row's cells by its label. */
    private class Table(val formats: List<String>, private val rows: Map<String, List<String>>) {
        fun column(format: String): Map<String, String> {
            val index = formats.indexOf(format)
            if (index < 0) return emptyMap()
            return rows.mapNotNull { (label, cells) -> cells.getOrNull(index)?.let { label to it } }.toMap()
        }
    }

    private fun readTable(table: JsonObject): Table? {
        val headers = table.memberOrNull<JsonArray>("headers")?.mapNotNull { it.stringOrNull() } ?: return null
        // The first header is the row-label column ("ROWHEADER"), not a format.
        if (headers.size < 2) return null

        val rows = (table.memberOrNull<JsonArray>("values") ?: return null).mapNotNull { row ->
            val cells = (row as? JsonObject)?.memberOrNull<JsonArray>("values")?.mapNotNull { it.stringOrNull() }
            if (cells.isNullOrEmpty()) null else cells.first() to cells.drop(1)
        }.toMap()

        return Table(headers.drop(1), rows)
    }

    /**
     * The last innings Cricbuzz lists, newest first.
     *
     * `recentBatting.rows[].values` is `[matchId, "74(60)", "ENG", "ODI", "19 Jul 26"]` — the match
     * id leads and is not displayed, so the cells are read from the end of the row backwards.
     */
    private fun recentForm(player: JsonObject): List<RecentInnings> {
        val rows = player.memberOrNull<JsonObject>("recentBatting")?.memberOrNull<JsonArray>("rows") ?: return emptyList()
        return rows.take(MAX_RECENT_INNINGS).mapNotNull { row ->
            val cells = (row as? JsonObject)?.memberOrNull<JsonArray>("values")?.mapNotNull { it.stringOrNull() }
            if (cells == null || cells.size < 4) return@mapNotNull null
            val score = SCORE_CELL.find(cells[cells.size - 4]) ?: return@mapNotNull null
            RecentInnings(
                runs = score.groupValues[1].toIntOrNull() ?: return@mapNotNull null,
                balls = score.groupValues[2].toIntOrNull() ?: 0,
                opponent = cells[cells.size - 3],
                format = cells[cells.size - 2],
                date = cells[cells.size - 1],
            )
        }
    }

    /** First object anywhere in this payload satisfying [predicate]; the flight tree nests deeply. */
    private fun findObject(element: JsonElement, predicate: (JsonObject) -> Boolean): JsonObject? {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                if (predicate(obj)) return obj
                for ((_, value) in obj.entrySet()) findObject(value, predicate)?.let { return it }
            }
            element.isJsonArray -> for (child in element.asJsonArray) findObject(child, predicate)?.let { return it }
        }
        return null
    }

    /** A primitive read as its source literal — numbers included, so `faceImageId` stays exact. */
    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.stringOrNull()?.takeIf { it.isNotBlank() && !BACK_REFERENCE.matches(it) }

    private fun JsonElement.stringOrNull(): String? =
        if (isJsonPrimitive) runCatching { asString }.getOrNull() else null

    /**
     * The member as a [T], or null when it is absent, JSON `null`, or another type. Gson's
     * `getAsJsonObject`/`getAsJsonArray` cast unconditionally and throw on a `JsonNull` member.
     */
    private inline fun <reified T : JsonElement> JsonObject.memberOrNull(key: String): T? = get(key) as? T

    private fun String?.asInt(): Int = this?.trim()?.toIntOrNull() ?: 0

    private fun String?.asDouble(): Double = this?.trim()?.toDoubleOrNull() ?: 0.0
}
