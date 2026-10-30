package com.tanush.bouncer.ui.theme.v2

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Bouncer V2 shape system — consistent rounded corners.
 */

@Immutable
data class BouncerShapesV2(
    /** 4dp — compact elements (small chips, badges) */
    val extraSmall: RoundedCornerShape = RoundedCornerShape(4.dp),
    /** 8dp — buttons, text fields, compact cards */
    val small: RoundedCornerShape = RoundedCornerShape(8.dp),
    /** 12dp — standard cards, match cards */
    val medium: RoundedCornerShape = RoundedCornerShape(12.dp),
    /** 16dp — large cards, hero cards, dialogs */
    val large: RoundedCornerShape = RoundedCornerShape(16.dp),
    /** 24dp — bottom sheets, modals */
    val extraLarge: RoundedCornerShape = RoundedCornerShape(24.dp),
    /** 50% — fully circular (pills, FABs, avatars) */
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50),
)

/** Convert to Material 3 Shapes for MaterialTheme interop */
fun BouncerShapesV2.toMaterial3(): Shapes = Shapes(
    extraSmall = extraSmall,
    small = small,
    medium = medium,
    large = large,
    extraLarge = extraLarge,
)

val LocalBouncerShapesV2 = staticCompositionLocalOf { BouncerShapesV2() }
