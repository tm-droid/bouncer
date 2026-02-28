package com.tanush.bouncer.managers

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object FavouriteTeamsManager {
    private const val PREF_NAME = "favourite_teams"
    private const val KEY = "favouriteTeams"
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    private val _favourites = MutableStateFlow<List<String>>(emptyList())
    val favourites: StateFlow<List<String>> = _favourites.asStateFlow()

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _favourites.value = load()
    }

    fun add(team: String) {
        val current = _favourites.value.toMutableList()
        if (!current.contains(team) && current.size < 10) {
            current.add(team)
            save(current)
        }
    }

    fun remove(team: String) {
        val current = _favourites.value.toMutableList()
        if (current.remove(team)) {
            save(current)
        }
    }

    fun move(fromIndex: Int, toIndex: Int) {
        val current = _favourites.value.toMutableList()
        // Simple swap logic for reordering
        if (fromIndex < toIndex) {
            for (i in fromIndex until toIndex) {
                java.util.Collections.swap(current, i, i + 1)
            }
        } else {
            for (i in fromIndex downTo toIndex + 1) {
                java.util.Collections.swap(current, i, i - 1)
            }
        }
        save(current)
    }

    fun reorder(newOrder: List<String>) {
        // Replace entire list with new order (iOS Parity)
        save(newOrder)
    }

    fun isFavourite(team: String): Boolean {
        return _favourites.value.contains(team)
    }

    private fun save(list: List<String>) {
        _favourites.value = list
        val json = gson.toJson(list)
        prefs.edit().putString(KEY, json).apply()
    }

    private fun load(): List<String> {
        if (!::prefs.isInitialized) return emptyList()
        val json = prefs.getString(KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type)
    }
}

