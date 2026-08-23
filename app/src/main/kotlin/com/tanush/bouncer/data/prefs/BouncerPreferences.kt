package com.tanush.bouncer.data.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow

/**
 * Typed surface over [PreferenceStore], which holds the storage engine and the reasoning behind it.
 *
 * A preference name carries one value of one type: `Preferences.Key` compares by name and ignores
 * its type parameter, so writing a name as one type and reading it as another reads back the wrong
 * thing — the same hazard SharedPreferences had.
 */
object BouncerPreferences {

    fun getBoolean(key: String, default: Boolean): Boolean =
        PreferenceStore.read(booleanPreferencesKey(key), default)

    fun getInt(key: String, default: Int): Int =
        PreferenceStore.read(intPreferencesKey(key), default)

    fun getLong(key: String, default: Long): Long =
        PreferenceStore.read(longPreferencesKey(key), default)

    fun getString(key: String, default: String): String =
        PreferenceStore.read(stringPreferencesKey(key), default)

    fun putBoolean(key: String, value: Boolean) =
        PreferenceStore.write(booleanPreferencesKey(key), value)

    fun putInt(key: String, value: Int) = PreferenceStore.write(intPreferencesKey(key), value)

    fun putLong(key: String, value: Long) = PreferenceStore.write(longPreferencesKey(key), value)

    fun putString(key: String, value: String) =
        PreferenceStore.write(stringPreferencesKey(key), value)

    fun remove(vararg keys: String) = PreferenceStore.removeKeys(keys.toList())

    /**
     * Re-emits when the value changes, unlike [getBoolean] — so a toggle on the Settings screen
     * reaches a consumer that is already composed.
     */
    fun flowOfBoolean(key: String, default: Boolean): Flow<Boolean> =
        PreferenceStore.observe(booleanPreferencesKey(key), default)
}
