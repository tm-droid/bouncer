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
import okhttp3.Request

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
    private fun responseOrReport(functionId: String, execution: io.appwrite.models.Execution): String? {
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
            // Null is already every caller's not-ready path, and an error body is not a result.
            return null
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
            return null
        }
        return execution.responseBody
    }

    suspend fun executeFunction(functionId: String, body: String): String? {
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
            return null
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
            return null
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
            null
        }
    }

    /**
     * Download a public bucket file.
     *
     * Goes through the shared OkHttp client rather than `Storage.getFileDownload` for one reason:
     * the SDK exposes no response headers, so every call re-downloaded the whole blob even when it
     * had not changed. OkHttp's cache stores the validator and replays `If-None-Match`, so an
     * unchanged file costs a 304 with an empty body instead of the full payload — and these files
     * (the match lists, the win-probability model, the H2H table) are rewritten far less often than
     * they are read. Requires the server-side write-skip in `bouncer-poll-live-matches`, without
     * which the file is rewritten every minute and the validator never matches.
     *
     * Bandwidth is a shared API+Storage+Realtime pool, so this is guarded on [Quota.BANDWIDTH]. It
     * used to only REPORT a 402 and then download again on the very next call; now it stops.
     * Returning null is already the not-ready path and `CricketRepository` falls through to the
     * Vercel API.
     */
    suspend fun downloadFile(bucketId: String, fileId: String): ByteArray? {
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
                fetchFile("$endpoint/storage/buckets/$bucketId/files/$fileId/download", projectId)
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
     * One conditional GET. A 304 is served from OkHttp's cache with no body over the wire, so it is
     * counted as a storage request costing zero bytes — which is the whole point of the change.
     */
    private fun fetchFile(url: String, projectId: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("X-Appwrite-Project", projectId)
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
            val notModified = response.networkResponse?.code == HTTP_NOT_MODIFIED
            AppwriteReadGuard.recordSuccess(quota = AppwriteReadGuard.Quota.BANDWIDTH)
            AppwriteUsageTracker.recordStorageDownload(if (notModified) 0 else body.size)
            return body
        }
    }

    private const val HTTP_NOT_MODIFIED = 304
    private val TYPE_PATTERN = Regex("\"type\"\\s*:\\s*\"([^\"]+)\"")
}
