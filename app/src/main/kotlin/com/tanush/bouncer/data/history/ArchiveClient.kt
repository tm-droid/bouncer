package com.tanush.bouncer.data.history

import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.data.remote.CommentaryEntry
import com.tanush.bouncer.data.remote.commentaryEntryOf
import com.tanush.bouncer.models.MatchContext
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.utils.BoundedLruCache
import com.tanush.bouncer.utils.sentryBreadcrumb
import io.sentry.SentryLevel
import kotlinx.serialization.Serializable
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
    val final: Boolean = false,
    val rows: List<JsonObject> = emptyList(),
    val scorecard: JsonElement? = null,
    val context: JsonElement? = null,
) {
    /** The feed, newest first, the order the commentary tab reads. */
    fun commentary(): List<CommentaryEntry> =
        rows.asReversed().mapNotNull { row -> commentaryEntryOf(row.mapValues { (_, v) -> plain(v) }) }

    /** The final card, or null when the match had none or it does not decode. */
    fun scorecard(): Scorecard? = decode<Scorecard>(scorecard)?.takeIf { it.innings.isNotEmpty() }

    fun context(): MatchContext? = decode<MatchContext>(context)

    private inline fun <reified T> decode(element: JsonElement?): T? =
        element?.takeUnless { it is JsonNull }?.let { runCatching { AppJson.decodeFromJsonElement<T>(it) }.getOrNull() }
}

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
