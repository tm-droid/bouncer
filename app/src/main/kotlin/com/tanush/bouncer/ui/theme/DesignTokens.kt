package com.tanush.bouncer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding

/**
 * Lightweight design-token facade.
 * - Namespaced objects only (no top-level vals)
 * - Reads from existing Bouncer composition locals
 * - Safe to import across the codebase without symbol collisions
 */
object DesignTokens {
    object Color {
        @Composable
        fun goldPrimary(): androidx.compose.ui.graphics.Color = LocalBouncerColors.current.goldPrimary

        @Composable
        fun goldLight(): androidx.compose.ui.graphics.Color = LocalBouncerColors.current.goldLight

        @Composable
        fun backgroundBase(): androidx.compose.ui.graphics.Color = LocalBouncerColors.current.backgroundBase

        @Composable
        fun textPrimary(): androidx.compose.ui.graphics.Color = LocalBouncerColors.current.textPrimary

        @Composable
        fun textMuted(): androidx.compose.ui.graphics.Color = LocalBouncerColors.current.textMuted

        @Composable
        fun glassBackground(): androidx.compose.ui.graphics.Color = LocalBouncerColors.current.glassBackground

        @Composable
        fun glassBorder(): androidx.compose.ui.graphics.Color = LocalBouncerColors.current.glassBorder
    }

    object Spacing {
        @Composable
        fun sm(): Dp = LocalBouncerDimens.current.sm

        @Composable
        fun md(): Dp = LocalBouncerDimens.current.md

        @Composable
        fun lg(): Dp = LocalBouncerDimens.current.lg

        @Composable
        fun xl(): Dp = LocalBouncerDimens.current.xl

        @Composable
        fun screenHorizontal(): Dp = LocalBouncerDimens.current.screenHorizontal

        @Composable
        fun spacingXS(): Dp = LocalBouncerDimens.current.spacing.xs

        @Composable
        fun gridUnit(multiplier: Int): Dp = LocalBouncerDimens.current.spacing.gridUnit(multiplier)
    }

    object Radius {
        @Composable
        fun card(): Dp = LocalBouncerDimens.current.radius.card

        @Composable
        fun sm(): Dp = LocalBouncerDimens.current.radius.sm

        @Composable
        fun lg(): Dp = LocalBouncerDimens.current.radius.lg
    }

    object Typography {
        @Composable
        fun headingMedium() = LocalBouncerTypography.current.headingMedium

        @Composable
        fun bodySmall() = LocalBouncerTypography.current.bodySmall

        @Composable
        fun labelSmall() = LocalBouncerTypography.current.labelSmall
    }
}

// Helper modifier extensions with a unique prefix to avoid collisions
@Composable
fun Modifier.dtPaddingGrid(multiplier: Int): Modifier = this.then(Modifier.padding(BouncerDesignSystem.dimens.spacing.gridUnit(multiplier)))

@Composable
fun Modifier.dtPaddingAll(dp: Dp): Modifier = this.then(Modifier.padding(dp))

// Small utility for inline usage
@Composable
fun dtSp(value: Int) = value.sp
