package com.tanush.bouncer.managers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.tanush.bouncer.BuildConfig
import io.sentry.Sentry

/**
 * Manager for fetching and caching team data from RapidAPI
 * Mirrors the iOS TeamDirectory implementation
 */
object TeamDirectory {
    private const val TAG = "TeamDirectory"
    private val API_KEY: String get() = BuildConfig.RAPIDAPI_KEY
    private const val API_HOST = "cricket-api-free-data.p.rapidapi.com"
    private const val ENDPOINT = "https://cricket-api-free-data.p.rapidapi.com/cricket-teams"
    
    data class TeamInfo(
        val id: String,
        val logoUrl: String
    )
    
    private val _teams = MutableStateFlow<Map<String, TeamInfo>>(emptyMap())
    val teams: StateFlow<Map<String, TeamInfo>> = _teams.asStateFlow()
    
    private val client = OkHttpClient()
    
    /**
     * Fetch teams from RapidAPI and update the teams map
     */
    suspend fun fetchTeams() = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("X-RapidAPI-Key", API_KEY)
                .addHeader("X-RapidAPI-Host", API_HOST)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to fetch teams: ${response.code}")
                    return@withContext
                }
                
                val responseBody = response.body?.string() ?: return@withContext
                val json = JSONObject(responseBody)
                val teamsArray = json.optJSONArray("response") ?: return@withContext
                
                val newTeams = mutableMapOf<String, TeamInfo>()
                for (i in 0 until teamsArray.length()) {
                    val team = teamsArray.getJSONObject(i)
                    val id = team.optString("id", "")
                    val title = team.optString("title", "")
                    val image = team.optString("image", "")
                    
                    if (title.isNotEmpty()) {
                        newTeams[title] = TeamInfo(id = id, logoUrl = image)
                    }
                }
                
                _teams.value = newTeams
                Log.d(TAG, "Successfully fetched ${newTeams.size} teams")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch teams", e)
        }
    }
    
    /**
     * Get team info by team name
     */
    fun getTeamInfo(teamName: String): TeamInfo? {
        try {
            return _teams.value[teamName]
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Get logo URL for a team name
     */
    fun getLogoUrl(teamName: String): String? {
        try {
            return _teams.value[teamName]?.logoUrl
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    /**
     * Get team ID for a team name
     */
    fun getTeamId(teamName: String): String? {
        try {
            return _teams.value[teamName]?.id
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}
