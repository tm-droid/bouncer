package com.tanush.bouncer.managers

import kotlinx.serialization.Serializable
import com.tanush.bouncer.data.AppJson
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

@Serializable
data class NotificationItem(
    val id: String = UUID.randomUUID().toString(),
    val matchId: String,
    val title: String,
    val body: String,
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
)

object NotificationInboxManager {

    private const val PREF_NAME = "notification_inbox"
    private const val KEY_ITEMS = "inbox_items"
    private const val MAX_ITEMS = 50
    private const val TAG = "NotificationInboxManager"

    private var prefs: SharedPreferences? = null
    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _items = MutableStateFlow<List<NotificationItem>>(emptyList())
    val items: StateFlow<List<NotificationItem>> = _items.asStateFlow()

    // Mutators below carry @Synchronized. They are read-modify-write across the StateFlow and
    // prefs together, so the disk write has to sit inside the same critical section —
    // `MutableStateFlow.update {}` alone would let two concurrent callers persist in the opposite
    // order to the one they published.

    // Initialized in initialize() once scope is assigned, so stateIn uses the correct scope.
    private var _unreadCount: StateFlow<Int> = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> get() = _unreadCount

    @Synchronized
    fun initialize(context: Context, appScope: CoroutineScope = scope) {
        // Re-entering initialize() used to rebuild _unreadCount unconditionally, which silently
        // orphaned every collector of the previous StateFlow — they kept observing a flow nothing
        // would ever update again. Build it exactly once, on the first call.
        if (prefs != null) return
        scope = appScope
        _unreadCount = _items
            .map { list -> list.count { !it.isRead } }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _items.value = loadFromPrefs()
    }

    @Synchronized
    fun add(item: NotificationItem) {
        val updated = (listOf(item) + _items.value).take(MAX_ITEMS)
        saveToPrefs(updated)
        _items.value = updated
    }

    @Synchronized
    fun markAllRead() {
        val updated = _items.value.map { it.copy(isRead = true) }
        saveToPrefs(updated)
        _items.value = updated
    }

    @Synchronized
    fun clear() {
        prefs?.edit()?.remove(KEY_ITEMS)?.apply()
        _items.value = emptyList()
    }

    // Substring matching classified "South Africa" as a wicket — "s-OUT-h" — and because the
    // wicket branch is first it also shadowed six/four/start/end for every notification about
    // South Africa, a knockout stage, or an outstanding spell. Word boundaries are the floor;
    // the real fix is for the FCM payload to carry the type instead of it being reverse-engineered
    // from display text.
    private val WICKET_RE = Regex("\\b(?:wicket|out)\\b")
    private val SIX_RE = Regex("\\bsix\\b")
    private val FOUR_RE = Regex("\\b(?:four|boundary)\\b")
    private val MILESTONE_RE = Regex("\\b(?:milestone|century|fifty)\\b")
    private val START_RE = Regex("\\bstart\\b")
    private val END_RE = Regex("\\b(?:end|result)\\b")

    fun inferType(title: String): String {
        val lower = title.lowercase()
        return when {
            WICKET_RE.containsMatchIn(lower) -> "wicket"
            SIX_RE.containsMatchIn(lower) -> "six"
            FOUR_RE.containsMatchIn(lower) -> "four"
            MILESTONE_RE.containsMatchIn(lower) -> "milestone"
            START_RE.containsMatchIn(lower) -> "match_start"
            END_RE.containsMatchIn(lower) -> "match_end"
            else -> "update"
        }
    }

    private fun saveToPrefs(items: List<NotificationItem>) {
        val json = AppJson.encodeToString(items)
        prefs?.edit()?.putString(KEY_ITEMS, json)?.apply()
    }

    private fun loadFromPrefs(): List<NotificationItem> {
        val json = prefs?.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            AppJson.decodeFromString<List<NotificationItem>>(json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deserialize notification inbox, resetting", e)
            emptyList()
        }
    }
}
