package com.tanush.bouncer.data.repository

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class HighlightsRepository(
    private val highlightDao: HighlightDao,
    private val matchDao: MatchDao
) {
    private val TAG = "HighlightsRepository"
    private val FRESHNESS_MS = CacheConfig.HIGHLIGHTS_FRESHNESS_MS

    suspend fun fetchForMatch(bouncerMatchId: String): AppResult<List<HighlightEntity>> {
        return AppResult.runCatchingSuspend {
            withContext(Dispatchers.IO) {
                val freshnessCutoff = System.currentTimeMillis() - FRESHNESS_MS
                val cached = highlightDao.queryCachedByMatchId(bouncerMatchId, freshnessCutoff)
                if (cached.isNotEmpty()) {
                    Log.d(TAG, "Returning ${cached.size} cached highlights for match $bouncerMatchId")
                    return@withContext cached
                }

                Log.d(TAG, "Fetching fresh highlights from Appwrite for match $bouncerMatchId")
                val allHighlights = AppwriteHighlightsClient.getAllHighlights() ?: emptyList()
                if (allHighlights.isEmpty()) {
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
