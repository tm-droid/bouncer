package com.tanush.bouncer.data.remote

import android.util.Log
import com.tanush.bouncer.ApiKeys
import com.tanush.bouncer.data.CacheConfig
import com.tanush.bouncer.data.local.entities.PlayerCacheEntity
import io.appwrite.Permission
import io.appwrite.Role
import io.appwrite.exceptions.AppwriteException
import io.appwrite.services.TablesDB
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter

// Global cross-device player profile cache backed by Appwrite TablesDB.
// Eliminates per-device Cricbuzz scraping: first device writes, all others read.
// Uses the same DB as PlayerFormsClient; separate collection for full profile data.
// Falls back silently on any error — scraper is always the last resort.
object AppwritePlayerProfilesClient {
    private const val TAG = "AppwritePlayerProfilesClient"

    private var dbId = ""
    private var tableId = ""
    var isReady = false
        private set

    private val tablesDB: TablesDB?
        get() = AppwriteClient.client?.let { TablesDB(it) }

    fun initialize() {
        dbId = ApiKeys.appwritePlayerFormsDbId  // reuse player forms DB
        tableId = ApiKeys.appwritePlayerProfilesColId
        isReady = AppwriteClient.isReady && dbId.isNotBlank() && tableId.isNotBlank()
        if (!isReady) {
            Log.w(TAG, "Appwrite player profiles not configured — remote profile cache disabled")
        }
    }

    /**
     * Fetch a cached player profile by its Room playerId (e.g. "virat_kohli").
     * Returns null if not found, stale (>7 days), or Appwrite unreachable.
     */
    suspend fun pullProfile(playerId: String): PlayerCacheEntity? {
        if (!isReady) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val db = tablesDB ?: return@runCatching null
                // getRow() uses the primary key directly; Query.equal("$id") is unsupported in TablesDB
                // and silently returns no rows, causing every profile lookup to miss the cache.
                val row = try {
                    db.getRow(databaseId = dbId, tableId = tableId, rowId = playerId)
                } catch (e: AppwriteException) {
                    if (e.code == 404) return@runCatching null
                    throw e
                }
                val data = row.data
                val updatedAt = data["updated_at"] as? String
                if (updatedAt != null) {
                    val updatedMs = runCatching { Instant.parse(updatedAt).toEpochMilli() }.getOrNull() ?: 0L
                    if (updatedMs < System.currentTimeMillis() - CacheConfig.PLAYER_PROFILE_TTL_MS) {
                        Log.d(TAG, "Remote profile stale for $playerId")
                        return@runCatching null
                    }
                }
                val name = data["name"] as? String ?: return@runCatching null
                PlayerCacheEntity(
                    playerId = playerId,
                    name = name,
                    country = data["country"] as? String ?: "",
                    role = data["role"] as? String ?: "",
                    teamName = data["team_name"] as? String ?: "",
                    statsJson = data["stats_json"] as? String ?: "{}",
                    lastUpdated = updatedAt
                        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                        ?: System.currentTimeMillis()
                )
            }.onFailure { e ->
                Sentry.addBreadcrumb("AppwritePlayerProfilesClient.pullProfile failed for $playerId: ${e.message}")
                if (e is AppwriteException) {
                    Log.d(TAG, "Pull profile failed (silent): ${e.message} [${e.code}]")
                } else {
                    Log.d(TAG, "Pull profile failed (silent): ${e.message}")
                }
            }.getOrNull()
        }
    }

    /**
     * Upsert a player profile (try update → on 404 create).
     * Row ID = entity.playerId so the same player always maps to the same row.
     */
    suspend fun pushProfile(entity: PlayerCacheEntity) {
        if (!isReady) return
        val rowId = entity.playerId
        val updatedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        withContext(Dispatchers.IO) {
            runCatching {
                val db = tablesDB ?: return@runCatching
                val data = mapOf(
                    "name" to entity.name,
                    "country" to entity.country,
                    "role" to entity.role,
                    "team_name" to entity.teamName,
                    "stats_json" to entity.statsJson,
                    "updated_at" to updatedAt
                )
                runCatching {
                    db.updateRow(
                        databaseId = dbId,
                        tableId = tableId,
                        rowId = rowId,
                        data = data
                    )
                }.onFailure { e ->
                    if (e is AppwriteException && e.code == 404) {
                        db.createRow(
                            databaseId = dbId,
                            tableId = tableId,
                            rowId = rowId,
                            data = data,
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
                Sentry.addBreadcrumb("AppwritePlayerProfilesClient.pushProfile failed for ${entity.playerId}: ${e.message}")
                if (e is AppwriteException) {
                    Log.d(TAG, "Push profile failed (silent): ${e.message} [${e.code}]")
                } else {
                    Log.d(TAG, "Push profile failed (silent): ${e.message}")
                }
            }
        }
    }
}
