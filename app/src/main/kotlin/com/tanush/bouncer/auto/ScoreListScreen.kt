package com.tanush.bouncer.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.tanush.bouncer.data.CricketRepository
import com.tanush.bouncer.network.models.SanwebMatchSummary
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScoreListScreen(carContext: CarContext) : Screen(carContext) {

    private var matches: List<SanwebMatchSummary>? = null
    private var errorMessage: String? = null

    init {
        fetchMatches()
    }

    private fun fetchMatches() {
        matches = null
        errorMessage = null
        invalidate()

        CoroutineScope(Dispatchers.IO).launch {
            CricketRepository.getMatches("live")
                .onSuccess { response ->
                    matches = response.matches
                    errorMessage = null
                    invalidate()
                }
                .onFailure { throwable ->
                    Sentry.captureException(throwable)
                    errorMessage = throwable.localizedMessage ?: "Failed to load matches"
                    matches = null
                    invalidate()
                }
        }
    }

    override fun onGetTemplate(): Template {
        // Loading state
        if (matches == null && errorMessage == null) {
            return MessageTemplate.Builder("Loading live scores…")
                .setTitle("Bouncer")
                .setHeaderAction(Action.APP_ICON)
                .build()
        }

        // Error state
        if (errorMessage != null) {
            return MessageTemplate.Builder(errorMessage!!)
                .setTitle("Bouncer")
                .setHeaderAction(Action.APP_ICON)
                .addAction(
                    Action.Builder()
                        .setTitle("Retry")
                        .setOnClickListener { fetchMatches() }
                        .build()
                )
                .build()
        }

        val currentMatches = matches!!

        // Empty state
        if (currentMatches.isEmpty()) {
            return MessageTemplate.Builder("No live matches right now")
                .setTitle("Bouncer")
                .setHeaderAction(Action.APP_ICON)
                .addAction(
                    Action.Builder()
                        .setTitle("Refresh")
                        .setOnClickListener { fetchMatches() }
                        .build()
                )
                .build()
        }

        // Car App Library allows a maximum of 6 list items
        val listBuilder = ItemList.Builder()
        for (match in currentMatches.take(6)) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(match.title)
                    .addText(match.status)
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setTitle("Bouncer")
            .setHeaderAction(Action.APP_ICON)
            .addAction(
                Action.Builder()
                    .setTitle("Refresh")
                    .setOnClickListener { fetchMatches() }
                    .build()
            )
            .setSingleList(listBuilder.build())
            .build()
    }
}
