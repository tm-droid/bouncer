package com.tanush.bouncer.services

import android.util.Log
import com.tanush.bouncer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class CommentaryFusionService {
    // OpenRouter.ai settings
    private val apiUrl = "https://openrouter.ai/api/v1/chat/completions"
    // Using verified OpenRouter model: Claude 3.5 Sonnet
    private val model = "anthropic/claude-3.5-sonnet"
    // API key loaded from BuildConfig (set in local.properties)
    private val openRouterApiKey = BuildConfig.OPENROUTER_API_KEY

    private val client = com.tanush.bouncer.network.HttpClientProvider.sharedClient

    companion object {
        const val FUSION_PROMPT_TEMPLATE = """
    You are a professional cricket commentator.
    Given the following ball-by-ball input and recent match context, write a single, natural-sounding commentary line for the current ball.
    
    Ball-by-ball input from two sources:
    Commentary from source 1: {snippet1}
    Commentary from source 2: {snippet2}
    
    Ball number: {ball_number}
    Bowler: {bowler}
    Batsman: {batsman}
    Current score/context: {score_context}
    
    Recent match context (last 3 overs summary):
    {overs_summary}
    
    Instructions:
    - Start your commentary line with the ball number (e.g., 18.5: ...).
    - Use only the player names provided above or in the input/context. Do not invent or substitute player names.
    - Do not invent details about the play, score, or situation if not present in the input/context.
    - Paraphrase the ball input into a concise, engaging, and human-like sentence, as a real commentator would say on TV or radio.
    - If the match context (from the summary) suggests a shift in momentum, pressure, or a notable trend, you may add a brief, context-aware narrative or emotional remark (e.g., "It's not looking good for the Indian team here" or "The bowlers are on top at the moment").
    - Only add such narrative remarks if they fit naturally and are supported by the recent context—do not force them.
    - Otherwise, focus on clear, vivid, and accurate description of the current ball.
    - Avoid repetition and keep it concise.
    - Do not simply merge the two sources; pick the best or rephrase for freshness.
    """
    }

    // Rolling buffer of previous balls for each match
    private val previousBallsBuffer = mutableMapOf<String, MutableList<String>>() // matchId: [ball1, ball2, ...]
    private val maxBufferSize = 6

    fun addBallToBuffer(matchId: String, ball: String) {
        val buffer = previousBallsBuffer.getOrPut(matchId) { mutableListOf() }
        buffer.add(ball)
        if (buffer.size > maxBufferSize) {
            buffer.removeAt(0)
        }
    }

    fun getPreviousBalls(matchId: String): List<String> {
        return previousBallsBuffer[matchId] ?: emptyList()
    }

    suspend fun fuse(
        snippet1: String,
        snippet2: String,
        oversSummary: String,
        matchId: String,
        over: Int,
        ballNumber: String,
        bowler: String,
        batsman: String,
        scoreContext: String
    ): String {
        // Generate cache key based on context hash
        val contextHash = (snippet1 + snippet2 + oversSummary).hashCode().toString()
        val cacheKey = "ai_commentary:$matchId:$over:$contextHash"
        
        // Check cache first
        val cached = AICacheService.getCommentary(cacheKey)
        if (cached != null) {
            return cached
        }
        
        val prompt = FUSION_PROMPT_TEMPLATE
            .replace("{snippet1}", snippet1)
            .replace("{snippet2}", snippet2)
            .replace("{ball_number}", ballNumber)
            .replace("{bowler}", bowler)
            .replace("{batsman}", batsman)
            .replace("{score_context}", scoreContext)
            .replace("{overs_summary}", oversSummary)

        val result = callOpenRouter(model, prompt) ?: snippet1 // Fallback to snippet1
        
        // Cache the result
        AICacheService.setCommentary(result, cacheKey)
        
        return result
    }

    suspend fun summarizeOversWithAI(commentaryLines: List<String>, matchId: String, over: Int): String {
        if (commentaryLines.isEmpty()) return "No recent commentary available."
        
        // Generate cache key based on context hash
        val context = commentaryLines.joinToString(" ")
        val contextHash = context.hashCode().toString()
        val cacheKey = "ai_summary:$matchId:$over:$contextHash"
        
        // Check cache first
        val cached = AICacheService.getCommentary(cacheKey)
        if (cached != null) {
            return cached
        }
        
        val prompt = """
        Summarize the following cricket commentary lines (representing the last few overs) into a concise paragraph describing the flow of the match, key events, and current momentum.
        
        Commentary lines:
        ${commentaryLines.joinToString("\n")}
        """
        
        val result = callOpenRouter(model, prompt) ?: "Summary unavailable."
        
        // Cache the result
        AICacheService.setCommentary(result, cacheKey)
        
        return result
    }

    private suspend fun callOpenRouter(model: String, prompt: String): String? = withContext(Dispatchers.IO) {
        Log.d("CommentaryFusionService", "Using model: $model")
        
        val jsonBody = JSONObject()
        jsonBody.put("model", model)
        val messages = JSONArray()
        val message = JSONObject()
        message.put("role", "user")
        message.put("content", prompt)
        messages.put(message)
        jsonBody.put("messages", messages)
        jsonBody.put("max_tokens", 512)

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(apiUrl)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $openRouterApiKey")
            .addHeader("HTTP-Referer", "bouncer-app") // Optional
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("CommentaryFusionService", "API call failed with code ${response.code}: ${response.body?.string()}")
                    return@withContext null
                }

                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    val choices = jsonResponse.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val firstChoice = choices.getJSONObject(0)
                        val messageObj = firstChoice.optJSONObject("message")
                        val content = messageObj?.optString("content")
                        if (content != null) {
                            Log.d("CommentaryFusionService", "AI response received.")
                            return@withContext content.trim()
                        }
                    }
                }
                return@withContext null
            }
        } catch (e: IOException) {
            Log.e("CommentaryFusionService", "API call failed", e)
            return@withContext null
        }
    }
}
