package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Label + value row with optional divider. Used in scorecard, stats, settings.
 */
@Composable
fun StatRowV2(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm, horizontal = spacing.lg),
    ) {
        Text(
            text = label,
            style = typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = value,
            style = typography.titleSmall,
            color = colors.onSurface,
        )
    }
    if (showDivider) {
        HorizontalDivider(color = colors.divider)
    }
}
