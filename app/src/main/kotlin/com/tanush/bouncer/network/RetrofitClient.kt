package com.tanush.bouncer.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL_VERCEL = "https://api-iota-jade.vercel.app/"
    private const val BASE_URL_RAPID = "https://cricket-api-free-data.p.rapidapi.com/"
    // Local development API (Android emulator uses 10.0.2.2 to reach host localhost)
    private const val BASE_URL_LOCAL = "http://10.0.2.2:8000/"

    private fun createOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(logging)
            .build()
    }

    private val sharedClient by lazy { createOkHttpClient() }

    val vercelApi: VercelApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_VERCEL)
            .client(sharedClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VercelApi::class.java)
    }

    val rapidApi: RapidApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_RAPID)
            .client(sharedClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RapidApi::class.java)
    }

    // Local API client used for development and testing (mock server)
    val localApi: LocalApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_LOCAL)
            .client(sharedClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LocalApi::class.java)
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

