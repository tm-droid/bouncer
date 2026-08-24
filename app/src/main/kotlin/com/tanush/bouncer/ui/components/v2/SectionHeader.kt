package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Section title with optional "See All" button.
 */
@Composable
fun SectionHeaderV2(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = typography.headlineMedium,
            color = colors.onBackground,
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                style = typography.labelLarge,
                color = colors.primaryText,
                modifier = Modifier.clickable(onClick = onAction),
            )
        }
    }
}
