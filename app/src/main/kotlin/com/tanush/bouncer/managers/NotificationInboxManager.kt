package com.tanush.bouncer.managers

import kotlinx.coroutines.flow.MutableStateFlow
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

    private val _items = MutableStateFlow<List<NotificationItem>>(emptyList())
    val items: StateFlow<List<NotificationItem>> = _items.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    fun add(item: NotificationItem) {
        val updated = listOf(item) + _items.value
        _items.value = updated
        _unreadCount.value = updated.count { !it.isRead }
    }

    fun markAllRead() {
        _items.value = _items.value.map { it.copy(isRead = true) }
        _unreadCount.value = 0
    }

    fun clear() {
        _items.value = emptyList()
        _unreadCount.value = 0
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
}
