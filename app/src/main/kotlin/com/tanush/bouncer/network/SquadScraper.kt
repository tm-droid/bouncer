package com.tanush.bouncer.network

import android.util.Log
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Scraper for fetching squad/playing XI information from Cricbuzz
 * Mirrors the iOS SquadScraper implementation
 */
object SquadScraper {
    private const val TAG = "SquadScraper"
    private const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Safari/605.1.15"
    
    data class Player(
        val name: String,
        val role: String,
        val imageUrl: String,
        val isCaptain: Boolean = false,
        val isWicketKeeper: Boolean = false
    )
    
    /**
     * Fetch squads for a match from Cricbuzz
     * @param matchId The Cricbuzz match ID
     * @param matchTitle The match title (used to construct URL slug)
     * @return Pair of (team1Squad, team2Squad)
     */
    suspend fun fetchSquads(matchId: String): Pair<List<Player>, List<Player>> = withContext(Dispatchers.IO) {
        val urlString = "https://www.cricbuzz.com/live-cricket-scores/$matchId"
        
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.cricbuzz"
            message = "Fetching squads"
            level = SentryLevel.INFO
            setData("matchId", matchId)
            setData("url", urlString)
        })
        
        try {
            Log.d(TAG, "Fetching squads from: $urlString")
            
            val doc = Jsoup.connect(urlString)
                .userAgent(USER_AGENT)
                .get()
            
            // Team 1 (left column)
            val team1 = mutableListOf<Player>()
            val leftCol = doc.select("div.cb-play11-lft-col").first()
            if (leftCol != null) {
                val playerCards = leftCol.select("a.cb-player-card-left")
                for (card in playerCards) {
                    val nameDiv = card.select("div.cb-player-name-left > div").first()
                    val nameHtml = nameDiv?.html() ?: ""
                    val name = nameHtml.split("<br>").firstOrNull()?.trim() ?: ""
                    val role = card.select("span.cb-font-12.text-gray").text()
                    val img = card.select("img.cb-plyr-img-left").attr("src")
                    
                    if (name.isNotEmpty()) {
                        val isCaptain = role.contains("(c)", ignoreCase = true) || name.contains("(c)", ignoreCase = true)
                        val isWicketKeeper = role.contains("(wk)", ignoreCase = true) || name.contains("(wk)", ignoreCase = true)
                        val cleanName = name.replace("(c)", "").replace("(wk)", "").trim()
                        val cleanRole = role.replace("(c)", "").replace("(wk)", "").trim()
                        
                        team1.add(Player(
                            name = cleanName,
                            role = cleanRole,
                            imageUrl = img,
                            isCaptain = isCaptain,
                            isWicketKeeper = isWicketKeeper
                        ))
                    }
                }
            }
            
            // Team 2 (right column)
            val team2 = mutableListOf<Player>()
            val rightCol = doc.select("div.cb-play11-rt-col").first()
            if (rightCol != null) {
                val playerCards = rightCol.select("a.cb-player-card-right")
                for (card in playerCards) {
                    val nameDiv = card.select("div.cb-player-name-right > div").first()
                    val nameHtml = nameDiv?.html() ?: ""
                    val name = nameHtml.split("<br>").firstOrNull()?.trim() ?: ""
                    val role = card.select("span.cb-font-12.text-gray").text()
                    val img = card.select("img.cb-plyr-img-right").attr("src")
                    
                    if (name.isNotEmpty()) {
                        val isCaptain = role.contains("(c)", ignoreCase = true) || name.contains("(c)", ignoreCase = true)
                        val isWicketKeeper = role.contains("(wk)", ignoreCase = true) || name.contains("(wk)", ignoreCase = true)
                        val cleanName = name.replace("(c)", "").replace("(wk)", "").trim()
                        val cleanRole = role.replace("(c)", "").replace("(wk)", "").trim()
                        
                        team2.add(Player(
                            name = cleanName,
                            role = cleanRole,
                            imageUrl = img,
                            isCaptain = isCaptain,
                            isWicketKeeper = isWicketKeeper
                        ))
                    }
                }
            }
            
            if (team1.isEmpty() && team2.isEmpty()) {
                Log.w(TAG, "No squad data found, squads might not be announced")
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "network.cricbuzz"
                    message = "No squad data found"
                    level = SentryLevel.WARNING
                    setData("matchId", matchId)
                })
            } else {
                Log.d(TAG, "Successfully fetched squads: Team1=${team1.size} players, Team2=${team2.size} players")
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "network.cricbuzz"
                    message = "Successfully fetched squads"
                    level = SentryLevel.INFO
                    setData("matchId", matchId)
                    setData("team1Players", team1.size)
                    setData("team2Players", team2.size)
                })
            }
            
            Pair(team1, team2)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching squads", e)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.cricbuzz"
                message = "Error fetching squads"
                level = SentryLevel.ERROR
                setData("matchId", matchId)
                setData("url", urlString)
                setData("error", e.message ?: "Unknown error")
            })
            Sentry.captureException(e)
            Pair(emptyList(), emptyList())
        }
    }
}
