package com.tanush.bouncer.services

import android.util.Log
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.monitoring.SentryAiSpans
import com.tanush.bouncer.network.HttpClientProvider
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class CommentaryFusionService(
    private val client: OkHttpClient = HttpClientProvider.sharedClient,
    private val apiUrl: String = "https://openrouter.ai/api/v1/chat/completions"
) {
    private val openRouterApiKey = ApiKeys.openRouter

    private data class ModelRoute(
        val agentName: String,
        val modelIds: List<String>,
        val temperature: Double? = null,
        val maxTokens: Int = 512
    )

    // Keep a stable fallback in each route because some preferred free models are temporary.
    private val fusionRoute = ModelRoute(
        agentName = "openrouter_commentary_fusion",
        modelIds = listOf(FUSION_PRIMARY_MODEL, STABLE_FALLBACK_MODEL),
        temperature = 0.6,
        maxTokens = 256
    )

    private val verificationRoute = ModelRoute(
        agentName = "openrouter_commentary_verification",
        modelIds = listOf(VERIFICATION_PRIMARY_MODEL, VERIFICATION_SECONDARY_MODEL, STABLE_FALLBACK_MODEL),
        temperature = 0.0,
        maxTokens = 16
    )

    private val summaryRoute = ModelRoute(
        agentName = "openrouter_commentary_summary",
        modelIds = listOf(SUMMARY_PRIMARY_MODEL, STABLE_FALLBACK_MODEL),
        temperature = 0.4,
        maxTokens = 256
    )

    // ── Permanent failure detection ──────────────────────────────────────
    // A 402 "Insufficient credits" is a billing problem, not a transient
    // network error.  Once detected we stop calling the API to avoid:
    //   • wasting latency on guaranteed-failure requests
    //   • flooding Sentry / logcat with identical 402 errors
    private val apiPermanentlyFailed = AtomicBoolean(false)
    private val permanentFailureReportedAt = AtomicLong(0L)
    private val PERMANENT_FAILURE_RE_CHECK_MS = 600_000L // re-check every 10 min
    private val apiRateLimitedUntil = AtomicLong(0L)
    private val RATE_LIMIT_RE_CHECK_MS = 1_800_000L // re-check every 30 min
    private val transientRetryPolicy = RetryPolicy(maxAttempts = 3, baseDelayMs = 1000L, multiplier = 2.0)

    /**
     * Returns true while the API is known to be in a permanent failure
     * state (e.g. 402 Insufficient credits, 401 bad key).  Allows a
     * periodic re-check so the app auto-recovers when credits are topped up.
     */
    private fun isApiDisabled(): Boolean {
        val now = System.currentTimeMillis()

        val rateLimitedUntil = apiRateLimitedUntil.get()
        if (rateLimitedUntil > now) {
            return true
        }
        if (rateLimitedUntil != 0L && apiRateLimitedUntil.compareAndSet(rateLimitedUntil, 0L)) {
            Log.i("CommentaryFusionService", "Re-enabling API after rate-limit cool-down")
        }

        if (!apiPermanentlyFailed.get()) return false
        // Allow a single re-check after the cool-down period
        if (now - permanentFailureReportedAt.get() > PERMANENT_FAILURE_RE_CHECK_MS) {
            apiPermanentlyFailed.set(false)
            Log.i("CommentaryFusionService", "Re-enabling API after cool-down period")
            return false
        }
        return true
    }

    private fun markApiRateLimited(resetAtMs: Long?, errorBody: String?) {
        val now = System.currentTimeMillis()
        val requestedDisableUntil = when {
            resetAtMs != null && resetAtMs > now -> resetAtMs.coerceAtMost(now + RATE_LIMIT_RE_CHECK_MS)
            else -> now + RATE_LIMIT_RE_CHECK_MS
        }

        var previousDisableUntil = apiRateLimitedUntil.get()
        while (requestedDisableUntil > previousDisableUntil) {
            if (apiRateLimitedUntil.compareAndSet(previousDisableUntil, requestedDisableUntil)) {
                val retryInMinutes = ((requestedDisableUntil - now) / 60_000L).coerceAtLeast(1L)
                Log.w(
                    "CommentaryFusionService",
                    "API rate limited (HTTP 429) — disabling for ${retryInMinutes}m"
                )

                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "service.commentary"
                    message = "OpenRouter API disabled — rate limited"
                    level = SentryLevel.WARNING
                    setData("errorBody", errorBody?.take(200) ?: "null")
                    setData("rateLimitedUntil", requestedDisableUntil)
                    setData("cooldownMs", requestedDisableUntil - now)
                })
                return
            }
            previousDisableUntil = apiRateLimitedUntil.get()
        }
    }

    private fun extractRateLimitResetMs(errorBody: String?, resetHeader: String?): Long? {
        val bodyReset = if (errorBody.isNullOrBlank()) null else {
            runCatching {
                JSONObject(errorBody)
                    .optJSONObject("error")
                    ?.optJSONObject("metadata")
                    ?.optJSONObject("headers")
                    ?.optString("X-RateLimit-Reset")
                    ?.takeIf { it.isNotBlank() }
                    ?.toLongOrNull()
            }.getOrNull()
        }

        return resetHeader?.trim()?.toLongOrNull() ?: bodyReset
    }

    private fun markApiPermanentlyFailed(statusCode: Int, errorBody: String?) {
        if (apiPermanentlyFailed.compareAndSet(false, true)) {
            val now = System.currentTimeMillis()
            permanentFailureReportedAt.set(now)
            Log.e("CommentaryFusionService", "API permanently failed (HTTP $statusCode) — disabling for ${PERMANENT_FAILURE_RE_CHECK_MS / 1000}s")

            // Report to Sentry exactly once
            Sentry.captureMessage("OpenRouter API permanently failed: HTTP $statusCode")
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.commentary"
                message = "OpenRouter API disabled — permanent failure"
                level = SentryLevel.ERROR
                setData("statusCode", statusCode)
                setData("errorBody", errorBody?.take(200) ?: "null")
                setData("cooldownMs", PERMANENT_FAILURE_RE_CHECK_MS)
            })
        }
    }

    companion object {
        private const val FUSION_PRIMARY_MODEL = "inclusionai/ling-2.6-1t:free"
        private const val SUMMARY_PRIMARY_MODEL = "inclusionai/ling-2.6-1t:free"
        private const val VERIFICATION_PRIMARY_MODEL = "tencent/hy3-preview:free"
        private const val VERIFICATION_SECONDARY_MODEL = "openrouter/owl-alpha"
        private const val STABLE_FALLBACK_MODEL = "google/gemma-3-12b-it:free"

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
    {previous_balls_context}
    
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

        const val VERIFICATION_PROMPT_TEMPLATE = """
    You are a cricket commentary fact-checker. Your job is to verify that an AI-generated commentary line is accurate and does not hallucinate.
    
    Original source commentary 1: {snippet1}
    Original source commentary 2: {snippet2}
    
    AI-generated commentary: {ai_output}
    
    Ball number: {ball_number}
    Bowler: {bowler}
    Batsman: {batsman}
    
    Check the AI-generated commentary against the original sources and answer ONLY with one of these two words:
    - "PASS" if the AI commentary is accurate, uses only correct player names from the sources, and does not invent any details not present in the original sources.
    - "FAIL" if the AI commentary contains hallucinated player names, invented match details, incorrect ball descriptions, or information not supported by the original sources.
    
    Your response must be exactly one word: PASS or FAIL.
    """

        const val SINGLE_SOURCE_PROMPT_TEMPLATE = """
    You are a professional cricket commentator. Given the following ball-by-ball input and recent match context, write a vivid, engaging, and concise commentary line for this specific ball, as if you are commentating live.
    
    Ball commentary: {snippet}
    Ball number: {ball_number}
    Bowler: {bowler}
    Batsman: {batsman}
    Current score/context: {score_context}
    {previous_balls_context}
    
    Instructions:
    - Start your commentary line with the ball number (e.g., 18.5: ...).
    - Use only the player names provided above or in the input/context. Do not invent or substitute player names.
    - Do not invent details about the play, score, or situation if not present in the input/context.
    - Paraphrase the input into a natural, commentator-style sentence.
    - If the recent context suggests a shift in momentum, pressure, or a notable trend, you may add a brief, context-aware narrative or emotional remark (e.g., "It's not looking good for the Indian team here").
    - Only add such narrative remarks if they fit naturally and are supported by the recent context—do not force them.
    - Otherwise, focus on clear, vivid, and accurate description of the current ball.
    - Avoid repetition and keep it concise.
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

    /**
     * Verifies AI-generated commentary against original sources using a second
     * Gemma call.  Returns true if the output passes the hallucination check.
     */
    private suspend fun verifyCommentary(
        aiOutput: String,
        snippet1: String,
        snippet2: String,
        ballNumber: String,
        bowler: String,
        batsman: String
    ): Boolean {
        val verificationPrompt = VERIFICATION_PROMPT_TEMPLATE
            .replace("{snippet1}", snippet1)
            .replace("{snippet2}", snippet2)
            .replace("{ai_output}", aiOutput)
            .replace("{ball_number}", ballNumber)
            .replace("{bowler}", bowler)
            .replace("{batsman}", batsman)

        val verdict = callOpenRouter(verificationRoute, verificationPrompt)
        val passed = verdict?.trim()?.uppercase()?.startsWith("PASS") == true
        if (!passed) {
            Log.w("CommentaryFusionService", "Hallucination check FAILED — verdict: $verdict")
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.commentary"
                message = "Hallucination verification failed"
                level = SentryLevel.WARNING
                setData("verdict", verdict ?: "null")
                setData("aiOutput", aiOutput.take(200))
            })
        }
        return passed
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
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.commentary"
            message = "Fusing commentary snippets"
            level = SentryLevel.INFO
            setData("matchId", matchId)
            setData("over", over)
            setData("ballNumber", ballNumber)
        })
        
        // Generate cache key based on context hash
        val contextHash = (snippet1 + snippet2 + oversSummary).hashCode().toString()
        val cacheKey = "ai_commentary:$matchId:$over:$contextHash"
        
        // Check cache first
        val cached = AICacheService.getCommentary(cacheKey)
        if (cached != null) {
            return cached
        }

        // Build previous balls context from rolling buffer
        val previousBalls = getPreviousBalls(matchId)
        val previousContext = if (previousBalls.isEmpty()) ""
            else "\nPrevious balls: ${previousBalls.joinToString(", ")}"

        // Use single-source prompt when only one snippet is available
        val prompt = if (snippet1.isEmpty() || snippet2.isEmpty()) {
            SINGLE_SOURCE_PROMPT_TEMPLATE
                .replace("{snippet}", snippet1.ifEmpty { snippet2 })
                .replace("{ball_number}", ballNumber)
                .replace("{bowler}", bowler)
                .replace("{batsman}", batsman)
                .replace("{score_context}", scoreContext)
                .replace("{previous_balls_context}", previousContext)
        } else {
            FUSION_PROMPT_TEMPLATE
                .replace("{snippet1}", snippet1)
                .replace("{snippet2}", snippet2)
                .replace("{ball_number}", ballNumber)
                .replace("{bowler}", bowler)
                .replace("{batsman}", batsman)
                .replace("{score_context}", scoreContext)
                .replace("{overs_summary}", oversSummary)
                .replace("{previous_balls_context}", previousContext)
        }

            val result = callOpenRouter(fusionRoute, prompt)
            ?: return "[AI Fused] $snippet1 / $snippet2" // Fallback if API fails

        // Hallucination self-check: verify output against original sources
        val verified = verifyCommentary(result, snippet1, snippet2, ballNumber, bowler, batsman)
        if (!verified) {
            // Verification failed — fall back to best raw snippet
            val fallback = snippet1.ifEmpty { snippet2 }
            Log.w("CommentaryFusionService", "Using raw snippet fallback after failed verification")
            AICacheService.setCommentary(fallback, cacheKey)
            return fallback
        }
        
        // Cache the verified result
        AICacheService.setCommentary(result, cacheKey)
        
        return result
    }

    suspend fun summarizeOversWithAI(commentaryLines: List<String>, matchId: String, over: Int): String {
        if (commentaryLines.isEmpty()) return "No recent commentary available."
        
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service.commentary"
            message = "Summarizing overs with AI"
            level = SentryLevel.INFO
            setData("matchId", matchId)
            setData("over", over)
            setData("lineCount", commentaryLines.size)
        })

        // Cap at 18 balls (3 overs) for focused summaries
        val lines = commentaryLines.take(18)
        val context = lines.joinToString(" ")
        
        // Generate cache key based on context hash
        val contextHash = context.hashCode().toString()
        val cacheKey = "ai_summary:$matchId:$over:$contextHash"
        
        // Check cache first
        val cached = AICacheService.getCommentary(cacheKey)
        if (cached != null) {
            return cached
        }

        // Number lines for clearer AI comprehension
        val numbered = lines.mapIndexed { idx, line -> "${idx + 1}. $line" }.joinToString("\n")
        
        val prompt = """
        You are a professional cricket commentator. Summarize the following ${lines.size} ball-by-ball commentary lines into a single, engaging paragraph, highlighting key events and players.
        
        $numbered
        """
        
        val result = callOpenRouter(summaryRoute, prompt) ?: "[Summary unavailable]"
        
        // Cache the result
        AICacheService.setCommentary(result, cacheKey)
        
        return result
    }

    private suspend fun callOpenRouter(route: ModelRoute, prompt: String): String? {
        route.modelIds.forEachIndexed { index, modelId ->
            if (index > 0) {
                Log.i("CommentaryFusionService", "Retrying ${route.agentName} with fallback model: $modelId")
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "service.commentary"
                    message = "Retrying OpenRouter call with fallback model"
                    level = SentryLevel.INFO
                    setData("agentName", route.agentName)
                    setData("model", modelId)
                    setData("attempt", index + 1)
                })
            }

            val result = callOpenRouter(
                model = modelId,
                prompt = prompt,
                agentName = route.agentName,
                temperature = route.temperature,
                maxTokens = route.maxTokens
            )
            if (result != null) return result
        }

        return null
    }

    private suspend fun callOpenRouter(
        model: String,
        prompt: String,
        agentName: String,
        temperature: Double? = null,
        maxTokens: Int = 512
    ): String? = withContext(Dispatchers.IO) {
        // ── Short-circuit if API is known-broken ─────────────────────────
        if (isApiDisabled()) {
            Log.d("CommentaryFusionService", "API disabled (permanent failure) — returning null")
            return@withContext null
        }

        SentryAiSpans.withAgentInvocation(agentName = agentName, model = model) { tx ->
            Log.d("CommentaryFusionService", "Using model: $model")
            tx.setData("gen_ai.request.model", model)
            tx.setData("gen_ai.request.provider", "openrouter")
            tx.setData("gen_ai.request.prompt_length", prompt.length)
            temperature?.let { tx.setData("gen_ai.request.temperature", it) }
            tx.setData("gen_ai.request.max_tokens", maxTokens)

            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "service.commentary"
                message = "Calling OpenRouter API"
                level = SentryLevel.INFO
                setData("model", model)
                setData("agentName", agentName)
            })

            val jsonBody = JSONObject()
            jsonBody.put("model", model)
            val messagesArray = JSONArray()
            val messageObj = JSONObject()
            messageObj.put("role", "user")
            messageObj.put("content", prompt)
            messagesArray.put(messageObj)
            jsonBody.put("messages", messagesArray)
            jsonBody.put("max_tokens", maxTokens)
            temperature?.let { jsonBody.put("temperature", it) }

            val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(apiUrl)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $openRouterApiKey")
                .addHeader("HTTP-Referer", "bouncer-app")
                .build()

            try {
                transientRetryPolicy.withRetry(
                    isRetryable = { it is IOException },
                    onRetry = { attempt, delayMs, cause ->
                        Log.w("CommentaryFusionService", "Retry $attempt for $model after ${delayMs}ms: ${cause.message}")
                        Sentry.addBreadcrumb(Breadcrumb().apply {
                            category = "service.commentary"
                            message = "Retrying OpenRouter call after transient failure"
                            level = SentryLevel.WARNING
                            setData("model", model)
                            setData("attempt", attempt)
                            setData("delayMs", delayMs)
                            setData("cause", cause.message ?: "unknown")
                        })
                    }
                ) {
                    client.newCall(request).execute().use { response ->
                        tx.setData("gen_ai.response.http_status", response.code)
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string()
                            val code = response.code
                            val resetHeader = response.header("X-RateLimit-Reset")
                            Sentry.metrics().count("ai.request.error", 1.0)
                            Sentry.logger().warn("OpenRouter request failed: status=%s", code.toString())

                            if (code == 401 || code == 402) {
                                Log.e("CommentaryFusionService", "API call failed with code $code: $errorBody")
                                markApiPermanentlyFailed(code, errorBody)
                                return@withRetry null
                            }

                            if (code == 429) {
                                Log.w("CommentaryFusionService", "API rate limited with code 429: $errorBody")
                                markApiRateLimited(
                                    resetAtMs = extractRateLimitResetMs(errorBody, resetHeader),
                                    errorBody = errorBody
                                )
                                return@withRetry null
                            }

                            Log.e("CommentaryFusionService", "API call failed with code $code: $errorBody")
                            // Transient server error — throw to trigger retry
                            throw TransientApiException(code, errorBody?.take(200))
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
                                    tx.setData("gen_ai.usage.output_chars", content.length)
                                    Sentry.metrics().count("ai.request.success", 1.0)
                                    Log.d("CommentaryFusionService", "AI response received.")
                                    return@withRetry content.trim()
                                }
                            }
                        }
                        null
                    }
                }
            } catch (e: TransientApiException) {
                Log.e("CommentaryFusionService", "API failed after ${transientRetryPolicy.maxAttempts} retries: HTTP ${e.code}")
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "service.commentary"
                    message = "OpenRouter transient failure exhausted retries"
                    level = SentryLevel.ERROR
                    setData("statusCode", e.code)
                    setData("errorBody", e.body ?: "null")
                    setData("attempts", transientRetryPolicy.maxAttempts)
                })
                Sentry.captureMessage("OpenRouter transient failure: HTTP ${e.code} after ${transientRetryPolicy.maxAttempts} attempts")
                Sentry.metrics().count("ai.request.error", 1.0)
                null
            } catch (e: IOException) {
                Log.e("CommentaryFusionService", "API call failed after ${transientRetryPolicy.maxAttempts} retries", e)
                if (e.message != "Canceled") {
                    Sentry.captureException(e)
                }
                Sentry.metrics().count("ai.request.io_error", 1.0)
                null
            } catch (e: Exception) {
                Log.e("CommentaryFusionService", "Unexpected error in API call", e)
                Sentry.captureException(e)
                Sentry.metrics().count("ai.request.unexpected_error", 1.0)
                null
            }
        }
    }
}

internal class TransientApiException(val code: Int, val body: String?) : IOException("HTTP $code transient error")
