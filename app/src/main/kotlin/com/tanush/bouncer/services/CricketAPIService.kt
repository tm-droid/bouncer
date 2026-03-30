package com.tanush.bouncer.services

import android.util.Log
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.network.RetrofitClient
import com.tanush.bouncer.network.models.SanwebMatchSummary
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLogLevel
import io.sentry.SentryLevel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.Date

/**
 * Service for fetching cricket matches from the Vercel API
 * Mirrors the iOS CricketAPIService implementation
 */
class CricketAPIService(private val apiKey: String = "") {
    companion object {
        private const val TAG = "CricketAPIService"
    }
    private val baseURL = "https://api-iota-jade.vercel.app/"
    private var lastRequestTime: Date? = null
    private val minimumRequestIntervalMs = 1000L // 1 second
    
    private val vercelApi = RetrofitClient.vercelApi
    
    /**
     * Fetch matches for a specific status type
     */
    suspend fun fetchMatches(statusType: String): List<CricketMatch> {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.api"
            message = "Fetching matches"
            level = SentryLevel.INFO
            setData("statusType", statusType)
        })
        
        handleRateLimiting()
        
        val apiType = when (statusType) {
            "Live" -> "live"
            "Recent" -> "recent"
            "Upcoming" -> "upcoming"
            else -> "live"
        }
        
        return fetchSanwebMatches(apiType)
    }
    
    private suspend fun fetchSanwebMatches(type: String): List<CricketMatch> {
        return try {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.api"
                message = "Fetching Sanweb matches"
                level = SentryLevel.INFO
                setData("type", type)
            })
            
            val listResponse = vercelApi.getMatches(type)
            Sentry.metrics().gauge("cricket_api.list.matches", listResponse.matches.size.toDouble())
            
            if (type == "live") {
                // For live matches, we need details (scores)
                coroutineScope {
                    listResponse.matches.map { matchSummary ->
                        async {
                            try {
                                fetchSanwebMatchDetails(matchSummary.id, "Live")
                            } catch (e: Exception) {
                                Sentry.captureException(e)
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
            } else {
                // For Recent and Upcoming, construct from summary
                listResponse.matches.map { matchSummary ->
                    createMatchFromSummary(matchSummary, type.replaceFirstChar { it.uppercaseChar() })
                }
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            Sentry.metrics().count("cricket_api.fetch.error", 1.0)
            Sentry.logger().log(SentryLogLevel.ERROR, "CricketAPIService fetchSanwebMatches failed: %s", e.message ?: "unknown")
            emptyList()
        }
    }
    
    private fun createMatchFromSummary(summary: SanwebMatchSummary, category: String): CricketMatch {
        val titleParts = summary.title.split(", ")
        val teamsStr = titleParts.firstOrNull() ?: "Unknown vs Unknown"
        val teams = teamsStr.split(" vs ")
        val team1Name = teams.firstOrNull() ?: "Team 1"
        val team2Name = teams.getOrNull(1) ?: "Team 2"
        
        val team1 = CricketMatch.Team(
            name = team1Name,
            score = "",
            status = "",
            isBatting = false,
            logoUrl = TeamLogoManager.getLogoUrl(team1Name)
        )
        
        val team2 = CricketMatch.Team(
            name = team2Name,
            score = "",
            status = "",
            isBatting = false,
            logoUrl = TeamLogoManager.getLogoUrl(team2Name)
        )
        
        return CricketMatch(
            id = summary.id,
            seriesId = "",
            matchTitle = summary.title,
            matchType = "",
            status = summary.status,
            teams = listOf(team1, team2),
            venue = "",
            date = "",
            matchTime = "",
            score = null,
            currentStatus = summary.status,
            info = summary.title,
            recentBalls = null,
            currentBowler = null,
            scrapedBatsmen = null,
            category = category
        )
    }
    
    private suspend fun fetchSanwebMatchDetails(id: String, category: String): CricketMatch {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.category = "service.api"
            message = "Fetching match details"
            level = SentryLevel.INFO
            setData("matchId", id)
            setData("category", category)
        })
        
        val response = vercelApi.getMatchScore(id)
        
        // Parse title "Australia vs Pakistan, 2nd Test - Live Cricket Score"
        val titleParts = response.title.split(", ")
        val teamsStr = titleParts.firstOrNull()?.replace(" - Live Cricket Score", "") ?: "Unknown vs Unknown"
        val teams = teamsStr.split(" vs ")
        val team1Name = teams.firstOrNull() ?: "Team 1"
        val team2Name = teams.getOrNull(1) ?: "Team 2"
        
        // Parse scores from "livescore": "AUS 168/3 (60)"
        val scoreParts = response.livescore.split(" ")
        val team1Score = if (scoreParts.size > 1) scoreParts[1] else ""
        
        val team1 = CricketMatch.Team(
            name = team1Name,
            score = team1Score,
            status = "bat",
            isBatting = true,
            logoUrl = TeamLogoManager.getLogoUrl(team1Name)
        )
        
        val team2 = CricketMatch.Team(
            name = team2Name,
            score = "",
            status = "bowl",
            isBatting = false,
            logoUrl = TeamLogoManager.getLogoUrl(team2Name)
        )
        
        // Parse batsmen
        val batsman1 = CricketMatch.BatsmanScore(
            name = response.batterone,
            runs = response.batsmanonerun.toIntOrNull() ?: 0,
            balls = response.batsmanoneball.replace("(", "").replace(")", "").toIntOrNull() ?: 0,
            fours = 0, // Not provided
            sixes = 0, // Not provided
            strikeRate = response.batsmanonesr.toDoubleOrNull() ?: 0.0
        )
        
        val batsman2 = CricketMatch.BatsmanScore(
            name = response.battertwo,
            runs = response.batsmantworun.toIntOrNull() ?: 0,
            balls = response.batsmantwoball.replace("(", "").replace(")", "").toIntOrNull() ?: 0,
            fours = 0,
            sixes = 0,
            strikeRate = response.batsmantwosr.toDoubleOrNull() ?: 0.0
        )
        
        // Parse bowlers
        val bowler1 = CricketMatch.BowlerScore(
            name = response.bowlerone,
            overs = response.bowleroneover.toDoubleOrNull() ?: 0.0,
            maidens = 0, // Not provided
            runs = response.bowleronerun.toIntOrNull() ?: 0,
            wickets = response.bowleronewickers.toIntOrNull() ?: 0,
            economy = response.bowleroneeconomy.toDoubleOrNull() ?: 0.0
        )
        
        val matchScore = CricketMatch.MatchScore(
            currentInnings = response.update,
            batsmen = listOf(batsman1, batsman2),
            bowlers = listOf(bowler1),
            overs = null,
            currentRunRate = response.runrate,
            requiredRunRate = null,
            lastWicket = null,
            recentBalls = null
        )
        
        return CricketMatch(
            id = id,
            seriesId = "",
            matchTitle = response.title,
            matchType = "Test", // Should parse from title
            status = response.update,
            teams = listOf(team1, team2),
            venue = "", // Not provided
            date = "", // Not provided
            matchTime = "",
            score = matchScore,
            currentStatus = response.update,
            info = response.title,
            recentBalls = null,
            currentBowler = null,
            scrapedBatsmen = null,
            category = category
        )
    }
    
    private suspend fun handleRateLimiting() {
        val lastTime = lastRequestTime
        if (lastTime != null) {
            val timeSinceLastRequest = Date().time - lastTime.time
            if (timeSinceLastRequest < minimumRequestIntervalMs) {
                val sleepTime = minimumRequestIntervalMs - timeSinceLastRequest
                Log.d(TAG, "Rate limit: Sleeping for $sleepTime ms")
                delay(sleepTime)
            }
        }
        lastRequestTime = Date()
    }
    
    sealed class APIError : Exception() {
        object InvalidResponse : APIError()
        object InvalidData : APIError()
        data class DecodingError(override val cause: Throwable) : APIError()
    }
}
