package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Centered icon + title + subtitle for empty/error screens.
 *
 * The icon sits on a soft circular plate so the state reads as deliberate rather than as a stray
 * glyph. It appears without an entrance animation: a page arriving at "nothing here" is not a state
 * change worth announcing. Pass a context-appropriate [icon] and, for errors, [onRetry] for a retry
 * action — a text button in the interactive gold, not a filled slab.
 */
@Composable
fun EmptyStateV2(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.SearchOff,
    onRetry: (() -> Unit)? = null,
    actionLabel: String? = null,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(PLATE_SIZE)
                .clip(CircleShape)
                .background(colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE),
                tint = colors.textSecondary,
            )
        }
        Spacer(Modifier.height(spacing.lg))
        Text(
            text = title,
            style = typography.titleLarge,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.sm))
        Text(
            text = subtitle,
            style = typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(spacing.md))
            TextButton(onClick = onRetry) {
                Text(
                    text = actionLabel ?: stringResource(R.string.action_retry),
                    style = typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primaryText,
                )
            }
        }
    }
}

private val PLATE_SIZE = 96.dp
private val ICON_SIZE = 40.dp
