package com.tanush.bouncer.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.tanush.bouncer.models.CricsheetMatch
import com.tanush.bouncer.models.CricsheetInnings
import com.tanush.bouncer.models.CricsheetOver
import com.tanush.bouncer.models.CricsheetBall
import com.tanush.bouncer.models.MatchResult
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

class CricsheetDataFetcher(private val context: Context? = null) {
    companion object {
        private const val TAG = "CricsheetDataFetcher"
    }
    
    private val baseURL = "https://cricsheet.org/downloads/"
    private val gson: Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd")
        .create()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    suspend fun downloadMatchData(tournament: String, year: Int): List<CricsheetMatch> = withContext(Dispatchers.IO) {
        val urlString = "$baseURL${tournament}_$year.json"
        
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.cricsheet"
            message = "Downloading match data"
            level = SentryLevel.INFO
            setData("tournament", tournament)
            setData("year", year)
            setData("url", urlString)
        })
        
        try {
            val jsonString = URL(urlString).readText()
            val matches = parseMatchData(jsonString)
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.cricsheet"
                message = "Successfully downloaded match data"
                level = SentryLevel.INFO
                setData("matchCount", matches.size)
            })
            
            matches
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download match data from $urlString", e)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.cricsheet"
                message = "Error downloading match data"
                level = SentryLevel.ERROR
                setData("url", urlString)
                setData("error", e.message ?: "Unknown error")
            })
            Sentry.captureException(e)
            emptyList()
        }
    }

    private fun parseMatchData(jsonString: String): List<CricsheetMatch> {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "data.parse"
            message = "Parsing Cricsheet match data"
            level = SentryLevel.INFO
            setData("jsonLength", jsonString.length)
        })
        
        return try {
             // Cricsheet JSONs are usually a single match object per file if downloading individual matches,
             // but the URL structure `${tournament}_${year}.json` suggests a collection or a specific file.
             // Actually, Cricsheet provides zip files for tournaments. 
             // The iOS code downloads `${tournament}_${year}.json`. 
             // If that's a valid endpoint returning a list of matches or a single match, we handle it.
             // Assuming it returns a list of matches based on the return type `[CricsheetMatch]`.
             // But wait, the iOS code parses `CricsheetResponse` which has `info` and `innings`.
             // This structure corresponds to a SINGLE match JSON from Cricsheet (version 0.9+).
             // So `downloadMatchData` in iOS likely fetches a SINGLE match file, but the function signature implies it might be used for multiple?
             // No, the URL is constructed as `${tournament}_${year}.json`. This looks like a specific file name convention used in the iOS app, 
             // possibly pointing to a processed file or a specific match file.
             // However, standard Cricsheet downloads are zips.
             // If the user has a custom backend or specific URL pattern, I should stick to what the iOS code does.
             // The iOS code parses `CricsheetResponse` which is a single match.
             // And returns `[CricsheetMatch(...)]` (a list containing one match).
         
             val response = gson.fromJson(jsonString, CricsheetResponse::class.java)
             listOf(mapToCricsheetMatch(response))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse match data", e)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "data.parse"
                message = "Error parsing Cricsheet match data"
                level = SentryLevel.ERROR
                setData("error", e.message ?: "Unknown error")
            })
            Sentry.captureException(e)
            emptyList()
        }
    }
    
    private data class CricsheetResponse(
        val meta: MatchMeta,
        val info: MatchInfo,
        val innings: List<Innings>
    )

    private data class MatchMeta(
        val data_version: String,
        val created: String,
        val revision: Int
    )

    private data class MatchInfo(
        val dates: List<String>,
        val match_type: String,
        val venue: String,
        val teams: List<String>,
        val outcome: Outcome?,
        val gender: String,
        val overs: Int?,
        val player_of_match: List<String>?,
        val umpires: List<String>?
    )

    private data class Outcome(
        val winner: String?,
        val by: By?,
        val result: String?
    )

    private data class By(
        val runs: Int?,
        val wickets: Int?
    )

    private data class Innings(
        val team: String,
        val overs: List<Over>
    )

    private data class Over(
        val over: Int,
        val deliveries: List<Delivery>
    )

    private data class Delivery(
        val runs: Runs,
        val wickets: List<Wicket>?,
        val extras: Extras?
    )
    
    private data class Runs(
        val total: Int,
        val extras: Int
    )

    private data class Wicket(
        val kind: String,
        val player_out: String
    )
    
    private data class Extras(
        val wides: Int?,
        val noballs: Int?,
        val legbyes: Int?,
        val byes: Int?
    )

    private fun mapToCricsheetMatch(response: CricsheetResponse): CricsheetMatch {
        // Parse the first date from the dates list
        val matchDate = try {
            response.info.dates.firstOrNull()?.let { dateFormat.parse(it) } ?: Date()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse date, using current date", e)
            Date()
        }
        
        // Calculate run rate for each innings
        val mappedInnings = response.innings.map { innings ->
            val totalRuns = innings.overs.flatMap { it.deliveries }.sumOf { it.runs.total }
            val totalOvers = innings.overs.size.toDouble()
            val runRate = if (totalOvers > 0) totalRuns / totalOvers else 0.0
            
            CricsheetInnings(
                team = innings.team,
                overs = innings.overs.map { over ->
                    CricsheetOver(
                        overNumber = over.over,
                        balls = over.deliveries.map { delivery ->
                            CricsheetBall(
                                runs = delivery.runs.total,
                                isWicket = !delivery.wickets.isNullOrEmpty(),
                                wicketType = delivery.wickets?.firstOrNull()?.kind,
                                extras = delivery.runs.extras,
                                extraType = determineExtraType(delivery.extras)
                            )
                        },
                        runs = over.deliveries.sumOf { it.runs.total },
                        wickets = over.deliveries.sumOf { it.wickets?.size ?: 0 }
                    )
                },
                total = totalRuns,
                wickets = innings.overs.flatMap { it.deliveries }.sumOf { it.wickets?.size ?: 0 },
                runRate = runRate
            )
        }
        
        return CricsheetMatch(
            matchId = response.meta.data_version,
            date = matchDate,
            venue = response.info.venue,
            teams = CricsheetMatch.Teams(response.info.teams.getOrElse(0) { "" }, response.info.teams.getOrElse(1) { "" }),
            innings = mappedInnings,
            result = MatchResult(
                winner = response.info.outcome?.winner,
                margin = response.info.outcome?.by?.runs ?: response.info.outcome?.by?.wickets ?: 0,
                marginType = if (response.info.outcome?.by?.runs != null) "runs" else "wickets"
            )
        )
    }
    
    private fun determineExtraType(extras: Extras?): String? {
        if (extras == null) return null
        return when {
            extras.wides != null && extras.wides > 0 -> "wide"
            extras.noballs != null && extras.noballs > 0 -> "noball"
            extras.legbyes != null && extras.legbyes > 0 -> "legbye"
            extras.byes != null && extras.byes > 0 -> "bye"
            else -> null
        }
    }
    
    /**
     * Downloads and parses all historical matches from Cricsheet (all_json.zip)
     */
    suspend fun downloadAllHistoricalMatches(): List<CricsheetMatch> = withContext(Dispatchers.IO) {
        val urlString = "https://cricsheet.org/downloads/all_json.zip"
        
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.cricsheet"
            message = "Downloading all historical matches"
            level = SentryLevel.INFO
            setData("url", urlString)
        })
        
        try {
            val ctx = context ?: return@withContext emptyList()
            val filesDir = ctx.filesDir
            val zipPath = File(filesDir, "all_json.zip")
            
            // Download zip file
            Log.d(TAG, "Downloading all historical matches from $urlString")
            val connection = URL(urlString).openConnection()
            connection.getInputStream().use { input ->
                FileOutputStream(zipPath).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Unzip and parse all JSON files
            val matches = unzipAndParseAllMatches(zipPath.absolutePath, filesDir.absolutePath)
            
            // Clean up zip file
            zipPath.delete()
            
            Log.d(TAG, "Successfully parsed ${matches.size} historical matches")
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.cricsheet"
                message = "Successfully downloaded historical matches"
                level = SentryLevel.INFO
                setData("matchCount", matches.size)
            })
            
            matches
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download historical matches", e)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.cricsheet"
                message = "Error downloading historical matches"
                level = SentryLevel.ERROR
                setData("url", urlString)
                setData("error", e.message ?: "Unknown error")
            })
            Sentry.captureException(e)
            emptyList()
        }
    }
    
    /**
     * Unzips the downloaded zip and parses all JSON files into CricsheetMatch
     */
    private fun unzipAndParseAllMatches(zipPath: String, destinationPath: String): List<CricsheetMatch> {
        val matches = mutableListOf<CricsheetMatch>()
        val extractDir = File(destinationPath, "cricsheet_json")
        
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "data.parse"
            message = "Unzipping and parsing all matches"
            level = SentryLevel.INFO
            setData("zipPath", zipPath)
        })
        
        try {
            // Create extraction directory
            extractDir.mkdirs()
            
            // Unzip the file
            ZipInputStream(File(zipPath).inputStream()).use { zis ->
                var entry = zis.nextEntry
                var parsedCount = 0
                var failedCount = 0
                
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".json")) {
                        val outputFile = File(extractDir, entry.name)
                        outputFile.parentFile?.mkdirs()
                        
                        FileOutputStream(outputFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        
                        // Parse the JSON file
                        try {
                            val jsonString = outputFile.readText()
                            val response = gson.fromJson(jsonString, CricsheetResponse::class.java)
                            matches.add(mapToCricsheetMatch(response))
                            parsedCount++
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse ${entry.name}", e)
                            failedCount++
                        }
                        
                        // Delete the extracted file to save space
                        outputFile.delete()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "data.parse"
                    message = "Completed parsing zip contents"
                    level = SentryLevel.INFO
                    setData("parsedCount", parsedCount)
                    setData("failedCount", failedCount)
                })
            }
            
            // Clean up extraction directory
            extractDir.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unzip and parse matches", e)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "data.parse"
                message = "Error unzipping and parsing matches"
                level = SentryLevel.ERROR
                setData("error", e.message ?: "Unknown error")
            })
            Sentry.captureException(e)
            extractDir.deleteRecursively()
        }
        
        return matches
    }
    
    /**
     * Filters matches by series name, team, or event
     */
    fun filterMatches(
        matches: List<CricsheetMatch>,
        seriesName: String? = null,
        team: String? = null,
        event: String? = null
    ): List<CricsheetMatch> {
        return matches.filter { match ->
            val matchesSeries = seriesName == null || match.venue.contains(seriesName, ignoreCase = true)
            val matchesTeam = team == null || match.teams.batting == team || match.teams.bowling == team
            // Additional event/series filtering can be added if metadata is available
            matchesSeries && matchesTeam
        }
    }
}

/**
 * Cricsheet error types
 */
sealed class CricsheetError : Exception() {
    object InvalidURL : CricsheetError()
    object DownloadFailed : CricsheetError()
    object ParsingFailed : CricsheetError()
}
