package com.tanush.bouncer.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.tanush.bouncer.R
import com.tanush.bouncer.data.CricketRepository
import com.tanush.bouncer.network.models.SanwebMatchSummary
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAB_LIVE = "live"
private const val TAB_UPCOMING = "upcoming"
private const val TAB_ALL = "all"

class TabbedScoreScreen(carContext: CarContext) : Screen(carContext) {

    private var activeTab = TAB_LIVE
    private var liveMatches: List<SanwebMatchSummary>? = null
    private var upcomingMatches: List<SanwebMatchSummary>? = null
    private var allMatches: List<SanwebMatchSummary>? = null
    private var loading = true

    init {
        fetchAll()
    }

    private fun fetchAll() {
        loading = true
        liveMatches = null
        upcomingMatches = null
        allMatches = null
        invalidate()

        CoroutineScope(Dispatchers.IO).launch {
            CricketRepository.getMatches("live").onSuccess { liveMatches = it.matches }.onError { e -> Sentry.captureException(e) }
            CricketRepository.getMatches("upcoming").onSuccess { upcomingMatches = it.matches }.onError { e -> Sentry.captureException(e) }
            CricketRepository.getMatches("recent").onSuccess { allMatches = it.matches }.onError { e -> Sentry.captureException(e) }
            loading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        if (loading) {
            return MessageTemplate.Builder("Loading scores…")
                .setTitle("Bouncer")
                .setHeaderAction(Action.APP_ICON)
                .build()
        }

        val tabCallback = object : TabTemplate.TabCallback {
            override fun onTabSelected(contentId: String) {
                activeTab = contentId
                invalidate()
            }
        }

        return TabTemplate.Builder(tabCallback)
            .addTab(buildTab(TAB_LIVE, "Live", R.drawable.ic_cricket_ball))
            .addTab(buildTab(TAB_UPCOMING, "Upcoming", R.drawable.ic_cricket_ball))
            .addTab(buildTab(TAB_ALL, "Recent", R.drawable.ic_cricket_ball))
            .setTabContents(TabContents.Builder(buildListTemplate(activeTab)).build())
            .setActiveTabContentId(activeTab)
            .build()
    }

    private fun buildTab(contentId: String, title: String, iconRes: Int): Tab =
        Tab.Builder()
            .setTitle(title)
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes)).build())
            .setContentId(contentId)
            .build()

    private fun buildListTemplate(tab: String): Template {
        val matches = when (tab) {
            TAB_LIVE -> liveMatches
            TAB_UPCOMING -> upcomingMatches
            else -> allMatches
        }

        if (matches.isNullOrEmpty()) {
            return MessageTemplate.Builder("No matches")
                .setTitle("Bouncer")
                .setHeaderAction(Action.APP_ICON)
                .addAction(Action.Builder().setTitle("Refresh").setOnClickListener { fetchAll() }.build())
                .build()
        }

        val listBuilder = ItemList.Builder()
        for (match in matches.take(6)) {
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
            .setSingleList(listBuilder.build())
            .build()
    }
}
