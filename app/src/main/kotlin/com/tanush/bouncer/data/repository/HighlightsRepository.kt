package com.tanush.bouncer.data.repository

import android.content.Context
import android.util.Log
import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.CacheConfig
import com.tanush.bouncer.data.local.dao.HighlightDao
import com.tanush.bouncer.data.local.dao.MatchDao
import com.tanush.bouncer.data.local.entities.HighlightEntity
import com.tanush.bouncer.data.local.entities.MatchEntity
import com.tanush.bouncer.data.local.entities.toMatchEntity
import com.tanush.bouncer.data.remote.AppwriteHighlightsClient
import com.tanush.bouncer.data.remote.HighlightRow
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.services.HighlightFallbackUrlBuilder
import com.tanush.bouncer.services.HighlightFuzzyMatcher
import com.tanush.bouncer.data.prefs.BouncerPreferences
import com.tanush.bouncer.data.prefs.PreferenceStore
import com.tanush.bouncer.utils.PrefsRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Device-global marker for the last full highlights-table scan. Lets [HighlightsRepository]
 * throttle the table-wide Appwrite read (one scan populates Room for ALL matches) so it can't
 * fire once per match-tab open. Abstracted so it can be faked in JVM unit tests.
 */
interface HighlightsSyncMarker {
    fun lastFullSyncAtMs(): Long
    fun recordFullSync(nowMs: Long)

    /**
     * Highest `fetched_at` already pulled from Appwrite. This is a *server-supplied* row value, not
     * a local clock reading — using local time here would let clock skew skip rows permanently.
     */
    fun lastRowFetchedAt(): Long
    fun recordRowFetchedAt(value: Long)

    /**
     * Last time a page run was issued at all, drained or not. [lastFullSyncAtMs] only advances on a
     * completed drain, so it cannot pace the drain itself.
     */
    fun lastPageRunAtMs(): Long
    fun recordPageRun(nowMs: Long)
}

/** Matches the 7-day highlight retention in SeriesSyncWorker. */
private const val HIGHLIGHTS_BACKFILL_MS = CacheConfig.HIGHLIGHTS_FRESHNESS_MS * 7 * 24

/** [HighlightsSyncMarker] over the app's main preference store — survives process death. */
class PrefsHighlightsSyncMarker(context: Context) : HighlightsSyncMarker {
    private val prefs = BouncerPreferences.also { PreferenceStore.initialize(context) }

    override fun lastFullSyncAtMs(): Long =
        prefs.getLong(PrefsRegistry.Highlights.LAST_FULL_SYNC_AT, 0L)

    override fun recordFullSync(nowMs: Long) {
        prefs.putLong(PrefsRegistry.Highlights.LAST_FULL_SYNC_AT, nowMs)
    }

    // A fresh install has no watermark, and 0 asked for the whole `highlights` table — paid for 100
    // rows per 5-minute drain until it caught up, for clips SeriesSyncWorker deletes from Room
    // after 7 days anyway. So a missing watermark starts at that same retention edge.
    override fun lastRowFetchedAt(): Long =
        prefs.getLong(PrefsRegistry.Highlights.LAST_ROW_FETCHED_AT, 0L).takeIf { it > 0L }
            ?: (System.currentTimeMillis() - HIGHLIGHTS_BACKFILL_MS)

    override fun recordRowFetchedAt(value: Long) {
        prefs.putLong(PrefsRegistry.Highlights.LAST_ROW_FETCHED_AT, value)
    }

    override fun lastPageRunAtMs(): Long =
        prefs.getLong(PrefsRegistry.Highlights.LAST_PAGE_RUN_AT, 0L)

    override fun recordPageRun(nowMs: Long) {
        prefs.putLong(PrefsRegistry.Highlights.LAST_PAGE_RUN_AT, nowMs)
    }
}

class HighlightsRepository(
    private val highlightDao: HighlightDao,
    private val matchDao: MatchDao,
    private val syncMarker: HighlightsSyncMarker? = null
) {
    private val TAG = "HighlightsRepository"
    private val FRESHNESS_MS = CacheConfig.HIGHLIGHTS_FRESHNESS_MS
    private val drainIntervalMs = CacheConfig.HIGHLIGHTS_DRAIN_INTERVAL_MS

    /**
     * Why this call must not issue a page run, or null when it may.
     *
     * Two windows, and both are load-bearing. The hourly one paces the steady state. The drain one
     * exists because a run that stopped on the page cap deliberately leaves the hourly marker unset,
     * so without it every match-detail open issued another MAX_PAGES_PER_RUN × PAGE_SIZE burst of
     * billed reads — enough on its own to trip the read guard's per-minute cap and pause every
     * DB_READ in the app for five minutes (ANDROID-44). The backlog still drains, one run per
     * interval.
     */
    private fun syncSkipReason(marker: HighlightsSyncMarker?, now: Long): String? {
        if (marker == null) return null
        if (now - marker.lastFullSyncAtMs() < FRESHNESS_MS) return "Highlights sync throttled"
        if (now - marker.lastPageRunAtMs() < drainIntervalMs) return "Highlights drain paced"
        return null
    }

    suspend fun fetchForMatch(bouncerMatchId: String): AppResult<List<HighlightEntity>> {
        return AppResult.runCatchingSuspend {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val cached = highlightDao.queryCachedByMatchId(bouncerMatchId, now - FRESHNESS_MS)
                if (cached.isNotEmpty()) {
                    Log.d(TAG, "Returning ${cached.size} cached highlights for match $bouncerMatchId")
                    return@withContext cached
                }

                // Global sync throttle: one sync populates Room for ALL matches and Realtime keeps
                // it warm in between, so gate it on a device-global timestamp rather than this
                // match's per-match cache. Without this, opening a zero-highlight match (the common
                // case) never writes a row for its id, so its cache stays empty forever and every
                // open re-syncs — billing an org read per row returned.
                val marker = syncMarker
                val skip = syncSkipReason(marker, now)
                if (skip != null) {
                    Log.d(TAG, "$skip; UI served from Room for $bouncerMatchId")
                    return@withContext emptyList()
                }
                marker?.recordPageRun(now)

                val since = marker?.lastRowFetchedAt() ?: 0L
                Log.d(TAG, "Syncing highlights newer than $since for match $bouncerMatchId")
                val page = AppwriteHighlightsClient.getHighlightsSince(since)
                    ?: run {
                        // Guard-blocked / not ready / failed: leave both markers stale so the next
                        // open retries rather than silently skipping a window of rows.
                        Log.d(TAG, "Highlights sync unavailable")
                        return@withContext emptyList()
                    }

                // Advance the watermark whenever the query ran, even for an empty result — that is
                // the steady state and it must not re-ask for the same window next time.
                marker?.recordRowFetchedAt(page.maxFetchedAt)
                // Only start the freshness throttle once the backlog is drained. A run that stopped
                // on the page cap still has rows waiting, and making it wait an hour per batch
                // would leave a fresh install missing highlights for days.
                if (!page.hasMore) marker?.recordFullSync(System.currentTimeMillis())

                if (page.rows.isEmpty()) {
                    Log.d(TAG, "No new highlights from Appwrite")
                    return@withContext emptyList()
                }

                ingestRows(page.rows)
                // The ingested batch only covers this sync window, so answer from Room — this
                // match's highlights may have arrived in an earlier window and would otherwise
                // vanish from the result.
                highlightDao.queryAllByMatchId(bouncerMatchId)
            }
        }
    }

    // Fuzzy-match each Appwrite row to a local match, enrich, persist to Room, return enriched rows.
    // Shared by the one-shot screen fetch and the Realtime ingest path so live-inserted clips also
    // get a matchedBouncerMatchId (without it, every per-match query filters them out).
    suspend fun ingestRows(rows: List<HighlightRow>): List<HighlightEntity> {
        if (rows.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            val bouncerMatches = matchDao.getAllMatches().first()
            if (bouncerMatches.isEmpty()) {
                Log.w(TAG, "matchDao.getAllMatches() returned empty — highlights will be stored unmatched")
            }
            val results = rows.map { enrich(it.toEntity(), bouncerMatches) }
            highlightDao.upsertAll(results)
            Log.d(TAG, "Upserted ${results.size} highlights; ${results.count { it.matchedBouncerMatchId != null }} matched")
            results
        }
    }

    /**
     * Writes [matches] to the `matches` table the fuzzy matcher reads, then optionally re-scores
     * every highlight still stored without a match.
     *
     * Nothing wrote that table before this existed, so every highlight on every device was stored
     * unmatched and no match's Highlights tab could show one. Writing it is not enough on its own:
     * a row ingested before its match arrived (all existing rows, and a Realtime insert that lands
     * before the first poll) was scored against an empty table and is never looked at again. That
     * is what [rematch] is for.
     */
    suspend fun recordMatches(matches: List<CricketMatch>, rematch: Boolean) {
        if (matches.isEmpty()) return
        withContext(Dispatchers.IO) {
            matchDao.upsertMatches(matches.map { it.toMatchEntity() })
            if (!rematch) return@withContext
            val unmatched = highlightDao.queryUnmatched()
            if (unmatched.isEmpty()) return@withContext
            val bouncerMatches = matchDao.getAllMatches().first()
            val linked = unmatched.map { enrich(it, bouncerMatches) }.filter { it.matchedBouncerMatchId != null }
            if (linked.isNotEmpty()) highlightDao.upsertAll(linked)
            Log.d(TAG, "Re-matched ${linked.size} of ${unmatched.size} unmatched highlights")
        }
    }

    private suspend fun enrich(entity: HighlightEntity, bouncerMatches: List<MatchEntity>): HighlightEntity {
        val matched = HighlightFuzzyMatcher.matchHighlightToBouncerMatch(entity, bouncerMatches)
            ?: return entity
        val withId = entity.copy(matchedBouncerMatchId = matched.matchId)
        return withId.copy(fallbackUrl = HighlightFallbackUrlBuilder.buildFallbackUrl(withId, highlightDao))
    }

    suspend fun refreshIfStale(bouncerMatchId: String): AppResult<List<HighlightEntity>> {
        return fetchForMatch(bouncerMatchId)
    }
}

fun com.tanush.bouncer.data.remote.HighlightRow.toEntity(): HighlightEntity {
    return HighlightEntity(
        id = highlightId,
        title = title,
        imgUrl = imgUrl,
        embedUrl = embedUrl,
        fallbackUrl = null,
        source = source,
        channel = channel,
        type = type,
        matchedBouncerMatchId = null,
        iccMatchId = null,
        iplMatchId = null,
        leagueName = matchLeagueName,
        homeTeam = matchHomeTeam,
        awayTeam = matchAwayTeam,
        matchStartDate = matchStartDate,
        fetchedAt = System.currentTimeMillis()
    )
}
