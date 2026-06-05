package com.tanush.bouncer.managers

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tanush.bouncer.data.remote.AppwriteFavouritesSync
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages user's favourite teams with persistence, reordering, and max limit enforcement.
 * iOS Parity: Matches FavouriteTeamsView behavior with drag-to-reorder, max limit, and toggle.
 */
object FavouriteTeamsManager {
    private const val TAG = "FavouriteTeamsManager"
    private const val PREF_NAME = "favourite_teams"
    private const val KEY = "favouriteTeams"
    const val MAX_FAVOURITES = 10

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _favourites = MutableStateFlow<List<String>>(emptyList())
    val favourites: StateFlow<List<String>> = _favourites.asStateFlow()

    /**
     * Whether the favourites list is at maximum capacity
     */
    val isAtMax: Boolean
        get() = _favourites.value.size >= MAX_FAVOURITES

    fun initialize(context: Context, appScope: CoroutineScope = scope) {
        scope = appScope
        if (::prefs.isInitialized) return
        try {
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            _favourites.value = load()
            Log.d(TAG, "Initialized with ${_favourites.value.size} favourites")
            AppwriteFavouritesSync.initialize(prefs)
            // Pull remote favourites; adopt if local is empty (e.g. fresh install)
            scope.launch {
                runCatching {
                    val cm = context.getSystemService(ConnectivityManager::class.java)
                    val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
                    if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@runCatching
                    val remote = AppwriteFavouritesSync.pull()
                    if (remote != null && _favourites.value.isEmpty()) {
                        Log.d(TAG, "Adopting ${remote.size} favourites from remote")
                        _favourites.value = remote
                        prefs.edit().putString(KEY, gson.toJson(remote)).apply()
                    }
                }.onFailure { e -> Log.d(TAG, "Remote favourites pull failed: ${e.message}") }
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /**
     * Add a team to favourites. Returns false if at max capacity.
     */
    fun add(team: String): Boolean {
        try {
            val current = _favourites.value.toMutableList()
            if (current.contains(team)) return true // Already a favourite
            if (current.size >= MAX_FAVOURITES) {
                Log.w(TAG, "Cannot add $team: at max capacity ($MAX_FAVOURITES)")
                return false
            }
            current.add(team)
            save(current)
            Log.d(TAG, "Added $team (total: ${current.size})")
            return true
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun remove(team: String) {
        try {
            val current = _favourites.value.toMutableList()
            if (current.remove(team)) {
                save(current)
                Log.d(TAG, "Removed $team (total: ${current.size})")
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /**
     * Toggle a team's favourite status (iOS Parity).
     * Returns true if the team is now a favourite, false if removed.
     * Returns null if the team couldn't be added (at max capacity).
     */
    fun toggle(team: String): Boolean? {
        try {
            return if (isFavourite(team)) {
                remove(team)
                false
            } else {
                if (add(team)) true else null
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /**
     * Move a team from one position to another (drag-to-reorder support).
     */
    fun move(fromIndex: Int, toIndex: Int) {
        try {
            val current = _favourites.value.toMutableList()
            if (fromIndex !in current.indices || toIndex !in current.indices) {
                Log.w(TAG, "Invalid move indices: $fromIndex -> $toIndex (size: ${current.size})")
                return
            }
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            save(current)
            Log.d(TAG, "Moved ${item} from $fromIndex to $toIndex")
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /**
     * Replace entire order (iOS Parity: used after drag-reorder completion)
     */
    fun reorder(newOrder: List<String>) {
        try {
            save(newOrder)
            Log.d(TAG, "Reordered: ${newOrder.size} teams")
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun isFavourite(team: String): Boolean {
        try {
            return _favourites.value.contains(team)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /**
     * Get the position of a team in the favourites list (-1 if not found)
     */
    fun indexOf(team: String): Int {
        try {
            return _favourites.value.indexOf(team)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    private fun save(list: List<String>) {
        try {
            // Persist first so a process kill between disk + StateFlow doesn't lose state.
            val json = gson.toJson(list)
            prefs.edit().putString(KEY, json).apply()
            _favourites.value = list
            scope.launch {
                try {
                    AppwriteFavouritesSync.push(list)
                } catch (e: Exception) {
                    Sentry.captureException(e)
                }
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    private fun load(): List<String> {
        try {
            if (!::prefs.isInitialized) return emptyList()
            val json = prefs.getString(KEY, null) ?: return emptyList()
            return try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse favourites JSON", e)
                emptyList()
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

