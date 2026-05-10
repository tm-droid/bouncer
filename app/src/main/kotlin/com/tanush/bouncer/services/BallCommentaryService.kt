package com.tanush.bouncer.services

import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.network.HttpClientProvider
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object BallCommentaryService {

    const val FALLBACK_COMMENTARY = "Ball delivered. Play continues."

    private data class Provider(val name: String, val url: String, val key: String, val model: String)

    private val providers = listOf(
        Provider("Groq",      "https://api.groq.com/openai/v1/chat/completions",    ApiKeys.groq,      "llama-3.3-70b-versatile"),
        Provider("Cerebras",  "https://api.cerebras.ai/v1/chat/completions",         ApiKeys.cerebras,  "llama-3.3-70b"),
        Provider("SambaNova", "https://api.sambanova.ai/v1/chat/completions",        ApiKeys.sambaNova, "Meta-Llama-3.3-70B-Instruct"),
    )

    suspend fun generateCommentary(matchId: String, ballNumber: String, rawSnippet: String): String =
        withContext(Dispatchers.IO) {
            val cacheKey = "ball_ai:$matchId:$ballNumber"
            AICacheService.getCommentary(cacheKey)?.let { return@withContext it }

            val prompt = "You are a cricket commentator. One punchy sentence, max 15 words.\n" +
                "Ball $ballNumber: $rawSnippet\nCommentary:"

            for (provider in providers) {
                if (provider.key.isEmpty()) continue
                try {
                    val result = callProvider(provider, prompt)
                    AICacheService.setCommentary(result, cacheKey)
                    return@withContext result
                } catch (e: Exception) {
                    Sentry.addBreadcrumb("BallCommentaryService: ${provider.name} failed — ${e.message}")
                }
            }
            FALLBACK_COMMENTARY
        }

    private fun callProvider(provider: Provider, prompt: String): String {
        val body = JSONObject().apply {
            put("model", provider.model)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
            put("max_tokens", 50)
            put("temperature", 0.7)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(provider.url)
            .header("Authorization", "Bearer ${provider.key}")
            .post(body)
            .build()

        HttpClientProvider.sharedClient.newCall(request).execute().use { response ->
            if (response.code == 429) throw IOException("Rate limited (429)")
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val content = JSONObject(response.body!!.string())
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
            if (content.isEmpty()) throw IOException("Empty content from ${provider.name}")
            return content
        }
    }
}
