package com.tanush.bouncer.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.tanush.bouncer.models.CricsheetMatch
import com.tanush.bouncer.models.CricsheetInnings
import com.tanush.bouncer.models.CricsheetOver
import com.tanush.bouncer.models.CricsheetBall
import com.tanush.bouncer.models.CricsheetEvent
import com.tanush.bouncer.models.CricsheetToss
import com.tanush.bouncer.models.CricsheetOfficials
import com.tanush.bouncer.models.CricsheetPowerplay
import com.tanush.bouncer.models.CricsheetTarget
import com.tanush.bouncer.models.CricsheetReview
import com.tanush.bouncer.models.CricsheetWicketDetail
import com.tanush.bouncer.models.CricsheetPerson
import com.tanush.bouncer.models.CricsheetPlayerRegistry
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
            // URL pattern `${tournament}_${year}.json` returns a single-match Cricsheet response (v0.9+ schema)
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
    
    // MARK: - Cricsheet JSON Response Data Classes (full spec v1.1.0)
    
    private data class CricsheetResponse(
        val meta: MatchMeta,
        val info: MatchInfo,
        val innings: List<InningsData>?
    )

    private data class MatchMeta(
        val data_version: String,
        val created: String,
        val revision: Int
    )

    private data class MatchInfo(
        val dates: List<String>,
        val match_type: String?,
        val venue: String?,
        val teams: List<String>,
        val outcome: OutcomeData?,
        val gender: String?,
        val overs: Int?,
        val player_of_match: List<String>?,
        val umpires: List<String>?,
        // Newly captured fields
        val city: String?,
        val season: String?,
        val balls_per_over: Int?,
        val event: EventData?,
        val toss: TossData?,
        val players: Map<String, List<String>>?,
        val registry: RegistryData?,
        val officials: OfficialsData?,
        val match_type_number: Int?,
        val team_type: String?,
        // Supersub / bowl_out
        val supersubs: Map<String, String>?,
        val bowl_out: List<Any>?
    )

    private data class OutcomeData(
        val winner: String?,
        val by: ByData?,
        val result: String?,
        val method: String?,          // "D/L", "VJD", etc.
        val eliminator: String?,      // super over winner
        val bowl_out: String?
    )

    private data class ByData(
        val runs: Int?,
        val wickets: Int?,
        val innings: Int?
    )
    
    private data class EventData(
        val name: String?,
        val match_number: Int?,
        val group: String?,
        val stage: String?
    )
    
    private data class TossData(
        val winner: String?,
        val decision: String?
    )
    
    private data class RegistryData(
        val people: Map<String, String>?   // player name → unique identifier
    )
    
    private data class OfficialsData(
        val umpires: List<String>?,
        val match_referees: List<String>?,
        val tv_umpires: List<String>?,
        val reserve_umpires: List<String>?
    )

    private data class InningsData(
        val team: String,
        val overs: List<OverData>?,
        val powerplays: List<PowerplayData>?,
        val target: TargetData?,
        val declared: Boolean?,
        val forfeited: Boolean?,
        val super_over: Boolean?,
        val absent_hurt: List<String>?
    )
    
    private data class PowerplayData(
        val from: Double?,
        val to: Double?,
        val type: String?
    )
    
    private data class TargetData(
        val overs: Double?,
        val runs: Int?
    )

    private data class OverData(
        val over: Int,
        val deliveries: List<DeliveryData>
    )

    private data class DeliveryData(
        val batter: String?,
        val bowler: String?,
        val non_striker: String?,
        val runs: RunsData,
        val wickets: List<WicketData>?,
        val extras: ExtrasData?,
        val review: ReviewData?,
        val replacements: Any?  // complex structure, parsed if needed
    )
    
    private data class RunsData(
        val batter: Int?,
        val extras: Int?,
        val total: Int,
        val non_boundary: Boolean?
    )

    private data class WicketData(
        val kind: String,
        val player_out: String,
        val fielders: List<FielderData>?
    )
    
    private data class FielderData(
        val name: String?,
        val substitute: Boolean?
    )
    
    private data class ExtrasData(
        val wides: Int?,
        val noballs: Int?,
        val legbyes: Int?,
        val byes: Int?,
        val penalty: Int?
    )
    
    private data class ReviewData(
        val by: String?,
        val decision: String?,
        val batter: String?,
        val umpire: String?,
        val umpires_call: Boolean?
    )

    private fun mapToCricsheetMatch(response: CricsheetResponse): CricsheetMatch {
        val info = response.info
        
        // Parse all dates
        val allDates = info.dates.mapNotNull { dateStr ->
            try { dateFormat.parse(dateStr) } catch (_: Exception) { null }
        }
        val matchDate = allDates.firstOrNull() ?: Date()
        
        val toss = info.toss?.takeIf { it.winner != null && it.decision != null }?.let {
            CricsheetToss(winner = it.winner!!, decision = it.decision!!)
        }

        val event = info.event?.takeIf { it.name != null }?.let {
            CricsheetEvent(
                name = it.name!!,
                matchNumber = it.match_number,
                group = it.group,
                stage = it.stage
            )
        }
        
        // Parse officials
        val officials = info.officials?.let { o ->
            CricsheetOfficials(
                umpires = o.umpires ?: info.umpires ?: emptyList(),
                matchReferees = o.match_referees ?: emptyList(),
                tvUmpires = o.tv_umpires ?: emptyList(),
                reserveUmpires = o.reserve_umpires ?: emptyList()
            )
        } ?: info.umpires?.let { u ->
            CricsheetOfficials(umpires = u)
        }
        
        // Parse player registry (name → identifier)
        val registry = info.registry?.people ?: emptyMap()
        
        // Parse innings with all new fields
        val mappedInnings = (response.innings ?: emptyList()).map { innings ->
            val oversData = innings.overs ?: emptyList()
            val totalRuns = oversData.flatMap { it.deliveries }.sumOf { it.runs.total }
            val totalOvers = oversData.size.toDouble()
            val runRate = if (totalOvers > 0) totalRuns / totalOvers else 0.0
            
            CricsheetInnings(
                team = innings.team,
                overs = oversData.map { over ->
                    CricsheetOver(
                        overNumber = over.over,
                        balls = over.deliveries.map { delivery ->
                            val wicketDetails = delivery.wickets?.map { w ->
                                CricsheetWicketDetail(
                                    kind = w.kind,
                                    playerOut = w.player_out,
                                    fielders = w.fielders?.mapNotNull { it.name } ?: emptyList()
                                )
                            } ?: emptyList()
                            
                            val review = delivery.review?.takeIf { it.by != null && it.decision != null }?.let {
                                CricsheetReview(
                                    by = it.by!!,
                                    decision = it.decision!!,
                                    batter = it.batter,
                                    umpire = it.umpire,
                                    umpiresCall = it.umpires_call ?: false
                                )
                            }
                            
                            val batterRuns = delivery.runs.batter ?: 0
                            val isBoundary = (batterRuns == 4 || batterRuns == 6) && 
                                delivery.runs.non_boundary != true
                            
                            CricsheetBall(
                                runs = delivery.runs.total,
                                isWicket = !delivery.wickets.isNullOrEmpty(),
                                wicketType = delivery.wickets?.firstOrNull()?.kind,
                                extras = delivery.runs.extras ?: 0,
                                extraType = determineExtraType(delivery.extras),
                                batter = delivery.batter ?: "",
                                bowler = delivery.bowler ?: "",
                                nonStriker = delivery.non_striker ?: "",
                                batterRuns = batterRuns,
                                isBoundary = isBoundary,
                                wicketDetails = wicketDetails,
                                review = review
                            )
                        },
                        runs = over.deliveries.sumOf { it.runs.total },
                        wickets = over.deliveries.sumOf { it.wickets?.size ?: 0 }
                    )
                },
                total = totalRuns,
                wickets = oversData.flatMap { it.deliveries }.sumOf { it.wickets?.size ?: 0 },
                runRate = runRate,
                powerplays = innings.powerplays.orEmpty().mapNotNull { pp ->
                    if (pp.from != null && pp.to != null && pp.type != null) {
                        CricsheetPowerplay(from = pp.from, to = pp.to, type = pp.type)
                    } else null
                },
                target = innings.target?.let { t ->
                    CricsheetTarget(overs = t.overs, runs = t.runs)
                },
                declared = innings.declared ?: false,
                forfeited = innings.forfeited ?: false,
                superOver = innings.super_over ?: false,
                absentHurt = innings.absent_hurt ?: emptyList()
            )
        }
        
        // Build outcome with method/eliminator
        val outcomeData = info.outcome
        val outcome = CricsheetMatch.Outcome(
            winner = outcomeData?.winner,
            result = outcomeData?.result,
            method = outcomeData?.method,
            eliminator = outcomeData?.eliminator,
            bowlOut = outcomeData?.bowl_out
        )
        
        return CricsheetMatch(
            matchId = response.meta.data_version + "_" + (info.dates.firstOrNull() ?: "") + "_" + info.teams.joinToString("_"),
            date = matchDate,
            venue = info.venue ?: "",
            teams = CricsheetMatch.Teams(
                info.teams.getOrElse(0) { "" },
                info.teams.getOrElse(1) { "" }
            ),
            innings = mappedInnings,
            result = MatchResult(
                winner = outcomeData?.winner,
                margin = outcomeData?.by?.runs ?: outcomeData?.by?.wickets ?: 0,
                marginType = if (outcomeData?.by?.runs != null) "runs" else "wickets"
            ),
            outcome = outcome,
            matchType = info.match_type ?: "",
            gender = info.gender ?: "",
            city = info.city,
            season = info.season,
            ballsPerOver = info.balls_per_over ?: 6,
            event = event,
            toss = toss,
            players = info.players ?: emptyMap(),
            registry = registry,
            officials = officials,
            playerOfMatch = info.player_of_match ?: emptyList(),
            teamType = info.team_type ?: "",
            dates = allDates,
            oversLimit = info.overs
        )
    }
    
    private fun determineExtraType(extras: ExtrasData?): String? {
        if (extras == null) return null
        return when {
            (extras.wides ?: 0) > 0 -> "wide"
            (extras.noballs ?: 0) > 0 -> "noball"
            (extras.legbyes ?: 0) > 0 -> "legbye"
            (extras.byes ?: 0) > 0 -> "bye"
            (extras.penalty ?: 0) > 0 -> "penalty"
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
                            Sentry.captureException(e)
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
    ): List<CricsheetMatch> = matches.filter { match ->
        (seriesName == null || match.venue.contains(seriesName, ignoreCase = true)) &&
            (team == null || match.teams.batting == team || match.teams.bowling == team) &&
            (event == null || match.event?.name?.contains(event, ignoreCase = true) == true)
    }
    
    // MARK: - People Registry (people.csv)
    
    /**
     * Downloads and parses Cricsheet's people.csv player registry.
     * Provides unique IDs and cross-database keys for all known players.
     */
    suspend fun downloadPeopleRegistry(): CricsheetPlayerRegistry = withContext(Dispatchers.IO) {
        val urlString = "https://cricsheet.org/register/people.csv"
        
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.cricsheet"
            message = "Downloading people registry"
            level = SentryLevel.INFO
        })
        
        try {
            // Check local cache first
            val ctx = context
            val cacheFile = ctx?.let { File(it.filesDir, "people_registry.csv") }
            val cacheMaxAgeMs = 7 * 24 * 60 * 60 * 1000L // 7 days
            
            val csvText = if (cacheFile != null && cacheFile.exists() && 
                (System.currentTimeMillis() - cacheFile.lastModified()) < cacheMaxAgeMs) {
                Log.d(TAG, "Using cached people registry")
                cacheFile.readText()
            } else {
                Log.d(TAG, "Downloading people registry from $urlString")
                val text = URL(urlString).readText()
                cacheFile?.writeText(text)
                text
            }
            
            val people = parsePeopleCsv(csvText)
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.cricsheet"
                message = "Successfully loaded people registry"
                level = SentryLevel.INFO
                setData("playerCount", people.size)
            })
            
            CricsheetPlayerRegistry(people)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download people registry", e)
            Sentry.captureException(e)
            CricsheetPlayerRegistry()
        }
    }
    
    private fun parsePeopleCsv(csvText: String): List<CricsheetPerson> {
        val lines = csvText.lines()
        if (lines.size < 2) return emptyList()

        val colIndex = lines[0].split(",").map { it.trim() }
            .withIndex().associate { (idx, name) -> name to idx }

        return lines.drop(1).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val cols = parseCsvLine(line)
            fun col(key: String): String? = colIndex[key]?.let { cols.getOrNull(it) }
            fun nonBlank(key: String): String? = col(key)?.takeIf { it.isNotBlank() }

            val identifier = nonBlank("identifier") ?: return@mapNotNull null
            val name = col("name") ?: ""
            val uniqueName = col("unique_name") ?: name

            CricsheetPerson(
                identifier = identifier,
                name = name,
                uniqueName = uniqueName,
                keyCricbuzz = nonBlank("key_cricbuzz"),
                keyCricinfo = nonBlank("key_cricinfo"),
                keyCricinfoAlt = nonBlank("key_cricinfo_2"),
                keyBcci = nonBlank("key_bcci"),
                keyBigBash = nonBlank("key_bigbash"),
                keyCricHeroes = nonBlank("key_cricheroes"),
                keyCricHQ = nonBlank("key_crichq"),
                keyCricingIF = nonBlank("key_cricingif"),
                keyCricketArchive = nonBlank("key_cricketarchive"),
                keyNvPlay = nonBlank("key_nvplay"),
                keyOpta = nonBlank("key_opta"),
                keyPulse = nonBlank("key_pulse")
            )
        }
    }
    
    /** Handles CSV fields that may be quoted with commas inside */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    fields.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        fields.add(current.toString().trim())
        return fields
    }
    
    // MARK: - Format-Specific & Incremental Downloads
    
    /**
     * Downloads matches for a specific match type (e.g., "t20s", "odis", "tests").
     */
    suspend fun downloadByMatchType(type: String): List<CricsheetMatch> = withContext(Dispatchers.IO) {
        val urlString = "https://cricsheet.org/downloads/${type}_json.zip"
        downloadAndParseZip(urlString, "match_type_$type")
    }
    
    /**
     * Downloads matches for a specific competition (e.g., "ipl", "bbl", "psl", "cpl").
     */
    suspend fun downloadByCompetition(code: String): List<CricsheetMatch> = withContext(Dispatchers.IO) {
        val urlString = "https://cricsheet.org/downloads/${code}_json.zip"
        downloadAndParseZip(urlString, "competition_$code")
    }
    
    /**
     * Downloads recently added matches (within specified number of days: 2, 7, or 30).
     */
    suspend fun downloadRecentMatches(days: Int = 7): List<CricsheetMatch> = withContext(Dispatchers.IO) {
        val urlString = "https://cricsheet.org/downloads/recently_added_${days}_json.zip"
        downloadAndParseZip(urlString, "recent_${days}d")
    }
    
    /**
     * Downloads recently played matches (within specified number of days: 2, 7, or 30).
     */
    suspend fun downloadRecentlyPlayedMatches(days: Int = 7): List<CricsheetMatch> = withContext(Dispatchers.IO) {
        val urlString = "https://cricsheet.org/downloads/recently_played_${days}_json.zip"
        downloadAndParseZip(urlString, "recently_played_${days}d")
    }
    
    /**
     * Generic zip download + parse method used by format-specific and incremental downloads.
     */
    private suspend fun downloadAndParseZip(urlString: String, label: String): List<CricsheetMatch> {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "network.cricsheet"
            message = "Downloading $label matches"
            level = SentryLevel.INFO
            setData("url", urlString)
        })
        
        try {
            val ctx = context ?: return emptyList()
            val filesDir = ctx.filesDir
            val zipPath = File(filesDir, "${label}_json.zip")
            
            Log.d(TAG, "Downloading $label matches from $urlString")
            val connection = URL(urlString).openConnection()
            connection.getInputStream().use { input ->
                FileOutputStream(zipPath).use { output ->
                    input.copyTo(output)
                }
            }
            
            val matches = unzipAndParseAllMatches(zipPath.absolutePath, filesDir.absolutePath)
            zipPath.delete()
            
            Log.d(TAG, "Successfully parsed ${matches.size} $label matches")
            
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "network.cricsheet"
                message = "Successfully downloaded $label matches"
                level = SentryLevel.INFO
                setData("matchCount", matches.size)
            })
            
            return matches
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download $label matches from $urlString", e)
            Sentry.captureException(e)
            return emptyList()
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
