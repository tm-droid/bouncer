package com.tanush.bouncer.managers

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    private val _items = MutableStateFlow<List<NotificationItem>>(emptyList())
    val items: StateFlow<List<NotificationItem>> = _items.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    fun initialize(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val loaded = loadFromPrefs()
        _items.value = loaded
        _unreadCount.value = loaded.count { !it.isRead }
    }

    fun add(item: NotificationItem) {
        val updated = (listOf(item) + _items.value).take(MAX_ITEMS)
        _items.value = updated
        _unreadCount.value = updated.count { !it.isRead }
        saveToPrefs(updated)
    }

    fun markAllRead() {
        val updated = _items.value.map { it.copy(isRead = true) }
        _items.value = updated
        _unreadCount.value = 0
        saveToPrefs(updated)
    }

    fun clear() {
        _items.value = emptyList()
        _unreadCount.value = 0
        prefs?.edit()?.remove(KEY_ITEMS)?.apply()
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
            emptyList()
        }
    }
}
