package com.tanush.bouncer.liveactivity

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmapOrNull
import coil.Coil
import coil.request.ImageRequest
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.services.MatchCacheService

/**
 * The two things a Live Update needs beyond the live payload: the cached list entry a match came
 * from, and its team's logo as a bitmap.
 *
 * Both are best-effort by design. A subscription outlives the match cache and a logo fetch can
 * fail offline; neither is a reason to withhold a score.
 */

/**
 * Every cached live match, or **null when the cache has nothing to say** — which is not the same
 * as "no match is live". Callers must treat null as unknown and not act on it as a negative.
 *
 * Falls through to the offline copy because the TTL is tuned for a foreground list, and a
 * background service tracking a six-hour Test sits past it for most of the match. A stale team
 * name is worth far more than no team name.
 */
suspend fun cachedLiveMatches(): List<CricketMatch>? =
    MatchCacheService.getMatches(LIVE_STATUS) ?: MatchCacheService.getOfflineMatches(LIVE_STATUS)

/**
 * The cached list entry for [matchId], from whichever list holds it, or null when none does.
 *
 * Deliberately not the Live list alone. A match leaves that list the instant it finishes and lands
 * in Recent, so a Live-only lookup returns null for exactly the match whose result the card still
 * needs — leaving the frozen `match-live-detail` payload as the only vote on whether the match is
 * over, which is the staleness `eb91dcd` fixed on the match-detail screen. Live first because that
 * is where a running match is and where the search should stop.
 */
suspend fun cachedMatchEntry(matchId: String): CricketMatch? =
    listOf(MatchCacheService.LIVE, MatchCacheService.RECENT, MatchCacheService.UPCOMING)
        .firstNotNullOfOrNull { status ->
            (MatchCacheService.getMatches(status) ?: MatchCacheService.getOfflineMatches(status))
                ?.firstOrNull { it.id == matchId }
        }

/**
 * The notification shown between `startForegroundService` and the first payload.
 *
 * The platform gives roughly five seconds to call `startForeground`, which is far less than a
 * network round trip, so something has to be posted before any score exists. It shows the fixture
 * and an indeterminate bar — the documented way to say "working" without inventing a position.
 */
fun placeholderContent(matchId: String, match: CricketMatch?, fallbackTitle: String): LiveUpdateContent =
    LiveUpdateContent(
        matchId = matchId,
        subText = match?.matchType?.takeIf { it.isNotBlank() },
        title = match?.teams?.joinToString(" v ") { it.name }?.takeIf { it.isNotBlank() }
            ?: match?.matchTitle?.takeIf { it.isNotBlank() }
            ?: fallbackTitle,
        text = match?.currentStatus?.takeIf { it.isNotBlank() } ?: fallbackTitle,
        bigText = "",
        scoreboard = LiveUpdateScoreboard(
            battingName = null,
            battingScore = null,
            battingOvers = null,
            opponentName = null,
            opponentScore = null,
            batters = emptyList(),
            bowler = null,
            recentBalls = emptyList(),
            statusLine = match?.currentStatus?.takeIf { it.isNotBlank() } ?: fallbackTitle,
        ),
        shortCriticalText = null,
        accentTeamName = match?.teams?.firstOrNull()?.name.orEmpty(),
        logoTeamName = null,
        opponentLogoTeamName = null,
        isComplete = false,
    )

/**
 * Team logos as bitmaps for `setLargeIcon`.
 *
 * `TeamLogoManager` resolves URLs only — there is no drawable set and no synchronous accessor — so
 * this goes through the app-wide Coil loader configured in `BouncerApp`, which shares the OkHttp
 * client and its disk cache. `allowHardware(false)` is required: a hardware bitmap cannot be
 * parcelled into a notification.
 */
object LiveUpdateLogoLoader {

    private const val LOGO_PX = 128

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

    suspend fun bitmapFor(context: Context, teamName: String?): Bitmap? {
        val name = teamName?.takeIf { it.isNotBlank() } ?: return null
        cache[name]?.let { return it }
        val url = TeamLogoManager.getBestLogoUrl(name, null, LOGO_PX, LOGO_PX) ?: return null
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(LOGO_PX)
            .allowHardware(false)
            .build()
        val bitmap = Coil.imageLoader(context).execute(request).drawable?.toBitmapOrNull() ?: return null
        cache[name] = bitmap
        return bitmap
    }
}

/** The cache is keyed by this exact string; see [MatchCacheService.LIVE]. */
private val LIVE_STATUS = MatchCacheService.LIVE
