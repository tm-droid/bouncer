package com.tanush.bouncer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.models.PointsTableEntry
import com.tanush.bouncer.network.PointsTableScraper
import com.tanush.bouncer.network.isReportableScrapeFailure
import com.tanush.bouncer.ui.components.v2.FailureKind
import com.tanush.bouncer.ui.components.v2.failureKindOf
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PointsTableViewModel : ViewModel() {
    private val _entries = MutableStateFlow<List<PointsTableEntry>>(emptyList())
    val entries: StateFlow<List<PointsTableEntry>> = _entries.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _failureKind = MutableStateFlow(FailureKind.Server)
    /** What the last failed load was: offline or a server problem; read only while [error] is set. */
    val failureKind: StateFlow<FailureKind> = _failureKind.asStateFlow()

    private var lastSeriesId: String? = null

    fun load(seriesId: String) {
        if (seriesId == lastSeriesId && _entries.value.isNotEmpty()) return
        lastSeriesId = seriesId
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = PointsTableScraper().fetchPointsTable(seriesId)
                _entries.value = result
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (isReportableScrapeFailure(e)) Sentry.captureException(e)
                _failureKind.value = failureKindOf(e)
                _error.value = "Rain delay — couldn't reach the scorers. Check your connection and retry."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retry() {
        val seriesId = lastSeriesId ?: return
        lastSeriesId = null
        load(seriesId)
    }
}
