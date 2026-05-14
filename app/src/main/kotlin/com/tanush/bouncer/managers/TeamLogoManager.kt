package com.tanush.bouncer.managers

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import io.sentry.Sentry

object TeamLogoManager {
    private const val TAG = "TeamLogoManager"
    private var logos: Map<String, String> = emptyMap()
    private var isInitialized = false
    private var initError: String? = null

    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            val jsonString = context.assets.open("TeamLogos.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, String>>() {}.type
            logos = Gson().fromJson(jsonString, type)
            isInitialized = true
            initError = null
            Log.d(TAG, "Loaded ${logos.size} team logos")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load TeamLogos.json from assets", e)
            Sentry.captureException(e)
            initError = e.message
            logos = emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error initializing TeamLogoManager", e)
            Sentry.captureException(e)
            initError = e.message
            logos = emptyMap()
        }
    }

    /**
     * Retry initialization if previous attempt failed
     */
    fun retryInitialization(context: Context) {
        try {
            if (!isInitialized || logos.isEmpty()) {
                Log.d(TAG, "Retrying initialization (previous error: $initError)")
                initialize(context)
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun getLogoUrl(teamName: String): String? {
        try {
            return logos[teamName]
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun getBestLogoUrl(
        teamName: String?,
        fallbackUrl: String?,
        width: Int? = null,
        height: Int? = null
    ): String? {
        val preferred = teamName?.let { logos[it] } ?: fallbackUrl
        return preferred?.let { normalizeCricbuzzUrl(it, width, height) }
    }

    private fun normalizeCricbuzzUrl(url: String, width: Int?, height: Int?): String {
        try {
            if (!url.contains("static.cricbuzz.com")) return url
            if (width == null || height == null) return url
            return url.replace(Regex("/a/img/v1/\\d+x\\d+/"), "/a/img/v1/${width}x${height}/")
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}
