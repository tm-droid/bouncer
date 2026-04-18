package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Base elevated card for Bouncer V2. 12dp rounded corners, subtle shadow, surface bg.
 */
@Composable
fun BouncerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val shapes = BouncerThemeV2.shapes
    val elevation = BouncerThemeV2.elevation

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shapes.medium,
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation.card),
            content = content,
        )
    } else {
        Card(
            modifier = modifier,
            shape = shapes.medium,
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation.card),
            content = content,
        )
    }
}
