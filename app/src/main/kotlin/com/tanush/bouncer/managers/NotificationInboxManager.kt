package com.tanush.bouncer.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

    private val gson = Gson()
    private var prefs: SharedPreferences? = null
    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _items = MutableStateFlow<List<NotificationItem>>(emptyList())
    val items: StateFlow<List<NotificationItem>> = _items.asStateFlow()

    // Initialized in initialize() once scope is assigned, so stateIn uses the correct scope.
    private var _unreadCount: StateFlow<Int> = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> get() = _unreadCount

    fun initialize(context: Context, appScope: CoroutineScope = scope) {
        scope = appScope
        _unreadCount = _items
            .map { list -> list.count { !it.isRead } }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _items.value = loadFromPrefs()
    }

    fun add(item: NotificationItem) {
        val updated = (listOf(item) + _items.value).take(MAX_ITEMS)
        saveToPrefs(updated)
        _items.value = updated
    }

    fun markAllRead() {
        val updated = _items.value.map { it.copy(isRead = true) }
        saveToPrefs(updated)
        _items.value = updated
    }

    fun clear() {
        prefs?.edit()?.remove(KEY_ITEMS)?.apply()
        _items.value = emptyList()
    }

    fun inferType(title: String): String {
        val lower = title.lowercase()
        return when {
            lower.contains("wicket") || lower.contains("out") -> "wicket"
            lower.contains("six") -> "six"
            lower.contains("four") || lower.contains("boundary") -> "four"
            lower.contains("milestone") || lower.contains("century") || lower.contains("fifty") -> "milestone"
            lower.contains("start") -> "match_start"
            lower.contains("end") || lower.contains("result") -> "match_end"
            else -> "update"
        }
    }

    private fun saveToPrefs(items: List<NotificationItem>) {
        val json = gson.toJson(items)
        prefs?.edit()?.putString(KEY_ITEMS, json)?.apply()
    }

    private fun loadFromPrefs(): List<NotificationItem> {
        val json = prefs?.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<NotificationItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deserialize notification inbox, resetting", e)
            emptyList()
        }
    }
}
