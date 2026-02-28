package com.tanush.bouncer.data

import com.tanush.bouncer.BuildConfig
import com.tanush.bouncer.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TeamDirectory {
    data class TeamInfo(val id: String, val logoUrl: String)

    private val _teams = MutableStateFlow<Map<String, TeamInfo>>(emptyMap())
    val teams: StateFlow<Map<String, TeamInfo>> = _teams.asStateFlow()

    // API key loaded from BuildConfig (set in local.properties)
    private val API_KEY = BuildConfig.RAPIDAPI_KEY
    private const val API_HOST = "cricket-api-free-data.p.rapidapi.com"

    suspend fun fetchTeams() {
        try {
            val response = RetrofitClient.rapidApi.getTeams(API_KEY, API_HOST)
            val newTeams = response.response.associate {
                it.title to TeamInfo(it.id, it.image)
            }
            _teams.value = newTeams
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

