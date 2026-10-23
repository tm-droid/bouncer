package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.tanush.bouncer.R
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.FeaturedKind
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import kotlinx.coroutines.delay

/**
 * The featured match at the top of Home: a live match, or (U17 rule 2) a followed match starting
 * within 24 h. Full-bleed on one tonal step up from the background, no card, no radius, no shadow.
 *
 * The scores, run rates, chase line and start time come from [MatchHeroV2] in its compact size, so
 * the match-detail header and this card cannot drift apart. The win-probability bar is live only:
 * a pre-match prior drawn like a live reading is what the old hero got wrong.
 *
 * Home has no per-match "last score change" time, so [lastFetchedMs] is when the list was last
 * fetched successfully. It is a provider because the disk stamp moves on every poll while the list
 * itself often does not change, which would leave a value read once looking ever older.
 */
@Composable
fun HeroMatchCard(
    match: CricketMatch,
    kind: FeaturedKind,
    lastFetchedMs: () -> Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val spacing = BouncerThemeV2.spacing
    val live = kind == FeaturedKind.Live
    val fetchedMs by produceState(initialValue = if (live) lastFetchedMs() else null, live) {
        while (live) {
            delay(FETCH_STAMP_REREAD_MS)
            value = lastFetchedMs()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md)) {
            HeroHeaderRow(match, upcoming = !live)
            Spacer(Modifier.height(spacing.sm))
            MatchHeroV2(match = match, situation = null, lastUpdatedMs = fetchedMs, size = HeroSize.Compact)
            if (live && match.winProbability != null) {
                Spacer(Modifier.height(spacing.sm))
                WinProbabilityBarV2(match)
            }
        }
        HorizontalDivider(color = colors.divider)
    }
}

@Composable
private fun HeroHeaderRow(match: CricketMatch, upcoming: Boolean) {
    val colors = BouncerThemeV2.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (upcoming) {
            Text(
                text = stringResource(R.string.hero_next_up),
                style = BouncerThemeV2.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            Spacer(Modifier.width(BouncerThemeV2.spacing.sm))
        }
        Text(
            text = matchHeaderText(match),
            style = BouncerThemeV2.typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Home polls every ~6 s; re-reading the stamp at this pace keeps "updated N min ago" honest. */
private const val FETCH_STAMP_REREAD_MS = 10_000L
