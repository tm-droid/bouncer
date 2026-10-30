package com.tanush.bouncer.data.repository

import android.util.Log
import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.models.NewsArticleData
import io.sentry.Sentry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** What the News tab keeps between launches. */
interface NewsStore {
    suspend fun readHeadlines(): List<NewsArticleData>
    suspend fun writeHeadlines(articles: List<NewsArticleData>)
    suspend fun readBody(storyId: Long): List<String>
    suspend fun writeBody(storyId: Long, paragraphs: List<String>)
}

/**
 * The last headline list and the bodies of the stories that were opened, as JSON files in the
 * app's cache directory.
 *
 * Nothing was kept before. The tab was blank offline, every cold start waited on a function
 * execution before anything painted, and an article restored after process death had no list to
 * be found in. Files, not Room: a list read whole and written whole needs no schema or migration,
 * and the system may clear the cache directory, which is the right lifetime for it.
 *
 * Every method does nothing until [initialize] has run, without leaving the caller's thread — a
 * unit test constructing a ViewModel never reaches a real dispatcher through here.
 */
object NewsDiskCache : NewsStore {
    private const val TAG = "NewsDiskCache"
    private const val HEADLINES_FILE = "headlines.json"
    private const val BODY_PREFIX = "body-"
    private const val MAX_BODIES = 30

    @Serializable
    private data class SavedHeadlines(val articles: List<NewsArticleData> = emptyList())

    @Serializable
    private data class SavedBody(val paragraphs: List<String> = emptyList())

    @Volatile
    private var dir: File? = null

    fun initialize(cacheDir: File) {
        dir = File(cacheDir, "news")
    }

    override suspend fun readHeadlines(): List<NewsArticleData> {
        val file = fileFor(HEADLINES_FILE) ?: return emptyList()
        return withContext(Dispatchers.IO) {
            read(file) { AppJson.decodeFromString<SavedHeadlines>(it).articles }.orEmpty()
        }
    }

    override suspend fun writeHeadlines(articles: List<NewsArticleData>) {
        val file = fileFor(HEADLINES_FILE) ?: return
        // An empty refresh is not a reason to forget the stories already saved.
        if (articles.isEmpty()) return
        withContext(Dispatchers.IO) { write(file, AppJson.encodeToString(SavedHeadlines(articles))) }
    }

    override suspend fun readBody(storyId: Long): List<String> {
        val file = fileFor("$BODY_PREFIX$storyId.json") ?: return emptyList()
        return withContext(Dispatchers.IO) {
            read(file) { AppJson.decodeFromString<SavedBody>(it).paragraphs }.orEmpty()
        }
    }

    override suspend fun writeBody(storyId: Long, paragraphs: List<String>) {
        val file = fileFor("$BODY_PREFIX$storyId.json") ?: return
        if (paragraphs.isEmpty()) return
        withContext(Dispatchers.IO) {
            write(file, AppJson.encodeToString(SavedBody(paragraphs)))
            pruneBodies(file.parentFile)
        }
    }

    private fun fileFor(name: String): File? = dir?.let { File(it, name) }

    private fun <T> read(file: File, decode: (String) -> T): T? {
        if (!file.isFile) return null
        return try {
            decode(file.readText())
        } catch (e: Exception) {
            // An unreadable file is a cache miss. It is deleted so it is not re-read every launch.
            Log.w(TAG, "Unreadable ${file.name}", e)
            Sentry.captureException(e)
            file.delete()
            null
        }
    }

    private fun write(file: File, json: String) {
        try {
            file.parentFile?.mkdirs()
            // Written beside the file and renamed over it, so a kill mid-write cannot leave half.
            val staging = File(file.parentFile, "${file.name}.tmp")
            staging.writeText(json)
            if (!staging.renameTo(file)) staging.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Could not save ${file.name}", e)
            Sentry.captureException(e)
        }
    }

    private fun pruneBodies(directory: File?) {
        val bodies = directory?.listFiles { f -> f.name.startsWith(BODY_PREFIX) && f.name.endsWith(".json") }
            ?: return
        bodies.sortedByDescending { it.lastModified() }.drop(MAX_BODIES).forEach { it.delete() }
    }
}
