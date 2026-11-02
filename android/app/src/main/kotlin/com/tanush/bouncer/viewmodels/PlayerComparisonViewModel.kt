package com.tanush.bouncer.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.models.PlayerProfile
import com.tanush.bouncer.network.PlayerProfileScraper
import com.tanush.bouncer.services.PlayerCacheWorker
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerComparisonViewModel(application: Application) : AndroidViewModel(application) {
    private val _player1 = MutableStateFlow<PlayerProfile?>(null)
    val player1: StateFlow<PlayerProfile?> = _player1.asStateFlow()

    private val _player2 = MutableStateFlow<PlayerProfile?>(null)
    val player2: StateFlow<PlayerProfile?> = _player2.asStateFlow()

    private val _loadingPlayer1 = MutableStateFlow(false)
    val loadingPlayer1: StateFlow<Boolean> = _loadingPlayer1.asStateFlow()

    private val _loadingPlayer2 = MutableStateFlow(false)
    val loadingPlayer2: StateFlow<Boolean> = _loadingPlayer2.asStateFlow()

    fun setPlayer(slot: Int, stub: PlayerProfile) {
        when (slot) {
            1 -> { _player1.value = stub; loadFullProfile(1, stub) }
            2 -> { _player2.value = stub; loadFullProfile(2, stub) }
        }
    }

    fun clearPlayer(slot: Int) {
        when (slot) {
            1 -> _player1.value = null
            2 -> _player2.value = null
        }
    }

    private fun loadFullProfile(slot: Int, stub: PlayerProfile) {
        viewModelScope.launch {
            if (slot == 1) _loadingPlayer1.value = true else _loadingPlayer2.value = true
            try {
                // Cache first, exactly as the profile screen and PlayerCacheWorker do. Two
                // players compared meant two Cricbuzz page loads per open, for profiles the
                // shared table usually already holds.
                val full = fullProfile(stub.name)
                when (slot) {
                    1 -> _player1.value = full ?: stub
                    2 -> _player2.value = full ?: stub
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Sentry.captureException(e)
            } finally {
                if (slot == 1) _loadingPlayer1.value = false else _loadingPlayer2.value = false
            }
        }
    }
}

/**
 * Room, then the shared Appwrite table, then Cricbuzz — the order `PlayerCacheWorker` already
 * uses. Two players compared meant two Cricbuzz page loads per open, for profiles the shared table
 * usually already holds.
 *
 * Top-level to keep [PlayerComparisonViewModel.loadFullProfile] under detekt's complexity cap.
 */
private suspend fun PlayerComparisonViewModel.fullProfile(name: String): PlayerProfile? =
    PlayerCacheWorker.cachedProfile(getApplication(), name) ?: PlayerProfileScraper.scrapeProfile(name)
