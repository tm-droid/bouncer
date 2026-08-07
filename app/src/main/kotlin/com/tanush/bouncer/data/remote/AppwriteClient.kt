package com.tanush.bouncer.data.remote

import android.content.Context
import android.util.Log
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.network.HttpClientProvider
import com.tanush.bouncer.utils.sentryBreadcrumb
import com.tanush.bouncer.utils.sentryCaptureThrottled
import io.appwrite.Client
import io.appwrite.exceptions.AppwriteException
import io.appwrite.services.Functions
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * The outcome of one function execution, with the status code intact.
 *
 * [AppwriteClient.executeFunction] collapses everything that is not a 2xx to `null`, which is the
 * right shape for callers whose only question is "did I get a body". It was the wrong shape for
 * News: a 429 from the Cricbuzz plan and a latched quota breaker arrived as the same `null`, the
 * repository turned that into one literal string, and the screen tried to recover the status by
 * substring-matching the message it had just been handed. Callers that want to distinguish the
 * cases ask for the outcome instead.
 */
sealed interface FunctionOutcome {
    data class Success(val body: String) : FunctionOutcome

    /** The function ran and answered, but with a 4xx/5xx. [body] is its error body. */
    data class HttpError(val statusCode: Int, val body: String) : FunctionOutcome

    /** No answer at all: SDK uninitialised, guard-blocked, execution FAILED, or the call threw. */
    data object Unavailable : FunctionOutcome
}

object AppwriteClient {
    private const val TAG = "AppwriteClient"

    private const val HTTP_ERROR_FLOOR = 400
    private const val HTTP_SERVER_ERROR_FLOOR = 500
    private const val SENTRY_FIELD_LIMIT = 500

    /**
     * The `error` field a Bouncer function puts in its error body, or a short excerpt.
     *
     * Every function in this project answers a failure with `{"error": "<reason>"}` —
     * `providers_exhausted`, `verification_failed`, `rate_limited`, `read_budget_exceeded`. Those
     * are four unrelated faults that all reach the user as "no commentary", so the reason is the
     * only part of the body worth carrying into Sentry. A body that is not JSON (an Appwrite
     * gateway page, an HTML 502) still says something, so it is excerpted rather than dropped.
     */
    private fun serverErrorOf(body: String): String {
        if (body.isBlank()) return "(empty body)"
        return runCatching { org.json.JSONObject(body).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: body.take(200)
    }

    @Volatile
    private var _client: Client? = null

    val client: Client? get() = _client
    val isReady: Boolean get() = _client != null

    @Synchronized
    fun initialize(context: Context) {
        val endpoint = ApiKeys.appwriteEndpoint.trimEnd('/')
        val projectId = ApiKeys.appwriteProjectId
        if (endpoint.isBlank() || projectId.isBlank()) {
            Log.w(TAG, "Appwrite not configured — SDK client disabled")
            return
        }
        _client = Client(context.applicationContext)
            .setEndpoint(endpoint)
            .setProject(projectId)
    }

    private val functionsService: Functions? get() = client?.let { Functions(it) }

    /**
     * The execution's response body, or null after reporting why there isn't one.
     *
     * Appwrite marks an execution FAILED only on a 5xx. Every in-band 4xx a Bouncer function
     * returns — `429 rate_limited`, `402 read_budget_exceeded`, a 400 from a body an old build
     * sends wrong — arrives as `completed`, and a caller that reads one field out of the body
     * (`optString("commentary")`) gets a blank string and turns it into the same null it uses for
     * "nothing to do". Production was rate-limiting commentary generation on 2026-08-04 with no
     * device-side signal at all; that is the gap this closes, for every function caller rather than
     * just the commentary one.
     */
    private fun responseOrReport(functionId: String, execution: io.appwrite.models.Execution): FunctionOutcome {
        val statusCode = execution.responseStatusCode
        if (statusCode >= HTTP_ERROR_FLOOR) {
            Log.w(TAG, "Function $functionId returned HTTP $statusCode: ${execution.responseBody.take(200)}")
            // The status is in the throttle key so a 429 storm cannot hide a 402 behind it.
            sentryCaptureThrottled(
                key = "appwrite.fn.http.$functionId.$statusCode",
                message = "Appwrite function $functionId returned HTTP $statusCode",
                level = if (statusCode >= HTTP_SERVER_ERROR_FLOOR) SentryLevel.ERROR else SentryLevel.WARNING,
                data = mapOf(
                    "functionId" to functionId,
                    "statusCode" to statusCode,
                    "serverError" to serverErrorOf(execution.responseBody),
                    "errors" to execution.errors.take(SENTRY_FIELD_LIMIT),
                )
            )
            // Null is already every caller's not-ready path, and an error body is not a result —
            // so `executeFunction` still flattens this to null. The status survives here for the
            // callers that ask for it.
            // The SDK types this as Long; every HTTP status fits an Int and callers match on Int.
            return FunctionOutcome.HttpError(statusCode.toInt(), execution.responseBody)
        }
        if (execution.status == io.appwrite.enums.ExecutionStatus.FAILED) {
            Log.e(TAG, "Function $functionId failed: ${execution.errors}")
            // No status code at all — a timeout, or a crash before any handler code ran. Everything
            // that did return a status, including a 5xx a handler returned deliberately, is handled
            // above, so what lands here has no response body to explain itself.
            sentryCaptureThrottled(
                key = "appwrite.fn.failed.$functionId",
                message = "Appwrite function $functionId returned FAILED",
                level = SentryLevel.ERROR,
                data = mapOf(
                    "functionId" to functionId,
                    "errors" to execution.errors.take(SENTRY_FIELD_LIMIT)
                )
            )
            return FunctionOutcome.Unavailable
        }
        return FunctionOutcome.Success(execution.responseBody)
    }

    /**
     * Execute a function and return its body, or null for anything that is not a 2xx.
     *
     * Unchanged behaviour, and the right call for every caller whose degradation path is the same
     * whatever went wrong. Use [executeFunctionDetailed] when the status code changes what you do.
     */
    suspend fun executeFunction(functionId: String, body: String): String? =
        (executeFunctionDetailed(functionId, body) as? FunctionOutcome.Success)?.body

    suspend fun executeFunctionDetailed(functionId: String, body: String): FunctionOutcome {
        val functions = functionsService
        if (functions == null) {
            // Not a quota problem — the SDK client never initialized, so every function call on
            // this process is dead. Silently returning null here is how a dead presence heartbeat
            // could look identical to an idle one.
            sentryCaptureThrottled(
                key = "appwrite.fn.uninitialized",
                message = "Appwrite function $functionId skipped — SDK client not initialized",
                data = mapOf("functionId" to functionId)
            )
            return FunctionOutcome.Unavailable
        }
        // Executions are their own plan quota and previously had no guard and no 402/429 handling
        // at all. Callers already treat null as "unavailable".
        if (AppwriteReadGuard.checkAndCount("fn.$functionId", AppwriteReadGuard.Quota.EXECUTIONS) != null) {
            Log.w(TAG, "Function $functionId blocked by AppwriteReadGuard")
            // A latched EXECUTIONS quota switches off every server-side feature that runs through
            // a function — commentary generation and the viewer gate included — for as long as the
            // latch holds. Breadcrumb-only made that indistinguishable from "nothing to do".
            sentryCaptureThrottled(
                key = "appwrite.fn.guard-blocked.$functionId",
                message = "Appwrite function $functionId blocked by AppwriteReadGuard (EXECUTIONS quota)",
                data = mapOf("functionId" to functionId)
            )
            return FunctionOutcome.Unavailable
        }
        return try {
            // The device identifies itself so the server can rate-limit per device. This replaced
            // `x-bouncer-secret`, a shared secret compiled into BuildConfig and therefore readable
            // out of the APK — it gated nothing while implying it did. A device id is not a
            // credential and is not treated as one: it is a bucket key, and a request without it
            // simply falls into the server's IP bucket instead.
            val execution = functions.createExecution(
                functionId = functionId,
                body = body,
                async = false,
                headers = mapOf("x-bouncer-device" to DeviceIdentity.id),
            )
            AppwriteReadGuard.recordSuccess(quota = AppwriteReadGuard.Quota.EXECUTIONS)
            AppwriteUsageTracker.recordFunctionExecution()
            responseOrReport(functionId, execution)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "executeFunction $functionId error: ${e.message}", e)
            AppwriteReadGuard.recordFailure("fn.$functionId", e, AppwriteReadGuard.Quota.EXECUTIONS)
            Sentry.addBreadcrumb("AppwriteClient.executeFunction[$functionId] failed: ${e.message}")
            Sentry.captureException(e)
            FunctionOutcome.Unavailable
        }
    }

    /**
     * Download a public bucket file, accepting a cached copy up to [maxAgeSeconds] old.
     *
     * Goes through the shared OkHttp client rather than `Storage.getFileDownload` because the SDK
     * exposes no response headers, so every call re-downloaded the whole blob. **[maxAgeSeconds] is
     * the only thing bounding that cache.** Appwrite Storage answers a download with
     * `cache-control: private, max-age=3888000` — 45 days — and sends neither `ETag` nor
     * `Last-Modified`, so there is no validator to revalidate with and no 304 to be had. Left to the
     * server's own header, OkHttp serves the first body it ever saw for a month and a half without
     * one further request: `live-winprob` was pinned to a near-empty snapshot, and every upcoming
     * fixture's pre-match bar was missing because of it. A request-side `max-age` caps the effective
     * freshness at min(server, request), which is what makes the cache follow the file's real write
     * cadence.
     *
     * Pass the cadence of the file being read, not a guess: too long silently serves stale data with
     * no error anywhere, and too short spends bandwidth on bytes that cannot have changed.
     *
     * Bandwidth is a shared API+Storage+Realtime pool, so this is guarded on [Quota.BANDWIDTH]. It
     * used to only REPORT a 402 and then download again on the very next call; now it stops.
     * Returning null is already the not-ready path and `CricketRepository` falls through to the
     * Vercel API.
     */
    suspend fun downloadFile(bucketId: String, fileId: String, maxAgeSeconds: Int): ByteArray? {
        if (!isReady) return null
        val endpoint = ApiKeys.appwriteEndpoint.trimEnd('/')
        val projectId = ApiKeys.appwriteProjectId
        if (endpoint.isBlank() || projectId.isBlank()) return null
        if (AppwriteReadGuard.checkAndCount("storage.$fileId", AppwriteReadGuard.Quota.BANDWIDTH) != null) {
            Log.w(TAG, "downloadFile $bucketId/$fileId blocked by AppwriteReadGuard")
            return null
        }
        return try {
            withContext(Dispatchers.IO) {
                fetchFile(
                    "$endpoint/storage/buckets/$bucketId/files/$fileId/download",
                    projectId,
                    maxAgeSeconds,
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "downloadFile $bucketId/$fileId failed: ${e.message}")
            AppwriteReadGuard.recordFailure("storage.$fileId", e, AppwriteReadGuard.Quota.BANDWIDTH)
            if (e is AppwriteException && e.code == 404) {
                sentryBreadcrumb("appwrite", "downloadFile $bucketId/$fileId: not found (404)", SentryLevel.WARNING)
            } else {
                sentryBreadcrumb("appwrite", "downloadFile $bucketId/$fileId failed: ${e.message}", SentryLevel.WARNING)
                Sentry.captureException(e)
            }
            null
        }
    }

    /**
     * One GET, answered from OkHttp's cache while the copy is younger than [maxAgeSeconds].
     *
     * Not a conditional GET: Appwrite Storage sends no `ETag` or `Last-Modified` for a download, so
     * OkHttp has nothing to revalidate with and a refetch is always a full body. A cache hit costs
     * no bytes and is recorded as zero.
     */
    private fun fetchFile(url: String, projectId: String, maxAgeSeconds: Int): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("X-Appwrite-Project", projectId)
            .cacheControl(CacheControl.Builder().maxAge(maxAgeSeconds, TimeUnit.SECONDS).build())
            .build()
        HttpClientProvider.sharedClient.newCall(request).execute().use { response ->
            val body = response.body?.bytes() ?: ByteArray(0)
            if (!response.isSuccessful) {
                // Rebuilt as an AppwriteException so the 402 classifier and the 404 branch below
                // behave exactly as they did on the SDK path.
                throw AppwriteException(
                    String(body, Charsets.UTF_8).ifBlank { "Storage download failed" },
                    response.code,
                    TYPE_PATTERN.find(String(body, Charsets.UTF_8))?.groupValues?.get(1),
                )
            }
            val servedFromCache = response.networkResponse == null
            AppwriteReadGuard.recordSuccess(quota = AppwriteReadGuard.Quota.BANDWIDTH)
            AppwriteUsageTracker.recordStorageDownload(if (servedFromCache) 0 else body.size)
            return body
        }
    }

    private val TYPE_PATTERN = Regex("\"type\"\\s*:\\s*\"([^\"]+)\"")
}
