package com.tanush.bouncer.ui.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 window-size classification for adaptive layouts.
 *
 * Breakpoints follow the canonical Android guidance:
 * - **Compact**: phones in portrait (< 600 dp)
 * - **Medium**: small tablets, phones in landscape, foldables (600–839 dp)
 * - **Expanded**: large tablets, desktop (≥ 840 dp)
 */
enum class WindowSizeClass {
    /** Phone portrait, < 600 dp wide. */
    COMPACT,

    /** Phone landscape / small tablet / foldable, 600–839 dp wide. */
    MEDIUM,

    /** Large tablet / desktop, ≥ 840 dp wide. */
    EXPANDED;

    companion object {
        /** Calculate the current window size class from screen width. */
        @Composable
        fun calculate(): WindowSizeClass {
            val configuration = LocalConfiguration.current
            return fromWidth(configuration.screenWidthDp.dp)
        }

        fun fromWidth(width: Dp): WindowSizeClass = when {
            width < 600.dp -> COMPACT
            width < 840.dp -> MEDIUM
            else -> EXPANDED
        }
    }
}

/**
 * Returns the current [WindowSizeClass] based on screen configuration.
 * Convenience top-level composable.
 */
@Composable
fun rememberWindowSizeClass(): WindowSizeClass = WindowSizeClass.calculate()
