package com.tanush.bouncer.data.remote

import android.content.Context
import android.util.Log
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.network.HttpClientProvider
import com.tanush.bouncer.utils.sentryBreadcrumb
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

    suspend fun executeFunction(functionId: String, body: String): String? {
        val functions = functionsService ?: return null
        // Executions are their own plan quota and previously had no guard and no 402/429 handling
        // at all. Callers already treat null as "unavailable".
        if (AppwriteReadGuard.checkAndCount("fn.$functionId", AppwriteReadGuard.Quota.EXECUTIONS) != null) {
            Log.w(TAG, "Function $functionId blocked by AppwriteReadGuard")
            return null
        }
        return try {
            val secret = com.tanush.bouncer.ApiKeys.bouncerFunctionSecret
            val execution = functions.createExecution(
                functionId = functionId,
                body = body,
                async = false,
                headers = if (secret.isNotBlank()) mapOf("x-bouncer-secret" to secret) else null,
            )
            AppwriteReadGuard.recordSuccess(quota = AppwriteReadGuard.Quota.EXECUTIONS)
            AppwriteUsageTracker.recordFunctionExecution()
            if (execution.status == io.appwrite.enums.ExecutionStatus.FAILED) {
                Log.e(TAG, "Function $functionId failed: ${execution.errors}")
                null
            } else {
                execution.responseBody
            }
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
