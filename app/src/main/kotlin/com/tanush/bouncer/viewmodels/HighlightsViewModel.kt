package com.tanush.bouncer.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.local.BouncerDatabase
import com.tanush.bouncer.data.local.entities.HighlightEntity
import com.tanush.bouncer.data.remote.AppwriteHighlightsClient
import com.tanush.bouncer.data.repository.HighlightsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HighlightsViewModel(
    application: Application,
    val bouncerMatchId: String,
) : AndroidViewModel(application) {
    private val db = BouncerDatabase.getInstance(application)
    private val repo = HighlightsRepository(db.highlightDao(), db.matchDao())

    private val _highlights = MutableStateFlow<List<HighlightEntity>>(emptyList())
    val highlights: StateFlow<List<HighlightEntity>> = _highlights.asStateFlow()

    init {
        fetchHighlights()
    }

    private fun fetchHighlights() {
        viewModelScope.launch {
            when (val result = repo.fetchForMatch(bouncerMatchId)) {
                is AppResult.Success -> {
                    _highlights.value = result.data.sortedWith(compareBy({ if (it.type == "VERIFIED") 0 else 1 }, { -it.fetchedAt }))
                }
                is AppResult.Error -> {
                    _highlights.value = emptyList()
                }
                is AppResult.Loading -> {}
            }
        }
    }

    companion object {
        fun factory(context: Context, matchId: String) = viewModelFactory {
            initializer {
                HighlightsViewModel(
                    checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]),
                    matchId
                )
            }
        }
    }
}
