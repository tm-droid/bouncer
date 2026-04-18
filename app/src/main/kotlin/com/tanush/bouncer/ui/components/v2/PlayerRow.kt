package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Player name + role badge + optional stats — for squads and scorecard.
 */
@Composable
fun PlayerRowV2(
    name: String,
    role: String,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    isCaptain: Boolean = false,
    isWicketKeeper: Boolean = false,
    trailingStats: String? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Column {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = name,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.width(spacing.md))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        style = typography.titleMedium,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isCaptain) {
                        RoleBadge("c")
                    }
                    if (isWicketKeeper) {
                        RoleBadge("wk")
                    }
                }
                Text(
                    text = role,
                    style = typography.bodySmall,
                    color = colors.textSecondary,
                )
            }

            if (trailingStats != null) {
                Text(
                    text = trailingStats,
                    style = typography.scoreMedium,
                    color = colors.onSurface,
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(color = colors.divider)
        }
    }
}

@Composable
private fun RoleBadge(text: String) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val shapes = BouncerThemeV2.shapes

    Text(
        text = text,
        style = typography.labelSmall,
        color = colors.primary,
        modifier = Modifier
            .background(colors.primaryContainer, shapes.extraSmall)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
