package com.tanush.bouncer.data.remote

import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.models.MatchContext
import com.tanush.bouncer.utils.BoundedLruCache

/**
 * A match's context file, fetched when a reader opens the Info tab and not before.
 *
 * Read-only and unbilled as a database read: it is a bucket download through the same funnel as
 * every other one, so the breaker and the usage gauges see it. The file changes when the toss is
 * made and about hourly as its forecast is refreshed, so a short max-age is enough and a download
 * of an unchanged file is cheap.
 */
object MatchContextStore {
    private const val BUCKET_ID = "cricket-stats"
    private const val MAX_AGE_SECONDS = 10 * 60
    private const val MEMORY_TTL_MS = 5 * 60_000L
    private const val CACHE_SIZE = 16

    private val cache = BoundedLruCache<String, Pair<Long, MatchContext>>(CACHE_SIZE)

    /** The same id `contextFileId` in match-sync builds. */
    internal fun fileId(matchId: String): String =
        "mc-" + matchId.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(FILE_ID_MATCH_CHARS)

    private const val FILE_ID_MATCH_CHARS = 32

    suspend fun get(matchId: String, nowMs: Long = System.currentTimeMillis()): MatchContext? {
        synchronized(cache) { cache[matchId] }?.let { (at, context) ->
            if (nowMs - at < MEMORY_TTL_MS) return context
        }
        val bytes = AppwriteClient.downloadFile(BUCKET_ID, fileId(matchId), MAX_AGE_SECONDS)
            ?: return synchronized(cache) { cache[matchId]?.second }
        val parsed = parse(String(bytes, Charsets.UTF_8)) ?: return null
        synchronized(cache) { cache[matchId] = nowMs to parsed }
        return parsed
    }

    /** Null for anything that is not a context — a 404 body, a truncated file. */
    internal fun parse(json: String): MatchContext? =
        runCatching { AppJson.decodeFromString<MatchContext>(json) }.getOrNull()
}
