package com.tanush.bouncer.managers

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

object TeamLogoManager {
    private var logos: Map<String, String> = emptyMap()

    fun initialize(context: Context) {
        try {
            val jsonString = context.assets.open("TeamLogos.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, String>>() {}.type
            logos = Gson().fromJson(jsonString, type)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun getLogoUrl(teamName: String): String? {
        return logos[teamName]
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
        if (!url.contains("static.cricbuzz.com")) return url
        if (width == null || height == null) return url
        return url.replace(Regex("/a/img/v1/\\d+x\\d+/"), "/a/img/v1/${width}x${height}/")
    }
}
