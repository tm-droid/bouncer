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
    private const val ML_BUCKET_ID = "ml-models"
    private const val MODEL_FILE_ID = "score_model_v1"
    private const val MODEL_FILE_NAME = "score_model_v1.json"

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
            val execution = functions.createExecution(
                functionId = functionId,
                body = body,
                async = false,
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

    /**
     * Upload local model weights to Appwrite Storage bucket `ml-models`.
     *
     * If the local file doesn't exist, returns silently.
     * If a remote file already exists (409), deletes it first then re-uploads
     * (Appwrite Storage `updateFile` only changes metadata — content requires delete+create).
     */
    suspend fun uploadModelWeights(context: Context) {
        if (!isReady) return
        val c = client ?: return
        val localFile = File(context.filesDir, MODEL_FILE_NAME)
        if (!localFile.exists()) {
            Log.d(TAG, "uploadModelWeights: local file not found, skipping")
            return
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val storage = Storage(c)
                runCatching {
                    storage.createFile(
                        bucketId = ML_BUCKET_ID,
                        fileId = MODEL_FILE_ID,
                        file = InputFile.fromFile(localFile)
                    )
                }.onFailure { e ->
                    if (e is AppwriteException && e.code == 409) {
                        // File already exists — delete then re-upload
                        storage.deleteFile(bucketId = ML_BUCKET_ID, fileId = MODEL_FILE_ID)
                        storage.createFile(
                            bucketId = ML_BUCKET_ID,
                            fileId = MODEL_FILE_ID,
                            file = InputFile.fromFile(localFile)
                        )
                    } else {
                        throw e
                    }
                }
                Log.d(TAG, "uploadModelWeights: uploaded ${localFile.length()} bytes")
            }.onFailure { e ->
                Sentry.addBreadcrumb("AppwriteClient.uploadModelWeights failed: ${e.message}")
                Sentry.captureException(e)
                if (e is AppwriteException) {
                    Log.d(TAG, "uploadModelWeights failed: ${e.message} [${e.code}]")
                } else {
                    Log.d(TAG, "uploadModelWeights failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Download model weights from Appwrite Storage if the remote file is newer than the local one.
     *
     * Compares remote `$updatedAt` (ISO-8601) vs local file `lastModified()`.
     * Downloads and overwrites local file if remote is newer or local doesn't exist.
     *
     * @return true if new weights were written to disk; false otherwise.
     */
    suspend fun downloadModelWeightsIfNewer(context: Context): Boolean {
        if (!isReady) return false
        val c = client ?: return false
        return withContext(Dispatchers.IO) {
            runCatching {
                val storage = Storage(c)
                val remoteMeta = storage.getFile(bucketId = ML_BUCKET_ID, fileId = MODEL_FILE_ID)
                val remoteUpdatedAt = Instant.parse(remoteMeta.updatedAt).toEpochMilli()

                val localFile = File(context.filesDir, MODEL_FILE_NAME)
                val localModified = if (localFile.exists()) localFile.lastModified() else 0L

                if (remoteUpdatedAt <= localModified) {
                    Log.d(TAG, "downloadModelWeightsIfNewer: local is up-to-date, skipping")
                    return@runCatching false
                }

                val bytes = storage.getFileDownload(bucketId = ML_BUCKET_ID, fileId = MODEL_FILE_ID)
                val tmpFile = File(context.filesDir, "score_model_v1.json.tmp")
                tmpFile.writeBytes(bytes)
                if (!tmpFile.renameTo(localFile)) {
                    // rename failed (cross-partition or target locked) — fall back to copy
                    tmpFile.copyTo(localFile, overwrite = true)
                    tmpFile.delete()
                }
                Log.d(TAG, "downloadModelWeightsIfNewer: downloaded ${bytes.size} bytes (remote newer by ${remoteUpdatedAt - localModified}ms)")
                true
            }.onFailure { e ->
                if (e is AppwriteException) {
                    if (e.code != 404) {
                        Sentry.addBreadcrumb("AppwriteClient.downloadModelWeightsIfNewer failed: ${e.message}")
                        Sentry.captureException(e)
                    }
                    Log.d(TAG, "downloadModelWeightsIfNewer failed: ${e.message} [${e.code}]")
                } else {
                    Sentry.addBreadcrumb("AppwriteClient.downloadModelWeightsIfNewer failed: ${e.message}")
                    Sentry.captureException(e)
                    Log.d(TAG, "downloadModelWeightsIfNewer failed: ${e.message}")
                }
            }.getOrElse { false }
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
            sentryBreadcrumb("appwrite", "downloadFile $bucketId/$fileId failed: ${e.message}", SentryLevel.WARNING)
            Sentry.captureException(e)
            null
        }
    }
}
