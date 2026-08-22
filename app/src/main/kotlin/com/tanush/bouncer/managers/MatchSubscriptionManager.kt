package com.tanush.bouncer.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tanush.bouncer.utils.PrefsRegistry
import io.sentry.Sentry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MatchSubscriptionManager {
    private const val TAG = "MatchSubscriptionManager"
    private val PREFS_NAME = PrefsRegistry.PREFS_MATCH_SUBSCRIPTIONS
    private val KEY_SUBSCRIBED = PrefsRegistry.MatchSubscriptions.SUBSCRIBED_IDS

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private var initialized = false

    private val _subscriptions = MutableStateFlow<Set<String>>(emptySet())
    val subscriptions: StateFlow<Set<String>> = _subscriptions.asStateFlow()

    // Mutators below carry @Synchronized. They are read-modify-write across the StateFlow and
    // prefs together, so the disk write has to sit inside the same critical section —
    // `MutableStateFlow.update {}` alone would let two concurrent callers persist in the opposite
    // order to the one they published. The monitor is this object and is reentrant, so toggle()
    // can call subscribe()/unsubscribe().

    fun initialize(context: Context) {
        if (initialized) return
        try {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _subscriptions.value = load()
            initialized = true
            Log.d(TAG, "Initialized with ${_subscriptions.value.size} subscriptions")
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    @Synchronized
    fun subscribe(matchId: String) {
        if (!initialized) return
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

    @Synchronized
    fun unsubscribe(matchId: String) {
        if (!initialized) return
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

    @Synchronized
    fun toggle(matchId: String): Boolean {
        if (!initialized) return false
        return if (isSubscribed(matchId)) {
            unsubscribe(matchId)
            false
        } else {
            subscribe(matchId)
            true
        }
    }

    fun isSubscribed(matchId: String): Boolean {
        if (!initialized) return false
        return _subscriptions.value.contains(matchId)
    }

    private fun save(set: Set<String>) {
        // Persist before publishing so the two never disagree in the order callers observe.
        // `apply()` is asynchronous, so this is an ordering guarantee, not a durability one — a
        // process kill in the next few milliseconds can still lose the write. DataStore is the
        // real fix; commit() would only trade the loss for a main-thread disk write.
        prefs.edit().putString(KEY_SUBSCRIBED, gson.toJson(set.toList())).apply()
        _subscriptions.value = set
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
