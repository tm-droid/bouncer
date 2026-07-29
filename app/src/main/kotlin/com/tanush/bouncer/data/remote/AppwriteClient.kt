package com.tanush.bouncer.data.remote

import android.content.Context
import android.util.Log
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.utils.sentryBreadcrumb
import io.appwrite.Client
import io.appwrite.exceptions.AppwriteException
import io.appwrite.models.InputFile
import io.appwrite.services.Functions
import io.appwrite.services.Storage
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

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
        return try {
            val secret = com.tanush.bouncer.ApiKeys.bouncerFunctionSecret
            val execution = functions.createExecution(
                functionId = functionId,
                body = body,
                async = false,
                headers = if (secret.isNotBlank()) mapOf("x-bouncer-secret" to secret) else null,
            )
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
            Sentry.addBreadcrumb("AppwriteClient.executeFunction[$functionId] failed: ${e.message}")
            Sentry.captureException(e)
            null
        }
    }

    suspend fun downloadFile(bucketId: String, fileId: String): ByteArray? {
        if (!isReady) return null
        val c = client ?: return null
        return try {
            withContext(Dispatchers.IO) {
                Storage(c).getFileDownload(bucketId = bucketId, fileId = fileId)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "downloadFile $bucketId/$fileId failed: ${e.message}")
            AppwriteReadGuard.reportIfBudget402("storage.downloadFile", e)
            if (e is AppwriteException && e.code == 404) {
                sentryBreadcrumb("appwrite", "downloadFile $bucketId/$fileId: not found (404)", SentryLevel.WARNING)
            } else {
                sentryBreadcrumb("appwrite", "downloadFile $bucketId/$fileId failed: ${e.message}", SentryLevel.WARNING)
                Sentry.captureException(e)
            }
            null
        }
    }
}
