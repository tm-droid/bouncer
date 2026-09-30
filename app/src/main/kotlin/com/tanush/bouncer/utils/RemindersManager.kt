package com.tanush.bouncer.utils

import android.content.Context
import com.tanush.bouncer.data.prefs.BouncerPreferences
import com.tanush.bouncer.data.prefs.PreferenceStore
import org.json.JSONArray
import org.json.JSONObject
import io.sentry.Sentry

data class Reminder(val id: Int, val title: String, val body: String, val triggerAt: Long)

object RemindersManager {
    private const val KEY = "reminders"

    fun saveReminder(context: Context, reminder: Reminder) {
        try {
            PreferenceStore.initialize(context)
            val arr = JSONArray(BouncerPreferences.getString(KEY, "[]"))
            val obj = JSONObject()
            obj.put("id", reminder.id)
            obj.put("title", reminder.title)
            obj.put("body", reminder.body)
            obj.put("triggerAt", reminder.triggerAt)
            arr.put(obj)
            BouncerPreferences.putString(KEY, arr.toString())
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    fun getReminders(context: Context): List<Reminder> {
        try {
            PreferenceStore.initialize(context)
            val arr = JSONArray(BouncerPreferences.getString(KEY, "[]"))
            val out = mutableListOf<Reminder>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Reminder(o.getInt("id"), o.getString("title"), o.getString("body"), o.getLong("triggerAt")))
            }
            return out
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
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
            throw e
        }
    }

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
                    val obj = JSONObject()
                    obj.put("id", reminder.id)
                    obj.put("title", reminder.title)
                    obj.put("body", reminder.body)
                    obj.put("triggerAt", reminder.triggerAt)
                    newArr.put(obj)
                    replaced = true
                } else {
                    newArr.put(o)
                }
            }
            if (!replaced) {
                val obj = JSONObject()
                obj.put("id", reminder.id)
                obj.put("title", reminder.title)
                obj.put("body", reminder.body)
                obj.put("triggerAt", reminder.triggerAt)
                newArr.put(obj)
            }
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

    fun clearAll(context: Context) {
        try {
            PreferenceStore.initialize(context)
            BouncerPreferences.putString(KEY, "[]")
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

/** Pure half of [RemindersManager.upcoming]. */
internal fun upcomingOf(reminders: List<Reminder>, nowMs: Long): List<Reminder> =
    reminders.filter { it.triggerAt > nowMs }.sortedBy { it.triggerAt }
