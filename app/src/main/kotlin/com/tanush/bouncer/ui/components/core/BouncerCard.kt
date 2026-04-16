package com.tanush.bouncer.ui.components.core

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.tanush.bouncer.ui.components.BouncerImportance
import com.tanush.bouncer.ui.components.GlassCard
import com.tanush.bouncer.ui.theme.CornerRadius

@Composable
fun BouncerCard(
    modifier: Modifier = Modifier,
    importance: BouncerImportance = BouncerImportance.STANDARD,
    cornerRadius: Dp = CornerRadius.lg,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(
        modifier = modifier,
        importance = importance,
        cornerRadius = cornerRadius,
        content = content
    )
}

