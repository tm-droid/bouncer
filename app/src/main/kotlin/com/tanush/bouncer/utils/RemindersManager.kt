package com.tanush.bouncer.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Reminder(val id: Int, val title: String, val body: String, val triggerAt: Long)

object RemindersManager {
    private const val KEY = "reminders"

    fun saveReminder(context: Context, reminder: Reminder) {
        val prefs = context.getSharedPreferences("bouncer_prefs", Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        val obj = JSONObject()
        obj.put("id", reminder.id)
        obj.put("title", reminder.title)
        obj.put("body", reminder.body)
        obj.put("triggerAt", reminder.triggerAt)
        arr.put(obj)
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun getReminders(context: Context): List<Reminder> {
        val prefs = context.getSharedPreferences("bouncer_prefs", Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        val out = mutableListOf<Reminder>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Reminder(o.getInt("id"), o.getString("title"), o.getString("body"), o.getLong("triggerAt")))
        }
        return out
    }

    fun deleteReminder(context: Context, id: Int) {
        val prefs = context.getSharedPreferences("bouncer_prefs", Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getInt("id") != id) newArr.put(o)
        }
        prefs.edit().putString(KEY, newArr.toString()).apply()
    }

    fun updateReminder(context: Context, reminder: Reminder) {
        // Replace reminder with same id
        val prefs = context.getSharedPreferences("bouncer_prefs", Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY, "[]"))
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
        prefs.edit().putString(KEY, newArr.toString()).apply()
    }

    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences("bouncer_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(KEY, "[]").apply()
    }
}
