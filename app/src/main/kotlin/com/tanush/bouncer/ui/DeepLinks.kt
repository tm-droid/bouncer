package com.tanush.bouncer.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.tanush.bouncer.R
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.navigation.BouncerScreen
import com.tanush.bouncer.ui.navigation.navigateForward
import com.tanush.bouncer.ui.navigation.navigateToTab
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import androidx.core.net.toUri

// A link is handled once and then cleared by the activity. It used to be remembered here by
// its URI string, and every alert for one match carries the same URI — so the second alert a
// reader tapped for that match brought the app forward and opened nothing.
@Composable
internal fun HandleDeepLink(
    externalDeepLinkUri: String?,
    viewModel: LiveActivityViewModel,
    navController: NavHostController,
    onMatchClick: (CricketMatch) -> Unit,
    onDeepLinkConsumed: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(externalDeepLinkUri) {
        val incomingLink = externalDeepLinkUri?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val matchId = deepLinkMatchId(incomingLink)
        if (matchId != null) {
            // Opened through onMatchClick, the overlay a tapped match uses. The NavHost route this
            // used to push drew the same match under the bottom bar with the list poll running.
            val match = viewModel.resolveMatch(matchId)
            if (match != null) {
                onMatchClick(match)
            } else {
                Toast.makeText(context, R.string.deep_link_match_unavailable, Toast.LENGTH_SHORT).show()
            }
        } else {
            handleExternalDeepLink(incomingLink, navController)
        }
        onDeepLinkConsumed()
    }
}

/** `app://bouncer/…`, or null for anything else. */
private fun parseBouncerLink(deepLinkUri: String): Uri? =
    runCatching { deepLinkUri.toUri() }.getOrNull()
        ?.takeIf { it.scheme == "app" && it.host == "bouncer" }

/** The match a link names, or null when it is not a match link or carries no usable id. */
private fun deepLinkMatchId(deepLinkUri: String): String? {
    val uri = parseBouncerLink(deepLinkUri) ?: return null
    val segments = uri.pathSegments.filter { it.isNotBlank() }
    if (segments.firstOrNull()?.lowercase() != "match") return null
    return (segments.getOrNull(1) ?: uri.getQueryParameter("matchId"))
        ?.takeIf { it.isNotBlank() && it.length <= MAX_DEEP_LINK_ID_LENGTH }
}

private const val MAX_DEEP_LINK_ID_LENGTH = 64

// Top-level segment of an `app://bouncer/…` link to the tab it selects. A match link with a
// usable id never reaches this table (see deepLinkMatchId); one without lands on Home.
private val DEEP_LINK_TABS = mapOf(
    "home" to BouncerScreen.Home.route,
    "match" to BouncerScreen.Home.route,
    "series" to BouncerScreen.SeriesList.route,
    "favorites" to BouncerScreen.Favorites.route,
    "favourites" to BouncerScreen.Favorites.route,
    "more" to BouncerScreen.More.route,
    "news" to BouncerScreen.News.route,
)

// …and to the page it pushes.
private val DEEP_LINK_PAGES = mapOf(
    "matches" to BouncerScreen.Matches.route,
    "all_matches" to BouncerScreen.Matches.route,
    "settings" to BouncerScreen.Settings.route,
    "appearance" to BouncerScreen.Appearance.route,
    "about" to BouncerScreen.About.route,
)

private fun handleExternalDeepLink(
    deepLinkUri: String,
    navController: NavHostController,
): Boolean {
    val parsedUri = parseBouncerLink(deepLinkUri) ?: return false
    val topLevel = parsedUri.pathSegments.firstOrNull { it.isNotBlank() }?.lowercase() ?: "home"

    val tab = DEEP_LINK_TABS[topLevel]
    val page = DEEP_LINK_PAGES[topLevel]
    when {
        tab != null -> navController.navigateToTab(tab)
        page != null -> navController.navigateForward(page)
        else -> return false
    }
    return true
}
