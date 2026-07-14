package com.tanush.bouncer.data.repository

import android.content.Context
import android.util.Log
import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.CacheConfig
import com.tanush.bouncer.data.local.dao.HighlightDao
import com.tanush.bouncer.data.local.dao.MatchDao
import com.tanush.bouncer.data.local.entities.HighlightEntity
import com.tanush.bouncer.data.local.entities.MatchEntity
import com.tanush.bouncer.data.remote.AppwriteHighlightsClient
import com.tanush.bouncer.data.remote.HighlightRow
import com.tanush.bouncer.services.HighlightFallbackUrlBuilder
import com.tanush.bouncer.services.HighlightFuzzyMatcher
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
}

/** SharedPreferences-backed [HighlightsSyncMarker] — survives process death. */
class PrefsHighlightsSyncMarker(context: Context) : HighlightsSyncMarker {
    private val prefs = context.applicationContext
        .getSharedPreferences(PrefsRegistry.PREFS_APP, Context.MODE_PRIVATE)

    override fun lastFullSyncAtMs(): Long =
        prefs.getLong(PrefsRegistry.Highlights.LAST_FULL_SYNC_AT, 0L)

    override fun recordFullSync(nowMs: Long) {
        prefs.edit().putLong(PrefsRegistry.Highlights.LAST_FULL_SYNC_AT, nowMs).apply()
    }
}

class HighlightsRepository(
    private val highlightDao: HighlightDao,
    private val matchDao: MatchDao,
    private val syncMarker: HighlightsSyncMarker? = null
) {
    private val TAG = "HighlightsRepository"
    private val FRESHNESS_MS = CacheConfig.HIGHLIGHTS_FRESHNESS_MS

    suspend fun fetchForMatch(bouncerMatchId: String): AppResult<List<HighlightEntity>> {
        return AppResult.runCatchingSuspend {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val cached = highlightDao.queryCachedByMatchId(bouncerMatchId, now - FRESHNESS_MS)
                if (cached.isNotEmpty()) {
                    Log.d(TAG, "Returning ${cached.size} cached highlights for match $bouncerMatchId")
                    return@withContext cached
                }

                // Global full-sync throttle: the Appwrite scan is table-wide and Realtime keeps
                // Room warm between scans, so gate it on a device-global timestamp rather than
                // this match's per-match cache. Without this, opening a zero-highlight match (the
                // common case) never writes a row for its id, so its cache stays empty forever and
                // every open re-scans the whole table — billing an org read per row returned.
                val marker = syncMarker
                if (marker != null && now - marker.lastFullSyncAtMs() < FRESHNESS_MS) {
                    Log.d(TAG, "Highlights full-sync throttled; UI served from Room for $bouncerMatchId")
                    return@withContext emptyList()
                }

                Log.d(TAG, "Fetching fresh highlights from Appwrite for match $bouncerMatchId")
                val allHighlights = AppwriteHighlightsClient.getAllHighlights()
                // Mark only when the scan actually ran (non-null) — even a genuinely-empty result
                // counts, so zero-highlight matches stop re-scanning. A null (guard-blocked / not
                // ready / failed) leaves the marker stale so the next open retries.
                if (allHighlights != null) marker?.recordFullSync(System.currentTimeMillis())
                if (allHighlights.isNullOrEmpty()) {
                    Log.d(TAG, "No highlights from Appwrite")
                    return@withContext emptyList()
                }

                ingestRows(allHighlights).filter { it.matchedBouncerMatchId == bouncerMatchId }
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
