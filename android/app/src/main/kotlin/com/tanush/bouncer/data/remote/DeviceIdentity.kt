package com.tanush.bouncer.data.remote

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * The stable per-install id this device presents to Appwrite functions.
 *
 * Not a credential and not authentication — the app is deliberately unauthenticated (see
 * `.claude/rules/appwrite-patterns.md`). It is a bucket key: `bouncer-generate-commentary` rate
 * limits per device so one runaway client cannot burn the project's provider spend, and a request
 * without it simply lands in the server's shared IP bucket rather than being let through.
 *
 * The id itself predates this file — the since-deleted favourites sync persisted
 * `appwrite_device_id`, and the same value is reused so a device does not acquire a second identity.
 */
object DeviceIdentity {
    /** The key the deleted favourites sync used, kept so no install acquires a second id. */
    const val PREF_DEVICE_ID = "appwrite_device_id"

    /**
     * `favourite_teams`, because that is where the id already is.
     *
     * The favourites sync stored every existing install's UUID in that file. Reading from a
     * tidier-sounding file would mint a second id for every device already out there.
     */
    private const val PREFS_NAME = "favourite_teams"

    @Volatile
    private var cached: String = ""

    /**
     * The device id, or `"unidentified"` before [initialize] has run.
     *
     * Never blank: a blank header would read to the server as a missing one, quietly moving every
     * such request into the shared IP bucket where devices throttle each other.
     */
    val id: String get() = cached.ifEmpty { "unidentified" }

    /** Idempotent; generates and persists the id on first call. */
    @Synchronized
    fun initialize(context: Context) {
        if (cached.isNotEmpty()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        cached = readOrCreate(prefs)
    }

    /** Direct-prefs entry point for callers that already hold the right [SharedPreferences]. */
    @Synchronized
    fun initialize(prefs: SharedPreferences) {
        if (cached.isNotEmpty()) return
        cached = readOrCreate(prefs)
    }

    private fun readOrCreate(prefs: SharedPreferences): String =
        prefs.getString(PREF_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(PREF_DEVICE_ID, it).apply()
        }
}
