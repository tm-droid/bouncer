package com.tanush.bouncer.data.remote

import android.content.SharedPreferences
import android.util.Log
import com.tanush.bouncer.ApiKeys
import io.appwrite.Permission
import io.appwrite.Role
import io.appwrite.exceptions.AppwriteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

// Cross-device favourites sync backed by Appwrite TablesDB.
// Uses a stable device UUID as the row ID — no auth required.
// Row-level permissions on createRow: read("any"), update("any") — no create("any").
// SECURITY NOTE: Existing tables in Appwrite console may have create("any") set at table level;
// update those via the Appwrite console to match. This client code only controls NEW row permissions.
object AppwriteFavouritesSync : AppwriteTableClient("AppwriteFavouritesSync") {
    private const val TAG = "AppwriteFavouritesSync"
    private const val PREF_DEVICE_ID = "appwrite_device_id"

    private var deviceId = ""

    fun initialize(prefs: SharedPreferences) {
        initTable(ApiKeys.appwriteAiCacheDbId, ApiKeys.appwriteFavouritesColId)
        if (!isReady) return

        deviceId = prefs.getString(PREF_DEVICE_ID, null) ?: run {
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(PREF_DEVICE_ID, id).apply()
            id
        }
    }

    suspend fun pull(): List<String>? {
        if (!isReady) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val row = guardedRead("favourites.getRow") {
                    it.getRow(
                        databaseId = dbId,
                        tableId = tableId,
                        rowId = deviceId
                    )
                } ?: return@runCatching null
                @Suppress("UNCHECKED_CAST")
                row.data["teams"] as? List<String>
            }.onFailure { e ->
                if (e is AppwriteException && e.code == 404) {
                    // Row doesn't exist yet — not an error
                } else {
                    Log.d(TAG, "Remote favourites pull failed (silent): ${e.message}")
                }
            }.getOrNull()
        }
    }

    suspend fun push(teams: List<String>) {
        if (!isReady) return
        withContext(Dispatchers.IO) {
            runCatching {
                runCatching {
                    guardedWrite("favourites.update") {
                        it.updateRow(
                            databaseId = dbId,
                            tableId = tableId,
                            rowId = deviceId,
                            data = mapOf("teams" to teams)
                        )
                    }
                }.onFailure { e ->
                    if (e is AppwriteException && e.code == 404) {
                        // Row doesn't exist yet — create it
                        guardedWrite("favourites.create") {
                            it.createRow(
                                databaseId = dbId,
                                tableId = tableId,
                                rowId = deviceId,
                                data = mapOf("teams" to teams),
                                // Row-level permissions: public read for cross-device sync;
                                // update but NOT create at role-any level (row is device-scoped).
                                // SECURITY NOTE: Existing tables in Appwrite console may have
                                // create("any") at table level — update via console to match.
                                // This code controls NEW row permissions only, not the table schema.
                                permissions = listOf(
                                    Permission.read(Role.any()),
                                    Permission.update(Role.any())
                                )
                            )
                        }
                    } else {
                        throw e
                    }
                }
            }.onFailure { e ->
                Log.d(TAG, "Remote favourites push failed (silent): ${e.message}")
            }
        }
    }
}
