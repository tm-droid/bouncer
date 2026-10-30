package com.tanush.bouncer.managers

import com.tanush.bouncer.data.AppJson
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.tanush.bouncer.data.remote.DeviceIdentity
import io.sentry.Sentry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private val _favourites = MutableStateFlow<List<String>>(emptyList())
    val favourites: StateFlow<List<String>> = _favourites.asStateFlow()

    // Mutators below carry @Synchronized. Every one is read-modify-write across two places — the
    // StateFlow and prefs — so `MutableStateFlow.update {}` is not enough on its own: the disk
    // write has to be inside the same critical section, or two concurrent callers can persist in
    // the opposite order to the one they published. The monitor is this object; it is reentrant,
    // so toggle() can call isFavourite()/add()/remove().

    /**
     * Whether the favourites list is at maximum capacity
     */
    val isAtMax: Boolean
        get() = _favourites.value.size >= MAX_FAVOURITES

    fun initialize(context: Context) {
        if (::prefs.isInitialized) return
        try {
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            _favourites.value = load()
            Log.d(TAG, "Initialized with ${_favourites.value.size} favourites")
            // Same prefs, same id: DeviceIdentity reuses the UUID this client has persisted since
            // favourites sync shipped, and is initialized here so it is ready before the first
            // function call rather than depending on this client's own lifecycle.
            DeviceIdentity.initialize(prefs)
            // No remote favourites sync. AppwriteFavouritesSync wrote a List into `user-favourites.teams`,
            // a non-array text column, so every push failed and the table never held a row; its
            // pull still cost a billed read on every process start, background wakes included. The
            // row was keyed by a per-install UUID, so it could never have followed a user to a new
            // device anyway — Android backup restores these prefs, which is the working path.
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /**
     * Add a team to favourites. Returns false if at max capacity.
     */
    @Synchronized
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
            // save() has reported it. The star stays as it was instead of crashing the tap.
            Log.w(TAG, "Could not add $team", e)
            return false
        }
    }

    @Synchronized
    fun remove(team: String) {
        try {
            val current = _favourites.value.toMutableList()
            if (current.remove(team)) {
                save(current)
                Log.d(TAG, "Removed $team (total: ${current.size})")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not remove $team", e)
        }
    }

    /**
     * Toggle a team's favourite status (iOS Parity).
     * Returns true if the team is now a favourite, false if removed.
     * Returns null if the team couldn't be added (at max capacity).
     */
    @Synchronized
    fun toggle(team: String): Boolean? {
        return if (isFavourite(team)) {
            remove(team)
            // Reads back rather than assuming: a remove that failed leaves the team a favourite.
            isFavourite(team)
        } else {
            if (add(team)) true else null
        }
    }

    /**
     * Move a team from one position to another (drag-to-reorder support).
     */
    @Synchronized
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
            Log.w(TAG, "Could not move favourite", e)
        }
    }

    /**
     * Replace entire order (iOS Parity: used after drag-reorder completion)
     */
    @Synchronized
    fun reorder(newOrder: List<String>) {
        try {
            save(newOrder)
            Log.d(TAG, "Reordered: ${newOrder.size} teams")
        } catch (e: Exception) {
            Log.w(TAG, "Could not reorder favourites", e)
        }
    }

    fun isFavourite(team: String): Boolean = _favourites.value.contains(team)

    /**
     * Get the position of a team in the favourites list (-1 if not found)
     */
    fun indexOf(team: String): Int = _favourites.value.indexOf(team)

    private fun save(list: List<String>) {
        try {
            // Persist before publishing so the two never disagree in the order callers observe.
            // Note `apply()` is asynchronous: it hands the write to a background thread, so this
            // does NOT guarantee the value survives a process kill in the next few milliseconds.
            // Only commit() would, and that is a main-thread disk write. The real answer is
            // DataStore; until then this ordering is the best available, not a durability promise.
            val json = AppJson.encodeToString(list)
            // initialize() failing leaves prefs unset. The choice then holds for this session
            // only, which beats an UninitializedPropertyAccessException on every star tap.
            if (::prefs.isInitialized) prefs.edit().putString(KEY, json).apply()
            _favourites.value = list
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
                AppJson.decodeFromString<List<String>>(json)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse favourites JSON", e)
                Sentry.captureException(e)
                emptyList()
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

