package com.tanush.bouncer.ui.components.premium

import com.tanush.bouncer.network.MatchSquads

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Scoreboard
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tanush.bouncer.ui.components.v2.DelayedSkeleton
import com.tanush.bouncer.ui.components.v2.ShimmerTableRows
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.models.Scorecard

// Terminal-state wrapper around PremiumScorecardTab: skeleton only while a load is in flight; once
// it finishes, a null/empty scorecard shows "No scorecard available" instead of spinning forever
// (a failed fetch leaves scorecard null with isLoading=false).
@Composable
fun ScorecardTab(
    scorecard: Scorecard?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    isLive: Boolean = false,
    squads: MatchSquads? = null,
    onRetry: (() -> Unit)? = null,
) {
    when {
        isLoading && scorecard == null -> DelayedSkeleton(isLoading) {
            ShimmerTableRows(Modifier.fillMaxSize())
        }
        scorecard == null || scorecard.innings.isEmpty() -> EmptyStateV2(
            title = stringResource(R.string.state_scorecard_title),
            subtitle = stringResource(R.string.state_scorecard_cause),
            modifier = Modifier.fillMaxSize(),
            icon = Icons.Outlined.Scoreboard,
            onRetry = onRetry,
            actionLabel = onRetry?.let { stringResource(R.string.action_retry) },
        )
        else -> CompositionLocalProvider(LocalScorecardIsLive provides isLive, LocalScorecardSquads provides squads) {
            PremiumScorecardTab(scorecard = scorecard, modifier = modifier)
        }
    }
}

/**
 * Whether the scorecard's match is in play. A local rather than a parameter of
 * [PremiumScorecardTab], whose signature is the key of its detekt baseline entries.
 */
internal val LocalScorecardIsLive = staticCompositionLocalOf { false }

/** The match's team sheets, for "Yet to bat". A local for the same reason as [LocalScorecardIsLive]. */
internal val LocalScorecardSquads = staticCompositionLocalOf<MatchSquads?> { null }
