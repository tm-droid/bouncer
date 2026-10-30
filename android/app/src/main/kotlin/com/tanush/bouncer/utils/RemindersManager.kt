package com.tanush.bouncer.utils

import android.content.Context
import com.tanush.bouncer.data.prefs.BouncerPreferences
import com.tanush.bouncer.data.prefs.PreferenceStore
import org.json.JSONArray
import org.json.JSONObject
import io.sentry.Sentry

/** [matchId] is null on records written before reminders carried one; those open Home when tapped. */
data class Reminder(
    val id: Int,
    val title: String,
    val body: String,
    val triggerAt: Long,
    val matchId: String? = null,
)

object RemindersManager {
    private const val KEY = "reminders"

    fun saveReminder(context: Context, reminder: Reminder) {
        try {
            PreferenceStore.initialize(context)
            val arr = JSONArray(BouncerPreferences.getString(KEY, "[]"))
            arr.put(reminder.toJson())
            BouncerPreferences.putString(KEY, arr.toString())
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /**
     * The stored reminders, or none when the record cannot be read. The Reminders screen and the
     * boot receiver both read this unguarded, and an unreadable record must not take either down.
     */
    fun getReminders(context: Context): List<Reminder> {
        try {
            PreferenceStore.initialize(context)
            val arr = JSONArray(BouncerPreferences.getString(KEY, "[]"))
            val out = mutableListOf<Reminder>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Reminder(
                        id = o.getInt("id"),
                        title = o.getString("title"),
                        body = o.getString("body"),
                        triggerAt = o.getLong("triggerAt"),
                        matchId = o.optString("matchId").takeIf { it.isNotBlank() },
                    )
                )
            }
            return out
        } catch (e: Exception) {
            Sentry.captureException(e)
            return emptyList()
        }
    }

    fun deleteReminder(context: Context, id: Int) {
        try {
            PreferenceStore.initialize(context)
            val arr = JSONArray(BouncerPreferences.getString(KEY, "[]"))
            val newArr = JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.getInt("id") != id) newArr.put(o)
            }
            BouncerPreferences.putString(KEY, newArr.toString())
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    /** Throws on failure: the caller tells the user the reminder was not set. */
    fun updateReminder(context: Context, reminder: Reminder) {
        try {
            // Replace reminder with same id
            PreferenceStore.initialize(context)
            val arr = JSONArray(BouncerPreferences.getString(KEY, "[]"))
            val newArr = JSONArray()
            var replaced = false
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.getInt("id") == reminder.id) {
                    newArr.put(reminder.toJson())
                    replaced = true
                } else {
                    newArr.put(o)
                }
            }
            if (!replaced) newArr.put(reminder.toJson())
            BouncerPreferences.putString(KEY, newArr.toString())
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /**
     * Reminders still to fire, soonest first. Ones already past are dropped from storage here: an
     * alarm that has fired leaves its record behind, and nothing else removes it.
     */
    fun upcoming(context: Context, nowMs: Long): List<Reminder> {
        val all = getReminders(context)
        val live = upcomingOf(all, nowMs)
        if (live.size != all.size) {
            all.filter { it !in live }.forEach { deleteReminder(context, it.id) }
        }
        return live
    }

    /**
     * Sets every pending reminder's alarm again. AlarmManager drops its alarms at reboot, so
     * without this a reminder stayed listed under Notifications › Reminders and never fired.
     */
    fun rescheduleAll(context: Context, nowMs: Long = System.currentTimeMillis()) {
        upcoming(context, nowMs).forEach { reminder ->
            try {
                ReminderScheduler.scheduleReminder(
                    context = context,
                    id = reminder.id,
                    title = reminder.title,
                    body = reminder.body,
                    delayMillis = reminder.triggerAt - nowMs,
                    matchId = reminder.matchId,
                )
            } catch (e: Exception) {
                // ReminderScheduler has reported it; the remaining reminders still get their alarm.
                android.util.Log.w("RemindersManager", "Could not reschedule reminder ${reminder.id}", e)
            }
        }
    }

    fun clearAll(context: Context) {
        try {
            PreferenceStore.initialize(context)
            BouncerPreferences.putString(KEY, "[]")
        } catch (e: Exception) {
            Sentry.captureException(e)
        }
    }

    private fun Reminder.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("body", body)
        put("triggerAt", triggerAt)
        matchId?.let { put("matchId", it) }
    }
}

/** Pure half of [RemindersManager.upcoming]. */
internal fun upcomingOf(reminders: List<Reminder>, nowMs: Long): List<Reminder> =
    reminders.filter { it.triggerAt > nowMs }.sortedBy { it.triggerAt }
