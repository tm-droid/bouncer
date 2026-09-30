package com.tanush.bouncer.network

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.tanush.bouncer.utils.BoundedLruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One player on a match's team sheet. [id] is Cricbuzz's profile id. */
data class SquadPlayer(
    val id: String,
    val name: String,
    val role: String,
    val captain: Boolean,
    val keeper: Boolean,
)

/**
 * One side's team sheet. [playingXi] and [bench] exist once the toss is done; before it Cricbuzz
 * publishes only [squad]. A side carries one shape or the other, never both.
 */
data class TeamSquad(
    val teamId: String,
    val name: String,
    val shortName: String,
    val playingXi: List<SquadPlayer>,
    val bench: List<SquadPlayer>,
    val squad: List<SquadPlayer>,
) {
    val hasPlayingXi: Boolean get() = playingXi.isNotEmpty()
}

data class MatchSquads(val team1: TeamSquad, val team2: TeamSquad) {
    val hasPlayingXi: Boolean get() = team1.hasPlayingXi && team2.hasPlayingXi
}

/**
 * Team sheets out of `/cricket-match-squads/{id}`, which is server-rendered: its flight stream
 * carries `{team1: {team, players}, team2: {team, players}}`, where `players` is keyed by group —
 * `"playing XI"` and `"bench"` after the toss, `"Squad"` before it, plus `"support staff"`.
 * Unlike the over-by-over page, which is client-rendered and carries nothing server-side.
 */
internal object MatchSquadsRsc {

    private const val GROUP_XI = "playing XI"
    private const val GROUP_BENCH = "bench"
    private const val GROUP_SQUAD = "Squad"

    /** The team sheets on this page, or null when it carries none in the expected shape. */
    fun parse(html: String): MatchSquads? {
        var holder: JsonObject? = null
        forEachNextFlightPayload(html) { payload ->
            if (holder == null) holder = findObject(payload) { o -> o.isSide("team1") && o.isSide("team2") }
        }
        val h = holder ?: return null
        val team1 = side(h.getAsJsonObject("team1")) ?: return null
        val team2 = side(h.getAsJsonObject("team2")) ?: return null
        return MatchSquads(team1, team2)
    }

    private fun JsonObject.isSide(key: String): Boolean =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject?.let { it.has("team") && it.has("players") } == true

    private fun side(obj: JsonObject): TeamSquad? {
        val team = obj.get("team")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val players = obj.get("players")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return TeamSquad(
            teamId = team.str("teamId").orEmpty(),
            name = team.str("teamName").orEmpty(),
            shortName = team.str("teamSName").orEmpty(),
            playingXi = players.group(GROUP_XI),
            bench = players.group(GROUP_BENCH),
            squad = players.group(GROUP_SQUAD),
        )
    }

    private fun JsonObject.group(key: String): List<SquadPlayer> {
        val arr = get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val p = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val name = p.str("name") ?: return@mapNotNull null
            SquadPlayer(
                id = p.str("id").orEmpty(),
                name = name,
                role = p.str("role").orEmpty(),
                captain = p.bool("captain"),
                keeper = p.bool("keeper"),
            )
        }
    }

    private fun findObject(element: JsonElement, predicate: (JsonObject) -> Boolean): JsonObject? {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                if (predicate(obj)) return obj
                for ((_, value) in obj.entrySet()) findObject(value, predicate)?.let { return it }
            }
            element.isJsonArray -> for (child in element.asJsonArray) findObject(child, predicate)?.let { return it }
        }
        return null
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asString }.getOrNull() }
            ?.takeIf { it.isNotBlank() && !it.startsWith("$") }

    private fun JsonObject.bool(key: String): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() } == true
}

/**
 * Fetches team sheets on demand — only when a reader opens the Info tab — and keeps them for the
 * process. A playing XI does not change once announced; a pre-toss squad is kept only
 * [PRE_TOSS_TTL_MS], so the XI appears once it is published. Costs Cricbuzz one page, no Appwrite.
 */
object MatchSquadsSource {
    private const val PRE_TOSS_TTL_MS = 10 * 60_000L
    private const val CACHE_SIZE = 16

    private val cache = BoundedLruCache<String, Pair<Long, MatchSquads>>(CACHE_SIZE)

    suspend fun get(matchId: String, nowMs: Long = System.currentTimeMillis()): MatchSquads? {
        synchronized(cache) { cache[matchId] }?.let { (at, squads) ->
            if (squads.hasPlayingXi || nowMs - at < PRE_TOSS_TTL_MS) return squads
        }
        val parsed = withContext(Dispatchers.IO) {
            runCatching {
                MatchSquadsRsc.parse(ScrapeFetcher.getString(CricBuzzUrls.squadsUrl(matchId), randomUserAgent()))
            }.getOrNull()
        } ?: return synchronized(cache) { cache[matchId]?.second }
        synchronized(cache) { cache[matchId] = nowMs to parsed }
        return parsed
    }
}
