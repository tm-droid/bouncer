package com.tanush.bouncer.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens a series by id, for screens drawn outside the NavHost's own destinations — the match screen
 * is an overlay above it. Null where nothing provides one, and a series name then stays plain text.
 */
val LocalOpenSeries = staticCompositionLocalOf<((String) -> Unit)?> { null }
