package com.tanush.bouncer.data.history

import com.tanush.bouncer.data.AppJson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** One side in `idx/teams.json`: `[name, gender, key, matches]`. */
data class HistoryTeam(val name: String, val women: Boolean, val key: String, val matches: Int)

/**
 * The Cricsheet site's list of sides, which the team and head-to-head records are keyed by
 * (`s/team/<key>.json`). Read once per process: the file changes weekly.
 *
 * A name resolves to a key only when it is certainly that side, the rule [HistoryMatchLinker]
 * applies to matches: an exact normalised name, else a single renamed candidate. An ambiguous
 * name is null, because a record of the wrong side is worse than none.
 */
object HistoryTeams {
    private const val PATH = "idx/teams.json"
    private const val MAX_AGE_SECONDS = 24 * 60 * 60
    private const val MAX_STALE_SECONDS = 7 * 24 * 60 * 60

    private val mutex = Mutex()
    @Volatile private var loaded: List<HistoryTeam>? = null

    /** Null when the file cannot be read; a failure is not remembered. */
    suspend fun load(): List<HistoryTeam>? {
        loaded?.let { return it }
        return mutex.withLock {
            loaded ?: fetch()?.also { loaded = it }
        }
    }

    suspend fun keyFor(name: String, women: Boolean): String? = load()?.let { keyFor(it, name, women) }

    /** How many matches Cricsheet has for [key], for ranking a team by how much cricket it plays. */
    suspend fun matchesFor(key: String): Int = load()?.firstOrNull { it.key == key }?.matches ?: 0

    internal fun keyFor(teams: List<HistoryTeam>, name: String, women: Boolean): String? {
        val sameGender = teams.filter { it.women == women }
        val exact = sameGender.filter { HistoryMatchLinker.canonical(it.name) == HistoryMatchLinker.canonical(name) }
        if (exact.isNotEmpty()) return exact.singleOrNull()?.key
        return sameGender.filter { HistoryMatchLinker.sameOrRenamed(it.name, name) }.singleOrNull()?.key
    }

    private suspend fun fetch(): List<HistoryTeam>? {
        val bytes = StaticSite.fetch(HistoryClient.BASE_URL, PATH, MAX_AGE_SECONDS, MAX_STALE_SECONDS) ?: return null
        return parse(bytes.decodeToString())
    }

    internal fun parse(json: String): List<HistoryTeam>? = runCatching {
        (AppJson.parseToJsonElement(json) as JsonArray).mapNotNull { row ->
            val cells = row as? JsonArray ?: return@mapNotNull null
            HistoryTeam(
                name = (cells.getOrNull(0) as? JsonPrimitive)?.content ?: return@mapNotNull null,
                women = (cells.getOrNull(1) as? JsonPrimitive)?.content == "female",
                key = (cells.getOrNull(2) as? JsonPrimitive)?.content ?: return@mapNotNull null,
                matches = (cells.getOrNull(3) as? JsonPrimitive)?.intOrNull ?: 0,
            )
        }
    }.getOrNull()
}
