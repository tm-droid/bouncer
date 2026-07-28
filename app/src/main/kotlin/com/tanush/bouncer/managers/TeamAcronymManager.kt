package com.tanush.bouncer.managers

import com.tanush.bouncer.utils.acronymFor
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import io.sentry.Sentry

object TeamAcronymManager {
    private var acronyms: Map<String, String> = emptyMap()

    fun initialize(context: Context) {
        try {
            val jsonString = context.assets.open("TeamAcronyms.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, String>>() {}.type
            acronyms = Gson().fromJson(jsonString, type)
        } catch (e: IOException) {
            Sentry.captureException(e)
        }
    }

    fun getAcronym(teamName: String): String {
        try {
            // The curated map wins, but its lookup is now case-insensitive: it was an exact-key
            // match, so a scraper returning "royal challengers bengaluru" missed the entry
            // entirely and fell through to the fallback.
            acronyms[teamName]?.let { return it }
            acronyms.entries.firstOrNull { it.key.equals(teamName, ignoreCase = true) }
                ?.let { return it.value }
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
