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

    /** 60-30-10 hierarchy color accessors */
    object Hierarchy {
        /** 60% — dark backgrounds, surfaces, text */
        val dominant: DominantColors
            @Composable @androidx.compose.runtime.ReadOnlyComposable
            get() = LocalColorHierarchy.current.dominant

        /** 30% — team colors: tints, borders, fills, indicators */
        val secondary: SecondaryColors
            @Composable @androidx.compose.runtime.ReadOnlyComposable
            get() = LocalColorHierarchy.current.secondary

        /** 10% — Bouncer Gold: CTAs, premium badges, key highlights */
        val accent: AccentColors
            @Composable @androidx.compose.runtime.ReadOnlyComposable
            get() = LocalColorHierarchy.current.accent
    }

    object Spacing {
        @Composable
        fun micro(): Dp = LocalBouncerDimens.current.spacing.micro

        @Composable
        fun xs(): Dp = LocalBouncerDimens.current.spacing.xs

        @Composable
        fun sm(): Dp = LocalBouncerDimens.current.sm

        @Composable
        fun md(): Dp = LocalBouncerDimens.current.md

        @Composable
        fun lg(): Dp = LocalBouncerDimens.current.lg

        @Composable
        fun xl(): Dp = LocalBouncerDimens.current.xl

        @Composable
        fun xxl(): Dp = LocalBouncerDimens.current.xxl

        @Composable
        fun screenHorizontal(): Dp = LocalBouncerDimens.current.screenHorizontal

        @Composable
        fun spacingXS(): Dp = xs()

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

        @Composable
        fun xl(): Dp = LocalBouncerDimens.current.radius.xl

        @Composable
        fun pill(): Dp = LocalBouncerDimens.current.radius.pill
    }

    object Elevation {
        @Composable
        fun sm(): Dp = LocalBouncerDimens.current.elevation.sm

        @Composable
        fun md(): Dp = LocalBouncerDimens.current.elevation.md

        @Composable
        fun lg(): Dp = LocalBouncerDimens.current.elevation.lg

        @Composable
        fun xl(): Dp = LocalBouncerDimens.current.elevation.xl
    }

    object Typography {
        @Composable
        fun headlineMedium() = LocalBouncerTypography.current.headlineMedium

        @Composable
        fun bodySmall() = LocalBouncerTypography.current.bodySmall

        @Composable
        fun labelSmall() = LocalBouncerTypography.current.labelSmall

        @Composable
        fun headingMedium() = LocalBouncerTypography.current.headlineMedium

        /** Tabular (monospace) numbers at various sizes */
        @Composable
        fun numberLarge() = LocalBouncerTypography.current.numberLarge

        @Composable
        fun numberMedium() = LocalBouncerTypography.current.numberMedium

        @Composable
        fun numberSmall() = LocalBouncerTypography.current.numberSmall

        @Composable
        fun runRate() = LocalBouncerTypography.current.runRate

        @Composable
        fun figureStat() = LocalBouncerTypography.current.figureStat
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
