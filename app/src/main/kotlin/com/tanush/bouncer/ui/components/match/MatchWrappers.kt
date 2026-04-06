package com.tanush.bouncer.ui.components.match

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.MatchCard as BaseMatchCard

/**
 * Match-related delegation wrappers.
 */

@Composable
fun MatchCard(
    match: CricketMatch,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    isFavourite: Boolean = false,
    isRefreshing: Boolean = false,
    liveAiCommentary: String? = null
) {
    BaseMatchCard(
        match = match,
        onClick = onClick,
        modifier = modifier,
        isFavourite = isFavourite,
        isRefreshing = isRefreshing,
        liveAiCommentary = liveAiCommentary
    )
}
