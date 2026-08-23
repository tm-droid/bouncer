package com.tanush.bouncer.network

import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.tanush.bouncer.data.AppJson
import retrofit2.Retrofit

object RetrofitClient {
    // Local development API (Android emulator uses 10.0.2.2 to reach host localhost)
    private const val BASE_URL_LOCAL = "http://10.0.2.2:8000/"

    // Share one Retrofit per backend to reduce initialization overhead.
    private fun retrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(HttpClientProvider.sharedClient)
            // kotlinx.serialization rather than Gson: Gson needed reflection over every DTO,
            // which is what the blanket `-keep class com.tanush.bouncer.**` rule in
            // proguard-rules.pro existed for — and that rule turned R8 into a no-op app-wide.
            .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    // Local API client used for development and testing (mock server)
    val localApi: LocalApi by lazy {
        retrofit(BASE_URL_LOCAL)
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
@Serializable
data class ProbabilityPointDto(
    val over: Float,
    val team1Probability: Float,
    val team2Probability: Float,
    val keyEvent: String? = null
)

@Serializable
data class CommentaryDto(
    val latestCommentary: String
)
