package com.tanush.bouncer.managers

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

object TeamAcronymManager {
    private var acronyms: Map<String, String> = emptyMap()

    fun initialize(context: Context) {
        try {
            val jsonString = context.assets.open("TeamAcronyms.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, String>>() {}.type
            acronyms = Gson().fromJson(jsonString, type)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun getAcronym(teamName: String): String {
        return acronyms[teamName] ?: generateFallbackAcronym(teamName)
    }

    private fun generateFallbackAcronym(teamName: String): String {
        // Fallback: First 3 letters uppercase
        return if (teamName.length >= 3) {
            teamName.substring(0, 3).uppercase()
        } else {
            teamName.uppercase()
        }
    }
}
