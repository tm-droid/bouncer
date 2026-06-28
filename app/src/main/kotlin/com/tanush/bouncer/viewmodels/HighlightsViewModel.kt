package com.tanush.bouncer.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.local.BouncerDatabase
import io.sentry.Sentry
import com.tanush.bouncer.data.local.entities.HighlightEntity
import com.tanush.bouncer.data.repository.HighlightsRepository
import com.tanush.bouncer.utils.bouncerExceptionHandler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HighlightsViewModel(
    application: Application,
    val bouncerMatchId: String,
) : AndroidViewModel(application) {
    private val db = BouncerDatabase.getInstance(application)
    private val repo = HighlightsRepository(db.highlightDao(), db.matchDao())

    // Observe Room reactively: any later write (Realtime ingest, refresh) flows to the UI.
    val highlights: StateFlow<List<HighlightEntity>> =
        db.highlightDao().queryByBouncerMatchId(bouncerMatchId)
            .map { rows -> rows.sortedWith(compareBy({ if (it.type == "VERIFIED") 0 else 1 }, { -it.fetchedAt })) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    // One-shot trigger that refreshes Room from Appwrite; results surface via the [highlights] Flow.
    private fun refresh() {
        viewModelScope.launch(bouncerExceptionHandler) {
            val result = repo.fetchForMatch(bouncerMatchId)
            if (result is AppResult.Error) Sentry.captureException(result.exception)
        }
    }

    companion object {
        fun factory(context: Context, matchId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HighlightsViewModel(context.applicationContext as Application, matchId) as T
            }
    }
}
