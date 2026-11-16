package com.tanush.bouncer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.data.history.HistoryClient
import com.tanush.bouncer.data.history.PastMatchIndex
import com.tanush.bouncer.data.history.PastMatchRow
import com.tanush.bouncer.data.history.PlayerCareer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the Cricsheet career page shows. */
sealed interface HistoricalPlayerState {
    data object Loading : HistoricalPlayerState
    data object Failed : HistoricalPlayerState

    /** [recent] are the player's latest matches, newest first, as list rows. */
    data class Loaded(val career: PlayerCareer, val recent: List<PastMatchRow>) : HistoricalPlayerState
}

private const val RECENT_MATCHES = 20

class HistoricalPlayerViewModel(
    private val playerId: String,
    private val loadCareer: suspend (String) -> PlayerCareer? = HistoryClient::player,
    /** Rows for these Cricsheet match ids from the search index; empty when it cannot be loaded. */
    private val indexedRows: suspend (List<String>) -> Map<String, PastMatchRow> = ::rowsFromIndex,
) : ViewModel() {
    private val _state = MutableStateFlow<HistoricalPlayerState>(HistoricalPlayerState.Loading)
    val state: StateFlow<HistoricalPlayerState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _state.value = HistoricalPlayerState.Loading
        viewModelScope.launch {
            val career = loadCareer(playerId)
            _state.value = if (career == null) HistoricalPlayerState.Failed else {
                HistoricalPlayerState.Loaded(career, recentRows(career))
            }
        }
    }

    /**
     * The player's record carries only date, sides and format for each match, not the result or the
     * event, so the rows come from the search index; a match it no longer holds (the archive's row
     * replaced it) keeps the record's own fields.
     */
    private suspend fun recentRows(career: PlayerCareer): List<PastMatchRow> {
        val recent = career.matches.distinctBy { it.id }.sortedByDescending { it.date }.take(RECENT_MATCHES)
        val known = indexedRows(recent.map { it.id })
        return recent.map { known[it.id] ?: it.toRow() }
    }

    companion object {
        fun storeKey(playerId: String): String = "history_player_$playerId"

        fun factory(playerId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HistoricalPlayerViewModel(playerId) as T
        }
    }
}

private suspend fun rowsFromIndex(ids: List<String>): Map<String, PastMatchRow> {
    PastMatchIndex.load()
    return PastMatchIndex.shared.value?.cricsheetById?.filterKeys { it in ids }.orEmpty()
}
