package com.tanush.bouncer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.components.glass.GlassSecondaryButton
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Centered empty / error state placeholder with icon, title, message,
 * and optional action button.
 */
@Composable
fun EmptyStateView(
    icon: ImageVector = Icons.Outlined.SportsCricket,
    title: String = "No matches found",
    message: String = "Check back later for live scores",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BouncerDesignSystem.dimens.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = BouncerDesignSystem.colors.goldPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = BouncerDesignSystem.typography.headingMedium,
            color = BouncerDesignSystem.colors.textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = BouncerDesignSystem.typography.bodyMedium,
            color = BouncerDesignSystem.colors.textSecondary,
            textAlign = TextAlign.Center
        )

        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(BouncerDesignSystem.dimens.xxl))

            GlassSecondaryButton(onClick = onAction, text = actionLabel)
        }
    }
}
