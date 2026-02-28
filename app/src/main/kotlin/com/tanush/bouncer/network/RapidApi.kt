package com.tanush.bouncer.network

import retrofit2.http.GET
import retrofit2.http.Header

interface RapidApi {
    @GET("cricket-teams")
    suspend fun getTeams(
        @Header("X-RapidAPI-Key") apiKey: String,
        @Header("X-RapidAPI-Host") apiHost: String
    ): RapidApiTeamResponse
}

data class RapidApiTeamResponse(
    val response: List<RapidApiTeam>
)

data class RapidApiTeam(
    val id: String,
    val title: String,
    val image: String
)

