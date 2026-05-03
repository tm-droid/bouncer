package com.tanush.bouncer.network

import com.tanush.bouncer.models.CricbuzzNewsResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

/**
 * Retrofit interface for the Cricbuzz RapidAPI news endpoint.
 * Base URL: https://cricbuzz-cricket.p.rapidapi.com/
 */
interface CricbuzzNewsApi {
    @GET("news/v1/index")
    suspend fun getNewsIndex(
        @Header("x-rapidapi-host") host: String = "cricbuzz-cricket.p.rapidapi.com",
        @Header("x-rapidapi-key") key: String = com.tanush.bouncer.BuildConfig.RAPIDAPI_KEY
    ): Response<CricbuzzNewsResponse>
}
