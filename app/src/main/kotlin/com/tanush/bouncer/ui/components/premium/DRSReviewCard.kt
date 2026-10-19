package com.tanush.bouncer.ui.components.premium

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.models.DrsReviewEvent
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * The match's DRS reviews, one row each, as Cricinfo reported them.
 *
 * This card used to draw one review as a "dramatic verdict": a simulated pitch, neon stumps, a
 * glowing OUT/NOT OUT, and "PITCHING IN LINE · IMPACT IN LINE · WICKETS HITTING" for every review
 * whatever happened. None of that was known: the source line carries the over, the side and
 * whether it was batting or bowling, what was challenged, the umpire, the batter and whether the
 * review was upheld. Nothing else, so nothing else is shown — and "upheld" is not "out", since a
 * batting side's upheld review is a not-out.
 */
@Composable
fun DRSReviewCard(
    reviews: List<DrsReviewEvent>,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    BouncerCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.lg)) {
            Text(
                text = stringResource(R.string.drs_reviews_title),
                style = typography.titleMedium,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(spacing.sm))
            reviews.forEachIndexed { index, review ->
                if (index > 0) HorizontalDivider(color = colors.divider)
                ReviewRow(review)
            }
        }
    }
}

@Composable
private fun ReviewRow(review: DrsReviewEvent) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = review.over,
            style = typography.numericSmall,
            color = colors.textSecondary,
            modifier = Modifier.width(OVER_COLUMN),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = listOf(review.decision, review.batterName).filter { it.isNotBlank() }.joinToString(" · "),
                style = typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    if (review.reviewType.equals("Batting", ignoreCase = true)) {
                        R.string.drs_review_by_batting
                    } else {
                        R.string.drs_review_by_bowling
                    },
                    review.teamName,
                    review.umpireName,
                ),
                style = typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = stringResource(if (review.isUpheld) R.string.drs_upheld else R.string.drs_struck_down),
            style = typography.labelMedium,
            color = colors.onSurface,
        )
    }
}

private val OVER_COLUMN = 44.dp
