package com.tanush.bouncer.ui.components.premium

import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.TextSecondary

// Terminal-state wrapper around PremiumScorecardTab: spinner only while a load is in flight; once
// it finishes, a null/empty scorecard shows "No scorecard available" instead of spinning forever
// (a failed fetch leaves scorecard null with isLoading=false).
@Composable
fun ScorecardTab(
    scorecard: Scorecard?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    when {
        isLoading && scorecard == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = hierarchy.secondary.primary)
        }
        // Offers a retry: a failed fetch and a match with no card yet look the same from here, and
        // the reader had no way to ask again short of leaving the match.
        scorecard == null || scorecard.innings.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.common_no_scorecard_available), color = TextSecondary)
                if (onRetry != null) {
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.action_retry), color = BouncerThemeV2.colors.primaryText)
                    }
                }
            }
        }
        else -> PremiumScorecardTab(scorecard = scorecard, modifier = modifier)
    }
}
