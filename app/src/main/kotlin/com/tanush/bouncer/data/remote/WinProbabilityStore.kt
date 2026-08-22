package com.tanush.bouncer.data.remote

import com.tanush.bouncer.data.AppJson
import android.os.SystemClock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.tanush.bouncer.utils.sentryBreadcrumb
import io.sentry.SentryLevel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Win probabilities for the currently live matches, as computed server-side.
 *
 * Nothing here calculates anything. The numbers come from the `bouncer-match-sync` function, which
 * writes one aggregate `live-winprob` file per run covering every live match.
 *
 * Why a bucket file rather than the match-live-detail table: the match list renders a bar per card,
 * and Appwrite bills one DB read per document returned. Reading a detail row per visible card would
 * cost a read per card per refresh, against the same org budget whose exhaustion 402s every read in
 * the app. Storage is a separate meter and this is a single download however many cards are shown.
 */
object WinProbabilityStore {

    private const val BUCKET_ID = "cricket-stats"
    private const val FILE_ID = "live-winprob"

    /**
     * Refresh interval. match-sync runs every minute, so anything shorter re-downloads a file that
     * cannot have changed. Uptime-based: wall-clock would jump on an NTP correction or a timezone
     * change, which is exactly the bug that disabled the match memory cache.
     */
    private const val TTL_MS = 60_000L
    private const val HTTP_MAX_AGE_SECONDS = 60

    @Serializable
    private data class Entry(
        @SerialName("team1WinProbability") val team1WinProbability: Double? = null,
        // Tests only. A draw belongs to neither side, so unlike the win probability the server does
        // not re-orient it.
        val drawProbability: Double? = null,
        val confidence: Double? = null,
        // True when the fixture has not started and the number is a prior from team strength alone.
        val prematch: Boolean? = null,
        val battingTeamName: String? = null,
        val battingTeamIsTeam1: Boolean? = null,
        val format: String? = null,
    )

    /**
     * What the server says about one match: a percentage for teams[0], a draw share if the format
     * has one, and whether this is a pre-match prior rather than a reading of a live position.
     */
    data class Probability(
        val team1Percent: Double,
        val drawPercent: Double?,
        val isPrematch: Boolean = false,
    )

    @Serializable
    private data class Payload(
        val version: Int = 0,
        val updatedAt: Long = 0,
        val matches: Map<String, Entry> = emptyMap(),
    )

    private val mutex = Mutex()

    @Volatile
    private var cache: Map<String, Probability> = emptyMap()

    @Volatile
    private var fetchedAtUptimeMs: Long = 0

    /** True once a successful fetch has happened, so callers can tell "no data yet" from "no value". */
    @Volatile
    var hasData: Boolean = false
        private set

    /**
     * Probability for `teams[0]` of [matchId] as a percentage (0..100), or null when the server has
     * no number for it. Reads the in-memory snapshot only — call [refresh] to update it.
     */
    fun forMatch(matchId: String): Double? = cache[matchId]?.team1Percent

    /** As [forMatch], but carrying the draw share for the formats that have one. */
    fun probabilityFor(matchId: String): Probability? = cache[matchId]

    /**
     * Download the aggregate file if the cached copy has expired.
     *
     * Safe to call on every poll; it no-ops inside the TTL. A failure leaves the previous snapshot
     * in place — a slightly stale probability beats a bar that blinks out on one bad request.
     */
    suspend fun refresh(force: Boolean = false) {
        if (isFresh(force)) return
        mutex.withLock {
            // Re-check inside the lock: concurrent pollers would otherwise each issue a download.
            if (isFresh(force)) return
            // Same window as this store's own TTL: match-sync rewrites the file every minute, and
            // an HTTP cache allowed to outlive the TTL would answer the refresh with the copy the
            // TTL just decided was too old.
            val bytes = AppwriteClient.downloadFile(BUCKET_ID, FILE_ID, HTTP_MAX_AGE_SECONDS) ?: return
            val parsed = parse(bytes) ?: return
            cache = parsed.matches.mapNotNull { (id, entry) ->
                entry.team1WinProbability?.let { team1 ->
                    id to Probability(
                        team1Percent = (team1 * 100.0).coerceIn(0.0, 100.0),
                        drawPercent = entry.drawProbability?.let { (it * 100.0).coerceIn(0.0, 100.0) },
                        isPrematch = entry.prematch == true,
                    )
                }
            }.toMap()
            fetchedAtUptimeMs = SystemClock.elapsedRealtime()
            hasData = true
        }
    }

    private fun isFresh(force: Boolean): Boolean =
        !force && hasData && SystemClock.elapsedRealtime() - fetchedAtUptimeMs < TTL_MS

    private fun parse(bytes: ByteArray): Payload? = runCatching {
        AppJson.decodeFromString<Payload>(String(bytes, Charsets.UTF_8))
    }.getOrElse { e ->
        sentryBreadcrumb(
            category = "winprob",
            message = "Failed to parse $FILE_ID: ${e.message}",
            level = SentryLevel.WARNING,
        )
        null
    }
}
