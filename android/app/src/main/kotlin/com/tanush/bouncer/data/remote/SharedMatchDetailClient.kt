package com.tanush.bouncer.data.remote

import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.data.SingleFlight
import com.tanush.bouncer.models.MatchStatusClassifier
import com.tanush.bouncer.models.StatusSignal
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.network.HttpClientProvider
import com.tanush.bouncer.utils.startsWithBallLabel
import com.tanush.bouncer.utils.BoundedLruCache
import io.appwrite.exceptions.AppwriteException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

internal const val SHARED_DETAIL_TIMEOUT_MS = 20_000L

@Serializable
data class DetailAvailability(
    val available: Boolean = false,
    val fetchedAt: Long? = null,
    val stale: Boolean = false,
)

@Serializable
data class RawDetailCommentary(
    val commText: String = "",
    val source: String = "",
    val aiGenerated: Boolean = false,
    val ballMetric: RawBallMetric? = null,
)

@Serializable
data class RawBallMetric(val label: String = "", val over: Int? = null, val ball: Int? = null)

@Serializable
data class SharedMatchDetail(
    val schemaVersion: Int,
    val matchId: String,
    val state: String = "",
    val detail: MatchLiveDetailPayload? = null,
    val scorecard: Scorecard? = null,
    val commentary: List<RawDetailCommentary>? = null,
    val availability: Map<String, DetailAvailability> = emptyMap(),
    val retryAfterSeconds: Int = 0,
) {
    fun detailSnapshot(): LiveDetailSnapshot? {
        val age = availability["detail"]?.takeIf { it.available } ?: return null
        // Scorecard availability belongs to its own section, including on legacy seed payloads.
        val payload = detail?.copy(scorecard = null) ?: return null
        val terminal = state.equals("Complete", true) || state.equals("Completed", true)
        val confirmed = terminal && !age.stale && MatchStatusClassifier.classify(payload.status) == StatusSignal.TERMINAL
        return LiveDetailSnapshot(payload, age.fetchedAt, confirmed, age.stale)
    }

    fun scorecardSnapshot(): ScorecardSnapshot? {
        val age = availability["scorecard"]?.takeIf { it.available } ?: return null
        val card = scorecard?.takeIf { it.innings.isNotEmpty() } ?: return null
        return ScorecardSnapshot(card, age.fetchedAt, age.stale)
    }

    fun availableScorecard(): Scorecard? = scorecardSnapshot()?.scorecard

    fun rawCommentary(): List<String> =
        commentary.orEmpty().takeIf { availability["commentary"]?.available == true }.orEmpty()
            .filter { it.source == "cricbuzz" && !it.aiGenerated && it.commText.isNotBlank() }
            .map { line ->
                val label = line.ballMetric?.label.orEmpty()
                if (label.isBlank() || startsWithBallLabel(line.commText)) line.commText else "$label: ${line.commText}"
            }

}

/** Cache before single-flight; failures propagate to the screen's archive/stale fallback. */
internal class SharedDetailRepository(
    private val fetch: suspend (String) -> SharedMatchDetail,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Cached(val value: SharedMatchDetail, val expiresAt: Long)
    private val cache = BoundedLruCache<String, Cached>(40)
    private val flight = SingleFlight<String, SharedMatchDetail>()

    private fun cached(matchId: String): SharedMatchDetail? = synchronized(cache) {
        cache[matchId]?.takeIf { now() < it.expiresAt }?.value
    }

    suspend fun get(matchId: String): SharedMatchDetail {
        require(matchId.matches(Regex("[0-9]{1,12}")))
        cached(matchId)?.let { return it }
        return flight.run(matchId) {
            cached(matchId) ?: fetch(matchId).also { value ->
                require(value.schemaVersion == 1 && value.matchId == matchId) { "Invalid match-detail response" }
                synchronized(cache) { cache[matchId] = Cached(value, expiresAt(value)) }
            }
        }
    }

    private fun expiresAt(value: SharedMatchDetail): Long {
        // Matches the backend: any nonterminal/nonupcoming state is live, including breaks.
        val settled = Regex("complete|result|terminal", RegexOption.IGNORE_CASE)
        val upcoming = Regex("preview|upcoming|scheduled|pre.match", RegexOption.IGNORE_CASE)
        val ttl = if (settled.containsMatchIn(value.state) || upcoming.containsMatchIn(value.state)) 600_000L else 60_000L
        val sectionExpiry = value.availability.values.filter { it.available }.minOfOrNull {
            if (it.stale || it.fetchedAt == null) now() else it.fetchedAt + ttl
        } ?: now()
        return maxOf(sectionExpiry, now() + value.retryAfterSeconds.coerceIn(0, 90) * 1_000L)
    }

}

object SharedMatchDetailClient {
    private val repository = SharedDetailRepository(::fetch)
    private val http by lazy {
        HttpClientProvider.sharedClient.newBuilder()
            .callTimeout(SHARED_DETAIL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(SHARED_DETAIL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    suspend fun get(matchId: String): SharedMatchDetail = repository.get(matchId)

    private suspend fun fetch(matchId: String): SharedMatchDetail {
        val route = "${ApiKeys.appwriteApiFunctionId}/match-detail"
        val key = "fn.$route"
        check(AppwriteReadGuard.checkAndCount(key, AppwriteReadGuard.Quota.EXECUTIONS) == null) {
            "Match detail execution budget unavailable"
        }
        val body = buildJsonObject {
            put("body", buildJsonObject { put("matchId", matchId) }.toString())
            put("async", false)
            put("path", "/match-detail")
            put("method", "POST")
            put("headers", buildJsonObject { put("x-bouncer-device", DeviceIdentity.id) })
        }
        val request = Request.Builder()
            .url("${ApiKeys.appwriteEndpoint.trimEnd('/')}/functions/${ApiKeys.appwriteApiFunctionId}/executions")
            .header("X-Appwrite-Project", ApiKeys.appwriteProjectId)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            val execution = http.newCall(request).awaitBody()
            AppwriteUsageTracker.recordFunctionExecution(route)
            val wire = AppJson.decodeFromString<DetailExecution>(execution)
            wire.requireSuccess()
            AppwriteReadGuard.recordSuccess(quota = AppwriteReadGuard.Quota.EXECUTIONS)
            return AppJson.decodeFromString(wire.responseBody)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppwriteReadGuard.recordFailure(key, e, AppwriteReadGuard.Quota.EXECUTIONS)
            throw e
        }
    }
}

@Serializable
private data class DetailExecution(val responseStatusCode: Int = 0, val responseBody: String = "") {
    fun requireSuccess() {
        if (responseStatusCode !in 200..299) throw AppwriteException(responseBody, responseStatusCode)
    }
}

private suspend fun Call.awaitBody(): String = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            val result = runCatching {
                response.use {
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw AppwriteException(body, response.code)
                    body
                }
            }
            if (continuation.isActive) continuation.resumeWith(result)
        }
    })
}
