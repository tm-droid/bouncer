package com.tanush.bouncer.data

import com.tanush.bouncer.data.repository.HighlightsRepository
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.services.MatchCacheService
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Feeds the match lists the app already fetches into the Room `matches` table, which is what
 * highlights are linked against.
 *
 * Upcoming is skipped: a highlight is of a match that has been played. Live is written so a clip of
 * a Test's first day can link while the match goes on; Recent additionally re-scores highlights
 * stored unmatched, on its slow cadence, because a clip arrives hours after its match — by which
 * time the match is on Recent.
 */
object PlayedMatchIndex {
    private var repository: HighlightsRepository? = null
    private var scope: CoroutineScope? = null

    // The Live list is re-fetched every 6s and is usually unchanged; an identical list is not
    // rewritten.
    private val lastRecorded = ConcurrentHashMap<String, List<CricketMatch>>()

    fun initialize(repository: HighlightsRepository, scope: CoroutineScope) {
        this.repository = repository
        this.scope = scope
    }

    fun record(status: String, matches: List<CricketMatch>) {
        if (status != MatchCacheService.LIVE && status != MatchCacheService.RECENT) return
        val repo = repository ?: return
        val launchScope = scope ?: return
        if (matches.isEmpty() || lastRecorded[status] == matches) return
        lastRecorded[status] = matches
        launchScope.launch(Dispatchers.IO) {
            runCatching { repo.recordMatches(matches, rematch = status == MatchCacheService.RECENT) }
                .onFailure {
                    // Let the next poll retry rather than treat a failed write as recorded.
                    lastRecorded.remove(status)
                    Sentry.captureException(it)
                }
        }
    }
}
