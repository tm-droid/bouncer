package com.tanush.bouncer.network

import com.tanush.bouncer.models.CricbuzzNewsResponse
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
        @Header("x-rapidapi-key") key: String = "fe2efb208fmshb18e935ea93e3c4p10c19ejsn28178ccd66fc"
    ): CricbuzzNewsResponse
}
