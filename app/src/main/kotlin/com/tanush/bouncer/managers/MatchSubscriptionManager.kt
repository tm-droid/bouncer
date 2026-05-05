package com.tanush.bouncer.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.sentry.Sentry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MatchSubscriptionManager {
    private const val TAG = "MatchSubscriptionManager"
    private const val PREFS_NAME = "match_subscriptions"
    private const val KEY_SUBSCRIBED = "subscribed_match_ids"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    private val _subscriptions = MutableStateFlow<Set<String>>(emptySet())
    val subscriptions: StateFlow<Set<String>> = _subscriptions.asStateFlow()

    fun initialize(context: Context) {
        try {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _subscriptions.value = load()
            Log.d(TAG, "Initialized with ${_subscriptions.value.size} subscriptions")
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun subscribe(matchId: String) {
        try {
            val current = _subscriptions.value.toMutableSet()
            if (current.add(matchId)) {
                save(current)
                Log.d(TAG, "Subscribed to $matchId (total: ${current.size})")
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    fun unsubscribe(matchId: String) {
        try {
            val current = _subscriptions.value.toMutableSet()
            if (current.remove(matchId)) {
                save(current)
                Log.d(TAG, "Unsubscribed from $matchId (total: ${current.size})")
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    fun toggle(matchId: String): Boolean {
        return if (isSubscribed(matchId)) {
            unsubscribe(matchId)
            false
        } else {
            subscribe(matchId)
            true
        }
    }

    fun isSubscribed(matchId: String): Boolean = _subscriptions.value.contains(matchId)

    private fun save(set: Set<String>) {
        _subscriptions.value = set
        prefs.edit().putString(KEY_SUBSCRIBED, gson.toJson(set.toList())).apply()
    }

    private fun load(): Set<String> {
        if (!::prefs.isInitialized) return emptySet()
        val json = prefs.getString(KEY_SUBSCRIBED, null) ?: return emptySet()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val list: List<String> = gson.fromJson(json, type)
            list.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse subscriptions JSON", e)
            emptySet()
        }
    }
}
