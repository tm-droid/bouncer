package com.tanush.bouncer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.models.Series
import com.tanush.bouncer.network.SeriesScraper
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SeriesListViewModel : ViewModel() {
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
            try {
                _series.value = SeriesScraper().fetchActiveSeries()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Sentry.captureException(e)
                _hasError.value = true
            } finally {
                _isLoading.value = false
            }
        }
    }
}
