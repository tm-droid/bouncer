package com.tanush.bouncer.data

import android.util.Log
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.network.RetrofitClient
import io.sentry.Sentry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TeamDirectory {
    data class TeamInfo(val id: String, val logoUrl: String)

    private val _teams = MutableStateFlow<Map<String, TeamInfo>>(emptyMap())
    val teams: StateFlow<Map<String, TeamInfo>> = _teams.asStateFlow()

    private val API_KEY = ApiKeys.rapidApi
    private const val API_HOST = "cricket-api-free-data.p.rapidapi.com"

    suspend fun fetchTeams() {
        if (ApiKeys.rapidApi.isBlank()) {
            Log.w("TeamDirectory", "Skipping fetchTeams because RAPIDAPI_KEY is empty")
            return
        }
        try {
            val response = RetrofitClient.rapidApi.getTeams(API_KEY, API_HOST)
            val newTeams = response.response.associate {
                it.title to TeamInfo(it.id, it.image)
            }
            _teams.value = newTeams
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }
}

