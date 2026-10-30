package com.tanush.bouncer.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.data.AppResult
import com.tanush.bouncer.data.history.CRICSHEET_PREFIX
import com.tanush.bouncer.data.local.BouncerDatabase
import io.sentry.Sentry
import com.tanush.bouncer.data.local.entities.HighlightEntity
import com.tanush.bouncer.data.repository.HighlightsRepository
import com.tanush.bouncer.data.repository.PrefsHighlightsSyncMarker
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
    private val repo = HighlightsRepository(
        db.highlightDao(),
        db.matchDao(),
        PrefsHighlightsSyncMarker(application)
    )

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
        // No Appwrite row exists for a Cricsheet-only match, so there is nothing to ask for.
        if (bouncerMatchId.startsWith(CRICSHEET_PREFIX)) return
        viewModelScope.launch(bouncerExceptionHandler) {
            val result = repo.fetchForMatch(bouncerMatchId)
            if (result is AppResult.Error) Sentry.captureException(result.exception)
        }
    }

    companion object {
        /**
         * `ViewModelStore` keys are a flat namespace shared by every type in the store, and
         * `ViewModelProvider` treats a key holding the wrong type as a miss: it constructs a
         * replacement and `ViewModelStore.put` **clears** whatever it evicted. A bare `matchId`
         * here collided with [MatchDetailViewModel]'s key on the same screen, so the two rebuilt
         * and cleared each other on every recomposition. Prefix per type; never share.
         */
        fun storeKey(matchId: String): String = "highlights_$matchId"

        fun factory(context: Context, matchId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HighlightsViewModel(context.applicationContext as Application, matchId) as T
            }
    }
}
