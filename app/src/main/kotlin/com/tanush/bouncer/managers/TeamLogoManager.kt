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
 * The crest for [teamName]: exact key first, normalized name second, then the parent side's crest
 * for a variant ("India A Women" takes India's, which for a national side is its flag).
 *
 * Pure and top-level so it can be tested without an asset — in a unit test the manager's map is
 * always empty, which is how an exact-key-only lookup went unnoticed for as long as it did.
 */
internal fun lookupLogo(
    exact: Map<String, String>,
    normalized: Map<String, String>,
    teamName: String,
): String? {
    exact[teamName]?.let { return it }
    val key = normalizeTeamName(teamName)
    return normalized[key] ?: baseSide(teamName).takeIf { it != key }?.let { normalized[it] }
}

/**
 * Variant words, normalized, as `tools/team_variants.json` lists them; `TeamVariantParityTest` pins
 * the two together, since the weekly logo tool fills `TeamLogos.json` by the same rule.
 */
internal val TEAM_VARIANT_SUFFIXES = listOf(
    "women", "xi", "a", "u19", "u 19", "under 19", "under 19s", "u23",
    "emerging", "legends", "masters", "development", "academy", "select",
)

private val VARIANT_TAILS = TEAM_VARIANT_SUFFIXES.sortedByDescending { it.length }.map { it.split(" ") }

/**
 * The parent side of a variant, normalized: variant words stripped from the end until none is
 * left, but never down to nothing — a side called "Women" stays "women".
 */
internal fun baseSide(name: String): String {
    var words = normalizeTeamName(name).split(" ").filter { it.isNotEmpty() }
    var stripped = true
    while (stripped) {
        val tail = VARIANT_TAILS.firstOrNull { words.size > it.size && words.takeLast(it.size) == it }
        stripped = tail != null
        if (tail != null) words = words.dropLast(tail.size)
    }
    return words.joinToString(" ")
}
