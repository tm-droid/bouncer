package com.tanush.bouncer.viewmodels

import android.app.Application
import com.tanush.bouncer.ui.components.v2.FailureKind
import com.tanush.bouncer.ui.components.v2.failureKindOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.local.BouncerDatabase
import com.tanush.bouncer.data.report
import com.tanush.bouncer.data.local.entities.SeriesCacheEntity
import com.tanush.bouncer.models.Series
import com.tanush.bouncer.network.SeriesScraper
import com.tanush.bouncer.network.isUpstreamOutage
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Year

/**
 * The Series tab: **Current** (on now or upcoming) and **Past** (browsed by year).
 *
 * Two corpora exist for this one fact and the tab used to read neither cache. `SeriesSyncWorker`
 * downloads `active-series` — which `bouncer-poll-live-matches` fills with active **and archived**
 * series — into the Room `series_cache`, and only `SearchRepository` read it. The tab live-scraped
 * Cricbuzz on every open, so it could not show a series search could find, showed nothing at all
 * offline, and replaced the list with an error screen whenever the scrape failed.
 *
 * Current paints the cache's still-running rows, then merges the scrape over them by id. It used to
 * *assign* the scrape, which threw every cached row away the moment it landed — and the scrape,
 * `/cricket-schedule/series/all`, lists current and upcoming series only, so nothing finished could
 * be opened from this tab at all. Past reads `/cricket-scorecard-archives/{year}`, one year per
 * fetch, kept for the life of the screen.
 */
class SeriesListViewModel(application: Application) : AndroidViewModel(application) {
    enum class Mode { CURRENT, PAST }

    private val _series = MutableStateFlow<List<Series>>(emptyList())
    val series: StateFlow<List<Series>> = _series.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _failure = MutableStateFlow<FailureKind?>(null)
    val failure: StateFlow<FailureKind?> = _failure.asStateFlow()

    private val _mode = MutableStateFlow(Mode.CURRENT)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    /** Newest first, down to the earliest year Cricbuzz archives. */
    val archiveYears: List<Int> = (Year.now().value downTo SeriesScraper.FIRST_ARCHIVE_YEAR).toList()

    private val _selectedYear = MutableStateFlow(archiveYears.first())
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    private var current: List<Series> = emptyList()
    private val pastByYear = mutableMapOf<Int, List<Series>>()
    private var currentJob: Job? = null
    private var pastJob: Job? = null

    init {
        loadCurrent()
    }

    fun retry() = when (_mode.value) {
        Mode.CURRENT -> loadCurrent()
        Mode.PAST -> {
            pastByYear.remove(_selectedYear.value)
            loadPast(_selectedYear.value)
        }
    }

    fun selectMode(mode: Mode) {
        if (mode == _mode.value) return
        _mode.value = mode
        when (mode) {
            Mode.CURRENT -> if (currentJob?.isActive == true || current.isNotEmpty()) {
                // Still loading, or already loaded: show what there is and let the load finish it.
                _series.value = current
                _failure.value = null
                _isLoading.value = current.isEmpty()
            } else {
                loadCurrent()
            }
            Mode.PAST -> loadPast(_selectedYear.value)
        }
    }

    fun selectYear(year: Int) {
        _selectedYear.value = year
        if (_mode.value == Mode.PAST) loadPast(year)
    }

    private fun showLoaded(list: List<Series>) {
        _series.value = list
        _failure.value = null
        _isLoading.value = false
    }

    // The two loads run independently — switching to Past must not cancel the Current scrape — so
    // each writes the visible state only while its own mode (and year) is the one on screen.
    private fun showingCurrent() = _mode.value == Mode.CURRENT
    private fun showingPast(year: Int) = _mode.value == Mode.PAST && _selectedYear.value == year

    private fun loadCurrent() {
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            if (showingCurrent()) {
                _isLoading.value = true
                _failure.value = null
            }
            val cachedCurrent = cachedCurrentSeries(getApplication())
            if (cachedCurrent.isNotEmpty()) {
                current = cachedCurrent
                // Content is on screen, so the spinner has nothing left to say; the scrape below
                // continues and merges into the list when it lands.
                if (showingCurrent()) showLoaded(cachedCurrent)
            }
            refreshCurrent(cachedCurrent)
        }
    }

    private suspend fun refreshCurrent(cachedCurrent: List<Series>) {
        try {
            current = mergeCurrentSeries(SeriesScraper().fetchActiveSeries(), cachedCurrent)
            if (showingCurrent()) showLoaded(current)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!isUpstreamOutage(e)) Sentry.captureException(e)
            // With a list on screen the failure becomes the stale-data banner, not an error page.
            if (showingCurrent()) {
                _failure.value = failureKindOf(e)
                _isLoading.value = false
            }
        }
    }

    private fun loadPast(year: Int) {
        pastByYear[year]?.let { return showLoaded(it) }
        pastJob?.cancel()
        _isLoading.value = true
        _failure.value = null
        _series.value = emptyList()
        pastJob = viewModelScope.launch {
            when (val result = SeriesScraper().fetchArchivedSeries(year)) {
                is AppResult.Success -> {
                    val past = pastSeriesOf(result.data, System.currentTimeMillis())
                    pastByYear[year] = past
                    if (showingPast(year)) showLoaded(past)
                }
                is AppResult.Error -> {
                    result.report()
                    if (showingPast(year)) {
                        _failure.value = failureKindOf(result.exception)
                        _isLoading.value = false
                    }
                }
                AppResult.Loading -> Unit
            }
        }
    }
}

private suspend fun cachedCurrentSeries(application: Application): List<Series> = runCatching {
    BouncerDatabase.getInstance(application).seriesCacheDao().getAll()
}.getOrDefault(emptyList()).map(::seriesFrom).let { currentSeriesOf(it, System.currentTimeMillis()) }

/**
 * The cached rows still running or yet to start. The cache also holds every archived series the
 * server has walked, which belong under Past; a row with no end date cannot be placed and stays
 * out — the scrape is the authority for what is on now, the cache only a first paint.
 */
internal fun currentSeriesOf(cached: List<Series>, nowMs: Long): List<Series> =
    cached.filter { series -> series.endDate.toLongOrNull()?.let { it >= nowMs } == true }

/** The scrape in its own order, then any cached current series it did not list. Scrape wins per id. */
internal fun mergeCurrentSeries(scraped: List<Series>, cachedCurrent: List<Series>): List<Series> {
    val ids = scraped.mapTo(HashSet()) { it.id }
    return scraped + cachedCurrent.filter { it.id !in ids }
}

/** An archive year minus the series still running, which Current already lists. */
internal fun pastSeriesOf(archived: List<Series>, nowMs: Long): List<Series> =
    archived.filter { series -> series.endDate.toLongOrNull()?.let { it < nowMs } ?: true }

/**
 * A cached row as a [Series].
 *
 * `season`, `format` and `isWomens` have no column — the table predates them — so they take their
 * defaults here and are filled in the moment the scrape lands. A cached row is a first paint and an
 * offline answer, not a replacement for the live page.
 */
private fun seriesFrom(entity: SeriesCacheEntity) = Series(
    id = entity.seriesId,
    name = entity.name,
    startDate = entity.startDate,
    endDate = entity.endDate,
    category = runCatching { Series.SeriesCategory.valueOf(entity.category) }
        .getOrDefault(Series.SeriesCategory.INTERNATIONAL),
)
