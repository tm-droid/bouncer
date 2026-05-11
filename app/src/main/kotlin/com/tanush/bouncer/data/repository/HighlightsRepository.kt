package com.tanush.bouncer.data.repository

import android.util.Log
import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.local.dao.HighlightDao
import com.tanush.bouncer.data.local.dao.MatchDao
import com.tanush.bouncer.data.local.entities.HighlightEntity
import com.tanush.bouncer.data.remote.AppwriteHighlightsClient
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
    private val FRESHNESS_MS = 3_600_000L  // 1 hour

    suspend fun fetchForMatch(bouncerMatchId: String): AppResult<List<HighlightEntity>> {
        return AppResult.runCatching {
            withContext(Dispatchers.IO) {
                val cached = highlightDao.queryAllRecent().filter {
                    it.matchedBouncerMatchId == bouncerMatchId &&
                    (System.currentTimeMillis() - it.fetchedAt) < FRESHNESS_MS
                }
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

                val bouncerMatches = matchDao.getAllMatches().first()
                val results = mutableListOf<HighlightEntity>()

                for (row in allHighlights) {
                    val entity = row.toEntity()
                    val matched = HighlightFuzzyMatcher.matchHighlightToBouncerMatch(entity, bouncerMatches)
                    if (matched != null) {
                        entity.copy(matchedBouncerMatchId = matched.matchId).let { enriched ->
                            val fallback = HighlightFallbackUrlBuilder.buildFallbackUrl(enriched, highlightDao)
                            val final = enriched.copy(fallbackUrl = fallback)
                            results.add(final)
                        }
                    } else {
                        results.add(entity)
                    }
                }

                highlightDao.upsertAll(results)
                Log.d(TAG, "Upserted ${results.size} highlights; ${results.count { it.matchedBouncerMatchId != null }} matched")

                results.filter { it.matchedBouncerMatchId == bouncerMatchId }
            }
        }
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
