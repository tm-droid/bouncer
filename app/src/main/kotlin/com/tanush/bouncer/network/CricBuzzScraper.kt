package com.tanush.bouncer.network

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.ScrapedBatsman
import com.tanush.bouncer.models.ScrapedBowler
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.Innings
import com.tanush.bouncer.models.BatsmanStats
import com.tanush.bouncer.models.BowlerStats
import com.tanush.bouncer.models.FallOfWicket
import com.tanush.bouncer.models.PartnershipStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.managers.TeamAcronymManager

class CricBuzzScraper {

    companion object {
        private const val TAG = "CricBuzzScraper"
        private const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    /**
     * Fetches live matches using Next.js data stream parsing.
     */
    suspend fun fetchLiveMatches(url: String, matchState: String): List<CricketMatch> = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(10000)
                .get()
            
            val html = doc.html()
            val matches = parseNextJsData(html)
            
            val filteredMatches = filterMatches(matches, matchState)
            Log.d(TAG, "Found ${matches.size} total matches, filtered down to ${filteredMatches.size} for state: $matchState")
            filteredMatches
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching matches from $url", e)
            emptyList()
        }
    }

    private fun filterMatches(matches: List<CricketMatch>, state: String): List<CricketMatch> {
        return matches.filter { match ->
            val matchStatus = match.info.lowercase().trim()
            val isUpcoming = matchStatus.contains("preview") || matchStatus.contains("upcoming") || matchStatus.contains("scheduled")
            val isRecent = matchStatus.contains("complete") || matchStatus.contains("abandon") || matchStatus.contains("result") || matchStatus.contains("tied")
            
            when (state) {
                "Upcoming" -> isUpcoming
                "Recent" -> isRecent
                "Live" -> !isUpcoming && !isRecent
                else -> true
            }
        }.distinctBy { it.id }
    }

    /**
     * Advanced Next.js f-push data parser.
     * Extracts JSON chunks from the stream and searches for match objects.
     */
    private fun parseNextJsData(html: String): List<CricketMatch> {
        val matches = mutableListOf<CricketMatch>()
        val startMarker = "self.__next_f.push("
        var startIndex = 0
        
        while (true) {
            val idx = html.indexOf(startMarker, startIndex)
            if (idx == -1) break
            
            val openParenIdx = idx + startMarker.length - 1
            var parenCount = 0
            var inString = false
            var escape = false
            var endIdx = -1
            
            for (i in openParenIdx until html.length) {
                val char = html[i]
                if (escape) { escape = false; continue }
                if (char == '\\') { escape = true; continue }
                if (char == '"') { inString = !inString; continue }
                if (!inString) {
                    if (char == '(') parenCount++
                    else if (char == ')') {
                        parenCount--
                        if (parenCount == 0) { endIdx = i; break }
                    }
                }
            }
            
            if (endIdx != -1) {
                val scriptContent = html.substring(openParenIdx + 1, endIdx)
                try {
                    val jsonElement = JsonParser.parseString(scriptContent)
                    if (jsonElement.isJsonArray) {
                        val array = jsonElement.asJsonArray
                        if (array.size() > 1 && array[1].isJsonPrimitive && array[1].asJsonPrimitive.isString) {
                            var innerJsonStr = array[1].asString
                            
                            val colonIndex = innerJsonStr.indexOf(':')
                            if (colonIndex in 1..9) {
                                val prefix = innerJsonStr.take(colonIndex)
                                if (prefix.all { it.isDigit() || it in 'a'..'f' }) {
                                    innerJsonStr = innerJsonStr.substring(colonIndex + 1)
                                }
                            }
                            
                            if (innerJsonStr.startsWith("I")) innerJsonStr = innerJsonStr.substring(1)
                            
                            try {
                                val data = JsonParser.parseString(innerJsonStr)
                                matches.addAll(findMatchesInJson(data))
                            } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}
                startIndex = endIdx + 1
            } else {
                startIndex = idx + 1
            }
        }
        return matches
    }

    private fun findMatchesInJson(element: JsonElement): List<CricketMatch> {
        val matches = mutableListOf<CricketMatch>()
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            val match = parseMatchObject(obj)
            if (match != null) matches.add(match)
            obj.keySet().forEach { matches.addAll(findMatchesInJson(obj.get(it))) }
        } else if (element.isJsonArray) {
            element.asJsonArray.forEach { matches.addAll(findMatchesInJson(it)) }
        }
        return matches
    }

    private fun parseMatchObject(obj: JsonObject): CricketMatch? {
        try {
            if (!obj.has("matchInfo")) return null
            val info = obj.getAsJsonObject("matchInfo")
            val matchId = info.get("matchId").asString
            val team1 = info.getAsJsonObject("team1").get("teamName").asString
            val team2 = info.getAsJsonObject("team2").get("teamName").asString
            val status = info.get("status").asString
            val state = info.get("state")?.asString ?: "Live"
            
            // Try to extract score if available in current Next.js structure
            var team1Score = ""
            var team2Score = ""
            var team1Overs = ""
            var team2Overs = ""
            var team1IsBatting = false
            var team2IsBatting = false

            if (obj.has("matchScore")) {
                val scoreObj = obj.getAsJsonObject("matchScore")
                if (scoreObj.has("team1Score")) {
                    val s1 = scoreObj.getAsJsonObject("team1Score").getAsJsonObject("inngs1")
                    team1Score = "${s1.get("runs")?.asString ?: ""}/${s1.get("wickets")?.asString ?: "0"}"
                    team1Overs = s1.get("overs")?.asString ?: ""
                }
                if (scoreObj.has("team2Score")) {
                    val s2 = scoreObj.getAsJsonObject("team2Score").getAsJsonObject("inngs1")
                    team2Score = "${s2.get("runs")?.asString ?: ""}/${s2.get("wickets")?.asString ?: "0"}"
                    team2Overs = s2.get("overs")?.asString ?: ""
                }
                // Determine batting team
                val battingId = scoreObj.get("battingTeamId")?.asString
                if (battingId != null) {
                    team1IsBatting = info.getAsJsonObject("team1").get("teamId")?.asString == battingId
                    team2IsBatting = info.getAsJsonObject("team2").get("teamId")?.asString == battingId
                }
            }
            
            val teams = listOf(
                CricketMatch.Team(team1, team1Score, "", team1IsBatting, TeamLogoManager.getLogoUrl(team1), TeamAcronymManager.getAcronym(team1), team1Overs),
                CricketMatch.Team(team2, team2Score, "", team2IsBatting, TeamLogoManager.getLogoUrl(team2), TeamAcronymManager.getAcronym(team2), team2Overs)
            )
            
            // Build MatchScore object for internal logic
            val matchScore = if (team1Score.isNotBlank() || team2Score.isNotBlank()) {
                CricketMatch.MatchScore(
                    currentInnings = if (team1IsBatting) "1" else "2",
                    batsmen = emptyList(),
                    bowlers = emptyList(),
                    overs = if (team1IsBatting) team1Overs else team2Overs,
                    currentRunRate = null,
                    requiredRunRate = null,
                    lastWicket = null,
                    recentBalls = null
                )
            } else {
                CricketMatch.MatchScore(
                    currentInnings = "",
                    batsmen = emptyList(),
                    bowlers = emptyList(),
                    overs = null,
                    currentRunRate = null,
                    requiredRunRate = null,
                    lastWicket = null,
                    recentBalls = null
                )
            }

            return CricketMatch(
                id = matchId,
                seriesId = "",
                matchTitle = "$team1 vs $team2",
                matchType = determineMatchType("$team1 vs $team2"),
                status = status,
                teams = teams,
                venue = "",
                date = "Today",
                matchTime = "",
                score = matchScore,
                currentStatus = status,
                info = state,
                category = "Other",
                gender = "Men",
                competitionType = "Other"
            )
        } catch (_: Exception) { return null }
    }

    data class MatchDetails(
        val recentBalls: List<String>,
        val overNumbers: List<String>,
        val batsmen: List<ScrapedBatsman>,
        val bowler: ScrapedBowler?,
        val recentBallsSummary: String?,
        val score: String? = null
    )

    /**
     * Accuracy-focused fetch for Live Scorecard details.
     */
    suspend fun fetchBallByBallAndBatsmen(matchId: String): MatchDetails = withContext(Dispatchers.IO) {
        val url = "https://www.cricbuzz.com/live-cricket-scores/$matchId"
        try {
            val doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(10000).get()
            
            val batsmen = mutableListOf<ScrapedBatsman>()
            val batRows = doc.select("div.cb-col.cb-col-100.cb-min-itm-rw")
            for (row in batRows) {
                val nameLink = row.select("div.cb-col-50 a").first()
                if (nameLink != null) {
                    val name = nameLink.text()
                    val stats = row.select("div.cb-col-10, div.cb-col-14")
                    if (stats.size >= 4) {
                        val isStriker = nameLink.parent()?.text()?.contains("*") == true || row.text().contains("*")
                        batsmen.add(ScrapedBatsman(
                            name = name.replace("*", "").trim(),
                            runs = stats[0].text(),
                            balls = stats[1].text(),
                            fours = stats[2].text(),
                            sixes = stats[3].text(),
                            strikeRate = stats.lastOrNull()?.text() ?: "-",
                            isStriker = isStriker
                        ))
                    }
                }
            }

            var bowler: ScrapedBowler? = null
            val bowlerHeader = doc.select("div.cb-col-50:contains(Bowler)").first()
            if (bowlerHeader != null) {
                var current = bowlerHeader.parent()?.nextElementSibling()
                while (current != null && current.hasClass("cb-min-itm-rw")) {
                    val name = current.select("div.cb-col-50 a").text()
                    val stats = current.select("div.cb-col-10")
                    if (stats.size >= 4) {
                        bowler = ScrapedBowler(name, stats[0].text(), stats[1].text(), stats[2].text(), stats[3].text(), stats.lastOrNull()?.text() ?: "-")
                        break
                    }
                    current = current.nextElementSibling()
                }
            }

            val recentBalls = mutableListOf<String>()
            val overNumbers = mutableListOf<String>()
            doc.select("div.cb-min-rcnt span").forEach { 
                val text = it.text().trim()
                if (text.isNotEmpty()) recentBalls.add(text)
            }
            
            val score = doc.select("span.cb-font-20.text-bold").first()?.text() ?: 
                        doc.select("div.cb-min-bat-rw span").first()?.text()

            MatchDetails(recentBalls, overNumbers, batsmen, bowler, null, score)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching match details for $matchId", e)
            MatchDetails(emptyList(), emptyList(), emptyList(), null, null, null)
        }
    }

    /**
     * Enhanced Commentary Extraction with multi-source fallback.
     */
    suspend fun fetchLiveCommentary(matchUrl: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(matchUrl).userAgent(USER_AGENT).timeout(12000).get()
            val lines = mutableListOf<String>()
            
            doc.select("p.cb-com-ln").forEach { lines.add(it.text().trim()) }
            doc.select("div.cb-col.cb-col-100.cb-com-ln").forEach { lines.add(it.text().trim()) }
            doc.select("div.flex.gap-4.border-dotted").forEach { lines.add(it.text().trim()) }

            Log.d(TAG, "Fetched ${lines.size} commentary lines from Cricbuzz")
            lines.filter { it.length > 5 }.distinct()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Cricbuzz commentary", e)
            emptyList()
        }
    }

    private fun determineMatchType(title: String): String {
        val t = title.lowercase()
        return when {
            t.contains("test") -> "Test"
            t.contains("odi") -> "ODI"
            t.contains("t20") -> "T20"
            else -> "Limited Overs"
        }
    }

    suspend fun fetchScorecard(matchId: String): Scorecard = withContext(Dispatchers.IO) {
        val url = "https://www.cricbuzz.com/live-cricket-scorecard/$matchId"
        try {
            val doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(15000).get()
            val inningsList = mutableListOf<Innings>()
            
            val inningsHeaders = doc.select("div[id^=team-][id*=-innings-]")
            for (header in inningsHeaders) {
                val headerId = header.id()
                val content = doc.getElementById("scard-$headerId") ?: continue
                
                val teamName = header.select("div.font-bold").first()?.text() ?: "Unknown"
                val score = header.select("span.font-bold").text()
                val overs = header.select("span:contains(Ov)").text().replace("(", "").replace(")", "").trim()
                
                val batsmen = mutableListOf<BatsmanStats>()
                content.select("div.grid.scorecard-bat-grid").forEach { row ->
                    val name = row.select("a").first()?.text() ?: ""
                    if (name.isNotEmpty() && !name.contains("Batter")) {
                        val stats = row.children()
                        if (stats.size >= 6) {
                            batsmen.add(BatsmanStats(name, row.select("div.text-cbTxtSec").text(), stats[1].text(), stats[2].text(), stats[3].text(), stats[4].text(), stats[5].text()))
                        }
                    }
                }

                val bowlers = mutableListOf<BowlerStats>()
                content.select("div.grid.scorecard-bowl-grid").forEach { row ->
                    val name = row.select("a").first()?.text() ?: ""
                    if (name.isNotEmpty() && !name.contains("Bowler")) {
                        val stats = row.children()
                        if (stats.size >= 5) {
                            bowlers.add(BowlerStats(name, stats[1].text(), stats[2].text(), stats[3].text(), stats[4].text(), "0", "0", stats.last()?.text() ?: ""))
                        }
                    }
                }
                
                val fows = mutableListOf<FallOfWicket>()
                val fowSection = content.select("div:contains(Fall of Wickets)").first()
                if (fowSection != null) {
                    val fowText = fowSection.nextElementSibling()?.text() ?: ""
                    // Parse "10-1 (V Kohli, 1.2), 25-2 (RG Sharma, 4.5)"
                    val fowItems = fowText.split("),")
                    fowItems.forEachIndexed { idx, item ->
                        val parts = item.trim().split(" (")
                        if (parts.size >= 2) {
                            val scoreParts = parts[0].split("-")
                            val detailParts = parts[1].replace(")", "").split(", ")
                            if (scoreParts.size >= 2 && detailParts.size >= 2) {
                                fows.add(FallOfWicket(
                                    score = parts[0],
                                    overs = detailParts[1],
                                    batsman = detailParts[0],
                                    number = idx + 1
                                ))
                            }
                        }
                    }
                }
                
                val partnerships = mutableListOf<PartnershipStats>()
                val partSection = content.select("div:contains(Partnerships)").first()
                if (partSection != null) {
                    // Cricbuzz often has a partnerships section in some views, but it's sometimes hidden in scorecard
                    // Fallback to manual calculation if needed, but let's try to find the grid if it exists
                }

                inningsList.add(Innings(teamName, score, overs, batsmen, bowlers, fows, partnerships, "", ""))
            }
            Scorecard(inningsList)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching scorecard", e)
            Scorecard(emptyList())
        }
    }
}
