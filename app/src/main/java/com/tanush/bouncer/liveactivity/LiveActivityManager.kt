package com.tanush.bouncer.liveactivity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.screens.NotificationPrefsManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LiveActivityManager(
    private val context: Context,
    private val notificationBuilder: LiveActivityNotificationBuilder
) {
    private val lock = Mutex()
    private val pendingUpdates = ArrayDeque<QueuedUpdate>()

    private var currentMatchId: String? = null
    private var selectedStyle: LiveActivityStyle = LiveActivityStyle.Compact
    private var updateFrequency: UpdateFrequency = UpdateFrequency.EVERY_OVER
    private var lastUpdateTimeMs: Long = 0L

    suspend fun startActivity(matchId: String, style: LiveActivityStyle) {
        lock.withLock {
            currentMatchId = matchId
            selectedStyle = style
            pendingUpdates.clear()
            lastUpdateTimeMs = 0L
        }
        notificationBuilder.createNotificationChannel()
    }

    suspend fun postUpdate(state: MatchState, update: LiveMatchUpdate?) {
        if (!hasNotificationPermission()) return
        if (state.isCompleted) {
            endActivity()
            return
        }

        val now = System.currentTimeMillis()
        if (update != null) {
            lock.withLock {
                pendingUpdates.addLast(QueuedUpdate(update, now))
                if (pendingUpdates.size > 30) pendingUpdates.removeFirst()
            }
        }

        if (!shouldPostUpdate(update, now)) return

        val relativeTime = relativeUpdateLabel(now, state.lastUpdateEpochMs)
        val notification = notificationBuilder.buildNotification(
            matchId = state.matchId,
            style = selectedStyle,
            state = state,
            relativeTime = relativeTime,
            highPriority = update is LiveMatchUpdate.WicketUpdate
        )
        NotificationManagerCompat.from(context).notify(state.matchId.hashCode(), notification)

        lock.withLock {
            lastUpdateTimeMs = now
            pendingUpdates.clear()
        }
    }

    suspend fun updateFrequency(frequency: UpdateFrequency) {
        lock.withLock { updateFrequency = frequency }
    }

    suspend fun setStyle(style: LiveActivityStyle) {
        lock.withLock { selectedStyle = style }
    }

    suspend fun endActivity() {
        val matchId = lock.withLock {
            val id = currentMatchId
            currentMatchId = null
            pendingUpdates.clear()
            id
        }
        if (matchId != null) {
            NotificationManagerCompat.from(context).cancel(matchId.hashCode())
        }
    }

    suspend fun shouldPostUpdate(update: LiveMatchUpdate?, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (isSuppressedByQuietHours(nowMs)) return false

        val minIntervalMs = when (updateFrequency) {
            UpdateFrequency.EVERY_RUN -> 4_000L
            UpdateFrequency.EVERY_OVER -> 18_000L
            UpdateFrequency.EVERY_WICKET -> 12_000L
        }

        if (nowMs - lastUpdateTimeMs < minIntervalMs && update !is LiveMatchUpdate.WicketUpdate) {
            return false
        }

        return when (updateFrequency) {
            UpdateFrequency.EVERY_RUN -> update is LiveMatchUpdate.ScoreUpdate || update is LiveMatchUpdate.WicketUpdate
            UpdateFrequency.EVERY_OVER -> update !is LiveMatchUpdate.ScoreUpdate || (update.runs % 6 == 0)
            UpdateFrequency.EVERY_WICKET -> update !is LiveMatchUpdate.ScoreUpdate || update is LiveMatchUpdate.WicketUpdate
        }
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isSuppressedByQuietHours(nowMs: Long): Boolean {
        val settings = NotificationPrefsManager.getInstance(context).settings.value
        if (!settings.quietHoursEnabled || settings.allowLiveMatchAlertsDuringQuietHours) {
            return false
        }

        val minuteOfDay = ((nowMs / 60_000L) % (24 * 60)).toInt()
        val start = settings.quietHours.startMinuteOfDay
        val end = settings.quietHours.endMinuteOfDay

        return if (start <= end) {
            minuteOfDay in start until end
        } else {
            minuteOfDay >= start || minuteOfDay < end
        }
    }

    private fun relativeUpdateLabel(now: Long, updatedAt: Long): String {
        val delta = (now - updatedAt).coerceAtLeast(0L)
        return when {
            delta < 25_000L -> context.getString(R.string.live_activity_last_update_just_now)
            delta < 60_000L -> context.getString(R.string.live_activity_last_update_less_than_min)
            delta < 60 * 60_000L -> context.getString(
                R.string.live_activity_last_update_minutes,
                (delta / 60_000L).toInt()
            )
            else -> context.getString(
                R.string.live_activity_last_update_hours,
                (delta / (60 * 60_000L)).toInt()
            )
        }
    }
}
