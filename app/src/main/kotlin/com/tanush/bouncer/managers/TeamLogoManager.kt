package com.tanush.bouncer.managers

import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.utils.normalizeTeamName
import android.content.Context
import android.util.Log
import java.io.IOException
import io.sentry.Sentry

object TeamLogoManager {
    private const val TAG = "TeamLogoManager"
    @Volatile private var logos: Map<String, String> = emptyMap()

    /**
     * The same map keyed by [normalizeTeamName], for names that differ only in case or punctuation.
     *
     * The lookup was exact-key only, so `"Namibia  U19"` (a real key, with the double space
     * Cricbuzz ships) missed for a scraper returning the single-spaced name, and a lowercased name
     * missed everything. [TeamAcronymManager] had already grown a case-insensitive pass for exactly
     * this; an index rather than its linear scan, because this is on the match-list parse path.
     *
     * First key wins on a collision — the asset holds both "Cape cobras" and "Cape Cobras" — so a
     * normalized hit is never worse than the miss it replaces.
     */
    @Volatile private var logosByNormalizedName: Map<String, String> = emptyMap()
    @Volatile private var isInitialized = false
    private var initError: String? = null

    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            val jsonString = context.assets.open("TeamLogos.json").bufferedReader().use { it.readText() }
            logos = AppJson.decodeFromString<Map<String, String>>(jsonString)
            logosByNormalizedName = buildNormalizedLogoIndex(logos)
            isInitialized = true
            initError = null
            Log.d(TAG, "Loaded ${logos.size} team logos")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load TeamLogos.json from assets", e)
            Sentry.captureException(e)
            initError = e.message
            logos = emptyMap()
            logosByNormalizedName = emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error initializing TeamLogoManager", e)
            Sentry.captureException(e)
            initError = e.message
            logos = emptyMap()
            logosByNormalizedName = emptyMap()
        }
    }

    /** The curated URL for [teamName], by exact key first and normalized name second. */
    private fun lookup(teamName: String): String? =
        lookupLogo(logos, logosByNormalizedName, teamName)

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
            return lookup(teamName)
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
        val preferred = teamName?.let { lookup(it) } ?: fallbackUrl
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

/**
 * [source] re-keyed by [normalizeTeamName].
 *
 * First key wins on a collision — the asset holds both "Cape cobras" and "Cape Cobras" — so a
 * normalized hit is never worse than the miss it replaces.
 */
internal fun buildNormalizedLogoIndex(source: Map<String, String>): Map<String, String> {
    val index = HashMap<String, String>(source.size)
    source.forEach { (name, url) -> index.putIfAbsent(normalizeTeamName(name), url) }
    return index
}

/**
 * The crest for [teamName]: exact key first, normalized name second.
 *
 * Pure and top-level so it can be tested without an asset — in a unit test the manager's map is
 * always empty, which is how an exact-key-only lookup went unnoticed for as long as it did.
 */
internal fun lookupLogo(
    exact: Map<String, String>,
    normalized: Map<String, String>,
    teamName: String,
): String? = exact[teamName] ?: normalized[normalizeTeamName(teamName)]
