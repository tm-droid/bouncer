package com.tanush.bouncer.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL_VERCEL = "https://api-iota-jade.vercel.app/"
    private const val BASE_URL_RAPID = "https://cricket-api-free-data.p.rapidapi.com/"
    private const val BASE_URL_CRICBUZZ_RAPID = "https://cricbuzz-cricket.p.rapidapi.com/"
    // Local development API (Android emulator uses 10.0.2.2 to reach host localhost)
    private const val BASE_URL_LOCAL = "http://10.0.2.2:8000/"

    val vercelApi: VercelApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_VERCEL)
            .client(HttpClientProvider.sharedClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VercelApi::class.java)
    }

    val rapidApi: RapidApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_RAPID)
            .client(HttpClientProvider.sharedClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RapidApi::class.java)
    }

    // Local API client used for development and testing (mock server)
    val localApi: LocalApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_LOCAL)
            .client(HttpClientProvider.sharedClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LocalApi::class.java)
    }

    // Cricbuzz News API via RapidAPI
    val cricbuzzNewsApi: CricbuzzNewsApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_CRICBUZZ_RAPID)
            .client(HttpClientProvider.sharedClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CricbuzzNewsApi::class.java)
    }
}

// Local API definitions (used for dev/test only)
interface LocalApi {
    @retrofit2.http.GET("match/{id}/probability")
    suspend fun getProbability(@retrofit2.http.Path("id") id: String): List<ProbabilityPointDto>

    @retrofit2.http.GET("match/{id}/commentary")
    suspend fun getCommentary(@retrofit2.http.Path("id") id: String): CommentaryDto
}

// DTOs returned by the local/mock API
data class ProbabilityPointDto(
    val over: Float,
    val team1Probability: Float,
    val team2Probability: Float,
    val keyEvent: String? = null
)

data class CommentaryDto(
    val latestCommentary: String
)

