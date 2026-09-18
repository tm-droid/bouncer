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
            // No captureException on the error paths: CricketRepository already reports the failure
            // with the `storage_read_reason` and `matches_type` tags that say which of its five
            // branches fired. A second, tagless capture of the same exception groups into the same
            // Sentry issue and leaves those tags reading empty (ANDROID-4H).
            CricketRepository.getMatches("live").onSuccess { liveMatches = it.matches }
            CricketRepository.getMatches("upcoming").onSuccess { upcomingMatches = it.matches }
            CricketRepository.getMatches("recent").onSuccess { allMatches = it.matches }
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
            val row = Row.Builder().setTitle(match.title)
            // The score line, when the file carries one. Until `bouncer-poll-live-matches` began
            // parsing Cricbuzz itself, `{type}-matches` held `{id, title, status}` and nothing
            // else, so this screen could only ever show a fixture and a status — no scores at all,
            // on the one surface where the phone is out of reach. A file written by an older
            // deployment still arrives in that shape, so an absent score is simply not drawn.
            scoreLine(match)?.let { row.addText(it) }
            row.addText(match.status)
            listBuilder.addItem(row.build())
        }

        return ListTemplate.Builder()
            .setTitle("Bouncer")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .build()
    }

    /**
     * "AUS 266 & 234/1 (56.4) · IND 301" — both sides, batting side first.
     *
     * Null when neither side has a score, which is every upcoming fixture and every match from a
     * file written before the list carried teams.
     */
    private fun scoreLine(match: SanwebMatchSummary): String? {
        val sides = match.teams
            .sortedByDescending { it.isBatting }
            .mapNotNull { side ->
                val score = side.score.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val overs = side.overs.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                "${side.name} $score$overs"
            }
        return sides.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
    }
}
