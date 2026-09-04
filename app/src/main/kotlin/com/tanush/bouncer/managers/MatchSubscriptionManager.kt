package com.tanush.bouncer.managers

import com.tanush.bouncer.data.AppJson
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.tanush.bouncer.services.LiveUpdateService
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
    private var appContext: Context? = null
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
            appContext = context.applicationContext
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
                startLiveUpdates()
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
        prefs.edit().putString(KEY_SUBSCRIBED, AppJson.encodeToString(set.toList())).apply()
        _subscriptions.value = set
    }

    /**
     * Following a match is what turns its Live Update on — there is no separate control, because a
     * second switch for "follow this match, but do not show it" is a setting nobody wants.
     *
     * The service decides for itself which subscriptions are actually live and stops when none
     * are, so this only has to nudge it. It is a no-op when the app is in the background, where
     * the platform refuses a foreground-service start; the next foreground entry starts it.
     */
    private fun startLiveUpdates() {
        val context = appContext ?: return
        LiveUpdateService.start(context)
    }

    /** Start tracking whatever is already subscribed. Called once the app is in the foreground. */
    fun resumeLiveUpdates(context: Context) {
        if (!initialized || _subscriptions.value.isEmpty()) return
        LiveUpdateService.start(context.applicationContext)
    }

    private fun load(): Set<String> {
        if (!::prefs.isInitialized) return emptySet()
        val json = prefs.getString(KEY_SUBSCRIBED, null) ?: return emptySet()
        return try {
            AppJson.decodeFromString<List<String>>(json).toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse subscriptions JSON", e)
            emptySet()
        }
    }
}
