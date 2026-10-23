package com.tanush.bouncer.data.history

import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.data.remote.commentaryEntryOf
import com.tanush.bouncer.models.MatchContext
import com.tanush.bouncer.models.OverPoint
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.utils.BoundedLruCache
import com.tanush.bouncer.utils.sentryBreadcrumb
import io.sentry.SentryLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * One match from the archive: what Appwrite held for it before the 14-day prune.
 *
 * `rows` are `match-commentary` rows under their own column names, in `sort_key` order, so they
 * read through [commentaryEntryOf] like a live row. The rest is kept as JSON and decoded only by
 * the surface that shows it. Written by `appwrite/tools/archive` (`.claude/rules/archive-data.md`).
 */
@Serializable
data class ArchivedMatch(
    val v: Int = 0,
    val id: String = "",
    /** `true` from the archiver, `1` from the backfill: kept as JSON because a Boolean property rejects the number. */
    val final: JsonElement? = null,
    val rows: List<JsonObject> = emptyList(),
    val scorecard: JsonElement? = null,
    val context: JsonElement? = null,
    /** The match-list entry it was seen under; kept as JSON, read by the surface that needs it. */
    val meta: JsonElement? = null,
    /** The `wpe-` win-probability log; kept as JSON, read by the graphs. */
    val evalLog: JsonElement? = null,
    /** `cricbuzz-backfill` for a match written from Cricbuzz's pages after the fact (scorecard, no commentary rows). */
    val source: String? = null,
    /** A backfilled match's toss, officials, player of the match and squads; read by [archivedInfo]. */
    val info: JsonElement? = null,
    /** A backfilled match's deliveries; read by [deliveries]. */
    val balls: JsonElement? = null,
) {
    val isFinal: Boolean get() = (final as? JsonPrimitive)?.let { it.content == "true" || it.content == "1" } == true

    /** The feed, newest first, the order the commentary tab reads. */
    fun commentary(): List<CommentaryEntry> =
        rows.asReversed().mapNotNull { row -> commentaryEntryOf(row.mapValues { (_, v) -> plain(v) }) }

    /** The final card, or null when the match had none or it does not decode. */
    fun scorecard(): Scorecard? = decode<Scorecard>(scorecard)?.takeIf { it.innings.isNotEmpty() }

    fun context(): MatchContext? = decode<MatchContext>(context)

    /**
     * The `wpe-` log's stamped overs as a card carrying only overs, oriented to its `team1Id`; null
     * with fewer than two stamped overs or no orientation, because an unoriented arc is not drawable.
     */
    fun evalOvers(): Scorecard? {
        val log = evalLog as? JsonObject ?: return null
        val teamId = (log["team1Id"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content ?: return null
        val points = (log["points"] as? JsonArray).orEmpty().mapNotNull { point ->
            val o = point as? JsonObject ?: return@mapNotNull null
            val num = { key: String -> (o[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.doubleOrNull }
            OverPoint(
                innings = num("innings")?.toInt() ?: return@mapNotNull null,
                over = num("over")?.toInt() ?: return@mapNotNull null,
                score = num("score")?.toInt(),
                wickets = num("wickets")?.toInt(),
                p = num("p"),
            )
        }
        return points.takeIf { it.size >= MIN_EVAL_POINTS }?.let { Scorecard(overs = it, probabilityTeamId = teamId) }
    }

    private inline fun <reified T> decode(element: JsonElement?): T? =
        element?.takeUnless { it is JsonNull }?.let { runCatching { AppJson.decodeFromJsonElement<T>(it) }.getOrNull() }
}

private const val MIN_EVAL_POINTS = 2

// Top-level, not in a companion: a private companion object hides the serializer the plugin
// generates there, and every decode then fails with IllegalAccessError.
/** A JSON value as the plain Kotlin value an Appwrite row's map would hold. */
private fun plain(value: JsonElement): Any? = when {
    value is JsonNull -> null
    value !is JsonPrimitive -> value
    value.isString -> value.content
    else -> value.booleanOrNull ?: value.longOrNull ?: value.doubleOrNull
}

/**
 * Reads the match archive: every match Bouncer followed, kept on GitHub Pages after Appwrite prunes
 * it. Keyed by the Cricbuzz match id, so no linking is involved (unlike [HistoryRepository]).
 *
 * The commentary, scorecard and Info tab of one match each ask for it, so a decoded file is held
 * briefly in memory; under that, OkHttp's cache holds the bytes for the request max-age.
 */
object ArchiveClient {
    const val BASE_URL = "https://tm-droid.github.io/bouncer-archive/"

    // A final file changes only when a late row arrives, hours after the match.
    private const val MAX_AGE_SECONDS = 24 * 60 * 60
    private const val MEMORY_TTL_MS = 10 * 60_000L
    private const val CACHE_SIZE = 4

    private val cache = BoundedLruCache<String, Pair<Long, ArchivedMatch?>>(CACHE_SIZE)

    suspend fun match(id: String, nowMs: Long = System.currentTimeMillis()): ArchivedMatch? {
        if (id.isEmpty() || !id.all(Char::isDigit)) return null
        synchronized(cache) { cache[id] }?.let { (at, match) -> if (nowMs - at < MEMORY_TTL_MS) return match }
        val bytes = StaticSite.fetch(BASE_URL, "m/$id.json", MAX_AGE_SECONDS)
        val match = bytes?.let { decode(id, it) }
        // A miss is remembered too: the three surfaces would otherwise each ask for the same 404.
        synchronized(cache) { cache[id] = nowMs to match }
        return match
    }

    internal fun decode(id: String, bytes: ByteArray): ArchivedMatch? =
        runCatching { AppJson.decodeFromString<ArchivedMatch>(bytes.decodeToString()) }
            .onFailure { sentryBreadcrumb("archive", "Unreadable archived match $id: ${it.message}", SentryLevel.WARNING) }
            .getOrNull()
}
