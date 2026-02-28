package com.tanush.bouncer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.network.CricBuzzScraper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val scraper = CricBuzzScraper()

    private val _currentMatches = MutableStateFlow<List<CricketMatch>>(emptyList())
    val currentMatches: StateFlow<List<CricketMatch>> = _currentMatches.asStateFlow()

    private val _selectedStatus = MutableStateFlow("Live")
    val selectedStatus: StateFlow<String> = _selectedStatus.asStateFlow()

    private val _selectedCategories = MutableStateFlow<Set<String>>(setOf("International", "League", "Domestic", "Women"))
    val selectedCategories: StateFlow<Set<String>> = _selectedCategories.asStateFlow()

    init {
        fetchMatches("Live")
    }

    fun setStatus(status: String) {
        _selectedStatus.value = status
        fetchMatches(status)
    }

    fun toggleCategory(category: String) {
        val current = _selectedCategories.value.toMutableSet()
        if (current.contains(category)) {
            current.remove(category)
        } else {
            current.add(category)
        }
        _selectedCategories.value = current
    }

    private fun fetchMatches(status: String) {
        viewModelScope.launch {
            val url = when (status) {
                "Live" -> "https://www.cricbuzz.com/cricket-match/live-scores"
                "Recent" -> "https://www.cricbuzz.com/cricket-match/live-scores/recent-matches"
                "Upcoming" -> "https://www.cricbuzz.com/cricket-match/live-scores/upcoming-matches"
                else -> "https://www.cricbuzz.com/cricket-match/live-scores"
            }
            
            val matches = scraper.fetchLiveMatches(url, status)
            _currentMatches.value = matches
        }
    }
}
