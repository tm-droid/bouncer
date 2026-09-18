package com.tanush.bouncer.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.data.local.BouncerDatabase
import com.tanush.bouncer.data.local.entities.SeriesCacheEntity
import com.tanush.bouncer.models.Series
import com.tanush.bouncer.network.SeriesScraper
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Series tab.
 *
 * Two corpora exist for this one fact and the tab used to read neither cache. `SeriesSyncWorker`
 * downloads `active-series` — which `bouncer-poll-live-matches` fills with active **and archived**
 * series — into the Room `series_cache`, and only `SearchRepository` read it. The tab live-scraped
 * Cricbuzz on every open, so it could not show a series search could find, showed nothing at all
 * offline, and replaced the list with an error screen whenever the scrape failed.
 *
 * Now: paint the cache, then let the scrape refresh it. The scrape stays the authority for what is
 * on right now — it is the page Cricbuzz itself renders — but it can only ever add to or replace a
 * list, never empty one.
 */
class SeriesListViewModel(application: Application) : AndroidViewModel(application) {
    private val _series = MutableStateFlow<List<Series>>(emptyList())
    val series: StateFlow<List<Series>> = _series.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasError = MutableStateFlow(false)
    val hasError: StateFlow<Boolean> = _hasError.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _hasError.value = false

            val cached = runCatching {
                BouncerDatabase.getInstance(getApplication()).seriesCacheDao().getAll()
            }.getOrDefault(emptyList()).map(::seriesFrom)
            if (cached.isNotEmpty()) {
                _series.value = cached
                // Content is on screen, so the spinner has nothing left to say; the scrape below
                // continues and replaces the list when it lands.
                _isLoading.value = false
            }

            try {
                _series.value = SeriesScraper().fetchActiveSeries()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Sentry.captureException(e)
                // An error state only when there is nothing to show. A failed refresh over a list
                // the user is already reading is not a failure they need to be told about.
                _hasError.value = cached.isEmpty()
            } finally {
                _isLoading.value = false
            }
        }
    }
}

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
