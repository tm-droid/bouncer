package com.tanush.bouncer.network

import com.tanush.bouncer.network.models.SanwebMatchResponse
import com.tanush.bouncer.network.models.SanwebMatchesListResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface VercelApi {
    @GET("matches")
    suspend fun getMatches(@Query("type") type: String): SanwebMatchesListResponse

    @GET("score")
    suspend fun getMatchScore(@Query("id") id: String): SanwebMatchResponse
}

