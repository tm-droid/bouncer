package com.tanush.bouncer.data.remote

import android.util.Log
import com.tanush.bouncer.ApiKeys
import io.appwrite.Permission
import io.appwrite.Query
import io.appwrite.Role
import io.appwrite.exceptions.AppwriteException
import io.appwrite.services.TablesDB
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter

data class PlayerFormSnapshot(
    val playerName: String,
    val recentSR: Double,
    val recentEconomy: Double,
    val matchCount: Int
)

// Cross-device player form cache backed by Appwrite TablesDB.
// Syncs recent strike-rate / economy snapshots so ML features stay consistent
// across devices and sessions. Falls back silently on any error.
object AppwritePlayerFormsClient {
    private const val TAG = "AppwritePlayerFormsClient"

    private var dbId = ""
    private var tableId = ""
    var isReady = false
        private set

    private val tablesDB: TablesDB?
        get() = AppwriteClient.client?.let { TablesDB(it) }

    fun initialize() {
        dbId = ApiKeys.appwritePlayerFormsDbId
        tableId = ApiKeys.appwritePlayerFormsColId
        isReady = AppwriteClient.isReady && dbId.isNotBlank() && tableId.isNotBlank()
        if (!isReady) {
            Log.w(TAG, "Appwrite player forms not configured — remote form sync disabled")
        }
    }

    /**
     * Upsert a player form snapshot (try update → on 404 create).
     * Uses the normalized player name as the stable row ID.
     */
    suspend fun pushFormSnapshot(
        playerName: String,
        sr: Double,
        economy: Double,
        matchCount: Int
    ) {
        if (!isReady) return
        val rowId = normalizePlayerId(playerName)
        val updatedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        withContext(Dispatchers.IO) {
            runCatching {
                val db = tablesDB ?: return@runCatching
                val data = mapOf(
                    "player_name" to playerName,
                    "recent_sr" to sr,
                    "recent_economy" to economy,
                    "match_count" to matchCount,
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
                        // Row doesn't exist yet — create it
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
                Sentry.addBreadcrumb("AppwritePlayerFormsClient.pushFormSnapshot failed for $playerName: ${e.message}")
                if (e is AppwriteException) {
                    Log.d(TAG, "Push form snapshot failed (silent): ${e.message} [${e.code}]")
                } else {
                    Log.d(TAG, "Push form snapshot failed (silent): ${e.message}")
                }
            }
        }
    }

    /**
     * Fetch all player form documents, keyed by player_name.
     * Uses cursor-based pagination to collect all rows (Appwrite default limit is 25).
     * Returns empty map on any error so callers degrade gracefully.
     */
    suspend fun pullAllForms(): Map<String, PlayerFormSnapshot> {
        if (!isReady) return emptyMap()
        return withContext(Dispatchers.IO) {
            runCatching {
                val db = tablesDB ?: return@runCatching emptyMap<String, PlayerFormSnapshot>()
                val allRows = mutableListOf<Map<String, Any>>()
                var lastId: String? = null
                while (true) {
                    val queries = buildList {
                        add(Query.limit(100))
                        if (lastId != null) add(Query.cursorAfter(lastId!!))
                    }
                    val page = db.listRows(
                        databaseId = dbId,
                        tableId = tableId,
                        queries = queries
                    )
                    @Suppress("UNCHECKED_CAST")
                    allRows.addAll(page.rows.map { it.data as? Map<String, Any> ?: emptyMap() })
                    if (page.rows.size < 100) break
                    lastId = page.rows.last().id
                }
                allRows.mapNotNull { data ->
                    val name = data["player_name"] as? String ?: return@mapNotNull null
                    name to PlayerFormSnapshot(
                        playerName = name,
                        recentSR = (data["recent_sr"] as? Number)?.toDouble() ?: 0.0,
                        recentEconomy = (data["recent_economy"] as? Number)?.toDouble() ?: 0.0,
                        matchCount = (data["match_count"] as? Number)?.toInt() ?: 0
                    )
                }.toMap()
            }.onFailure { e ->
                Sentry.addBreadcrumb("AppwritePlayerFormsClient.pullAllForms failed: ${e.message}")
                if (e is AppwriteException) {
                    Log.d(TAG, "Pull all forms failed (silent): ${e.message} [${e.code}]")
                } else {
                    Log.d(TAG, "Pull all forms failed (silent): ${e.message}")
                }
            }.getOrElse { emptyMap() }
        }
    }

    /** Normalize a player name to a stable, safe Appwrite row ID. */
    private fun normalizePlayerId(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9_-]"), "-").take(36)
}
