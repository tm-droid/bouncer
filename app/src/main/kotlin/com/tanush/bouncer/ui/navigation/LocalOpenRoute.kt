package com.tanush.bouncer.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens a route, for screens drawn outside the NavHost's own destinations — the match screen is an
 * overlay above it, and its series link, team names and players lead into the app beneath. Backing
 * out of the page reopens the match. Null where nothing provides one, and those stay plain text.
 */
val LocalOpenRoute = staticCompositionLocalOf<((String) -> Unit)?> { null }
