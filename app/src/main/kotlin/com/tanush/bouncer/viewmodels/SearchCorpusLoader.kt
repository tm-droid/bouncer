package com.tanush.bouncer.viewmodels

import com.tanush.bouncer.data.SearchRepository
import com.tanush.bouncer.data.local.BouncerDatabase
import com.tanush.bouncer.managers.TeamAcronymManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.network.SeriesScraper
import com.tanush.bouncer.utils.sentryBreadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The Room and bundled corpora search ranks in memory. */
internal class SearchCorpus(
    val teams: List<SearchRepository.TeamEntry>,
    val series: List<SearchRepository.SeriesEntry>,
    val players: List<SearchRepository.PlayerEntry>,
)

/**
 * Loads the search corpora once per screen and keeps them.
 *
 * Series and cached players come from Room on first use. The team list is rebuilt when the loaded
 * matches change, because the teams in a loaded match carry Cricbuzz's own logo and short code.
 */
internal class SearchCorpusLoader(
    private val db: BouncerDatabase,
    private val seriesScraper: SeriesScraper,
) {
    private val lock = Mutex()
    private var corpus: SearchCorpus? = null
    private var teamCorpusMatchIds: Set<String> = emptySet()

    suspend fun corpusFor(matches: List<CricketMatch>): SearchCorpus = lock.withLock {
        val matchIds = matches.mapTo(HashSet()) { it.id }
        val loaded = corpus
        if (loaded != null && matchIds == teamCorpusMatchIds) return@withLock loaded

        val fresh = SearchCorpus(
            // ~1,000 names normalised once each; not work for the main thread.
            teams = withContext(Dispatchers.Default) {
                SearchRepository.buildTeamCorpus(matches, TeamAcronymManager.allTeams())
            },
            series = loaded?.series ?: loadSeries(),
            players = loaded?.players ?: loadPlayers(),
        )
        teamCorpusMatchIds = matchIds
        corpus = fresh
        fresh
    }

    /**
     * `series_cache`, or a one-off live scrape when the table is empty — a fresh install before
     * `SeriesSyncWorker` first runs. The scrape used to run on every keystroke that Room had no
     * answer for, which on a normal install is most of them.
     */
    private suspend fun loadSeries(): List<SearchRepository.SeriesEntry> {
        val cached = try {
            db.seriesCacheDao().getAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Sentry.captureException(e)
            emptyList()
        }
        if (cached.isNotEmpty()) {
            return cached.map { SearchRepository.SeriesEntry(it.seriesId, it.name, it.category, it.startDate, it.endDate) }
        }
        val scraped = try {
            seriesScraper.fetchActiveSeries()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The Series tab reports its own scrape failures; search still has every other source.
            sentryBreadcrumb("search.series", "Series scrape failed: ${e.message}", SentryLevel.WARNING)
            emptyList()
        }
        return scraped.map { SearchRepository.SeriesEntry(it.id, it.name, it.category.name, it.startDate, it.endDate) }
    }

    private suspend fun loadPlayers(): List<SearchRepository.PlayerEntry> = try {
        db.playerCacheDao().searchCorpus().map { SearchRepository.PlayerEntry(it.name, it.teamName, it.role) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Sentry.captureException(e)
        emptyList()
    }
}
