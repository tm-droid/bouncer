package com.tanush.bouncer.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

/**
 * Utility for theme-related calculations and states
 */

@Composable
fun rememberDominantColor(url: String?, default: Color = GoldDark): State<Color> {
    // TODO: Implement actual Palette extraction from Coil image
    // For now, return a stable state with the default color to fix compilation
    return remember(url) { mutableStateOf(default) }
}
