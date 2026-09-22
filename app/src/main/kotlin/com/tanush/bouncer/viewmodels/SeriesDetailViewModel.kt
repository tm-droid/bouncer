package com.tanush.bouncer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.report
import com.tanush.bouncer.models.SeriesMatchDay
import com.tanush.bouncer.models.SeriesStats
import com.tanush.bouncer.network.SeriesDetailScraper
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One tab's load state. Empty is a real answer — a series with no fixtures listed yet. */
sealed interface SeriesTabState<out T> {
    data object Loading : SeriesTabState<Nothing>
    data class Loaded<T>(val data: T) : SeriesTabState<T>
    data object Empty : SeriesTabState<Nothing>
    data object Error : SeriesTabState<Nothing>
}

/**
 * A series' Schedule and Stats tabs.
 *
 * Schedule loads with the screen; Stats loads the first time its tab is opened, and each stat type
 * is kept once fetched, so flicking between Most Runs and Most Wickets fetches each once. Formats
 * need no fetch at all: one stats page carries every format's table.
 */
class SeriesDetailViewModel(
    private val scraper: SeriesDetailScraper = SeriesDetailScraper(),
) : ViewModel() {

    private val _schedule = MutableStateFlow<SeriesTabState<List<SeriesMatchDay>>>(SeriesTabState.Loading)
    val schedule: StateFlow<SeriesTabState<List<SeriesMatchDay>>> = _schedule.asStateFlow()

    private val _stats = MutableStateFlow<SeriesTabState<SeriesStats>>(SeriesTabState.Loading)
    val stats: StateFlow<SeriesTabState<SeriesStats>> = _stats.asStateFlow()

    /** The stat type on screen; null until the first stats page names its default. */
    private val _selectedStatType = MutableStateFlow<String?>(null)
    val selectedStatType: StateFlow<String?> = _selectedStatType.asStateFlow()

    private val _selectedFormat = MutableStateFlow<String?>(null)
    val selectedFormat: StateFlow<String?> = _selectedFormat.asStateFlow()

    private var seriesId: String? = null
    private val statsByType = mutableMapOf<String, SeriesStats>()
    private var statsJob: Job? = null
    private var statsRequested = false
    private var driftReported = false

    fun load(seriesId: String) {
        if (seriesId == this.seriesId) return
        this.seriesId = seriesId
        statsByType.clear()
        statsRequested = false
        loadSchedule(seriesId)
    }

    fun retrySchedule() {
        seriesId?.let(::loadSchedule)
    }

    /** Called when the Stats tab is shown; fetches the default stat type once. */
    fun ensureStats() {
        if (statsRequested) return
        statsRequested = true
        loadStats(_selectedStatType.value)
    }

    fun retryStats() = loadStats(_selectedStatType.value)

    fun selectStatType(value: String) {
        if (value == _selectedStatType.value && _stats.value is SeriesTabState.Loaded) return
        _selectedStatType.value = value
        val cached = statsByType[value]
        if (cached != null) show(cached) else loadStats(value)
    }

    fun selectFormat(format: String) {
        _selectedFormat.value = format
    }

    private fun loadSchedule(seriesId: String) {
        viewModelScope.launch {
            _schedule.value = SeriesTabState.Loading
            when (val result = scraper.fetchSchedule(seriesId)) {
                is AppResult.Success -> {
                    val days = result.data
                    _schedule.value = if (days.isEmpty()) SeriesTabState.Empty else SeriesTabState.Loaded(days)
                    reportStatsDriftIfAny()
                }
                is AppResult.Error -> {
                    result.report()
                    _schedule.value = SeriesTabState.Error
                }
                AppResult.Loading -> Unit
            }
        }
    }

    private fun loadStats(statType: String?) {
        val id = seriesId ?: return
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            _stats.value = SeriesTabState.Loading
            when (val result = scraper.fetchStats(id, statType)) {
                is AppResult.Success -> {
                    val stats = result.data
                    val type = stats.selectedType.ifEmpty { statType.orEmpty() }
                    if (type.isNotEmpty()) statsByType[type] = stats
                    if (_selectedStatType.value == null && type.isNotEmpty()) _selectedStatType.value = type
                    show(stats)
                    reportStatsDriftIfAny()
                }
                is AppResult.Error -> {
                    result.report()
                    _stats.value = SeriesTabState.Error
                }
                AppResult.Loading -> Unit
            }
        }
    }

    private fun show(stats: SeriesStats) {
        val formats = stats.tables.map { it.format }
        // Keep the format the reader picked if this stat type has it too.
        if (_selectedFormat.value !in formats) {
            _selectedFormat.value = stats.defaultFormat ?: formats.firstOrNull()
        }
        // A stat type with no qualifying players ("Most Nineties") still has a menu to pick another
        // from, so only a page with neither a menu nor a row is empty.
        val nothing = stats.types.isEmpty() && stats.tables.all { it.rows.isEmpty() }
        _stats.value = if (nothing) SeriesTabState.Empty else SeriesTabState.Loaded(stats)
    }

    /**
     * No stats is normal for a series that has not started, and is the page shape a parser drift
     * would also produce. Played matches on the schedule tell the two apart.
     */
    private fun reportStatsDriftIfAny() {
        val days = (_schedule.value as? SeriesTabState.Loaded)?.data ?: return
        if (_stats.value != SeriesTabState.Empty || driftReported) return
        val played = days.any { day -> day.matches.any { it.info.equals("Complete", ignoreCase = true) } }
        if (played) {
            driftReported = true
            Sentry.captureMessage("Series $seriesId has completed matches but no stats parsed", SentryLevel.WARNING)
        }
    }
}
