package com.tanush.bouncer.data.remote

import android.content.SharedPreferences
import android.util.Log
import com.tanush.bouncer.ApiKeys
import io.appwrite.ID
import io.appwrite.Permission
import io.appwrite.Role
import io.appwrite.exceptions.AppwriteException
import io.appwrite.services.TablesDB
import java.util.UUID

// Cross-device favourites sync backed by Appwrite TablesDB.
// Uses a stable device UUID as the row ID — no auth required.
// Table-level permissions: create("any"), read("any"), update("any").
object AppwriteFavouritesSync {
    private const val TAG = "AppwriteFavouritesSync"
    private const val PREF_DEVICE_ID = "appwrite_device_id"

    private var dbId = ""
    private var tableId = ""
    private var deviceId = ""
    var isReady = false
        private set

    fun initialize(prefs: SharedPreferences) {
        dbId = ApiKeys.appwriteAiCacheDbId
        tableId = ApiKeys.appwriteFavouritesColId

        isReady = AppwriteClient.isReady && listOf(dbId, tableId).all { it.isNotBlank() }
        if (!isReady) {
            Log.w(TAG, "Appwrite favourites sync not configured — sync disabled")
            return
        }

        deviceId = prefs.getString(PREF_DEVICE_ID, null) ?: run {
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(PREF_DEVICE_ID, id).apply()
            id
        }
    }

    suspend fun pull(): List<String>? {
        if (!isReady) return null
        val client = AppwriteClient.client ?: return null
        return runCatching {
            val tablesDB = TablesDB(client)
            val row = tablesDB.getRow(
                databaseId = dbId,
                tableId = tableId,
                rowId = deviceId
            )
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

    suspend fun push(teams: List<String>) {
        if (!isReady) return
        val client = AppwriteClient.client ?: return
        runCatching {
            val tablesDB = TablesDB(client)
            runCatching {
                tablesDB.updateRow(
                    databaseId = dbId,
                    tableId = tableId,
                    rowId = deviceId,
                    data = mapOf("teams" to teams)
                )
            }.onFailure { e ->
                if (e is AppwriteException && e.code == 404) {
                    // Row doesn't exist yet — create it
                    tablesDB.createRow(
                        databaseId = dbId,
                        tableId = tableId,
                        rowId = deviceId,
                        data = mapOf("teams" to teams),
                        permissions = listOf(
                            Permission.read(Role.any()),
                            Permission.update(Role.any())
                        )
                    )
                } else {
                    throw e
                }
            }
        }.onFailure { e ->
            Log.d(TAG, "Remote favourites push failed (silent): ${e.message}")
        }
    }
}
