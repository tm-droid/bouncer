package com.tanush.bouncer.managers

import com.tanush.bouncer.data.AppJson
import com.tanush.bouncer.utils.acronymFor
import android.content.Context
import java.io.IOException
import io.sentry.Sentry

object TeamAcronymManager {
    @Volatile private var acronyms: Map<String, String> = emptyMap()

    // Lower-cased name to code, built once. A miss on the exact key used to scan all ~1,000
    // entries with equals(ignoreCase), and every match card asks for two codes on every
    // recomposition.
    @Volatile private var acronymsByLowerName: Map<String, String> = emptyMap()

    fun initialize(context: Context) {
        try {
            val jsonString = context.assets.open("TeamAcronyms.json").bufferedReader().use { it.readText() }
            val loaded = AppJson.decodeFromString<Map<String, String>>(jsonString)
            acronymsByLowerName = loaded.entries.associate { (name, code) -> name.lowercase() to code }
            acronyms = loaded
        } catch (e: IOException) {
            Sentry.captureException(e)
        }
    }

    /** Every curated team, name to code. Search's team corpus: ~1,000 sides, not the 51 it hardcoded. */
    fun allTeams(): Map<String, String> = acronyms

    fun getAcronym(teamName: String): String {
        try {
            // The curated map wins, but its lookup is now case-insensitive: it was an exact-key
            // match, so a scraper returning "royal challengers bengaluru" missed the entry
            // entirely and fell through to the fallback.
            acronyms[teamName]?.let { return it }
            acronymsByLowerName[teamName.lowercase()]?.let { return it }
            // Initials of the significant words. The old fallback took the first three letters,
            // which gave "Royal Challengers Bengaluru" -> "ROY", made both Sunrisers franchises
            // "SUN", and threw on any name shorter than three characters.
            return acronymFor(teamName)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}
