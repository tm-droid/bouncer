package com.tanush.bouncer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tanush.bouncer.ui.components.PremiumBottomNavBar

/**
 * Legacy API shim kept for compatibility.
 * This file used to contain its own PremiumBottomNavigation implementation which
 * duplicated the newer implementation in com.tanush.bouncer.ui.components.BottomNavBar.
 *
 * We now delegate to PremiumBottomNavBar to reduce duplication while keeping the
 * public API surface stable. Call sites can migrate to PremiumBottomNavBar.
 */

@Deprecated(
    message = "Use com.tanush.bouncer.ui.components.PremiumBottomNavBar instead"
)
@Composable
fun PremiumBottomNavigation(
    selectedItem: Any, // kept generic to avoid breaking binary compatibility; callers pass their own enum
    onItemSelected: (Any) -> Unit,
    modifier: Modifier = Modifier
) {
    // Map legacy selectedItem to a route string if possible. For safety, prefer to keep
    // behavior identical for existing app entry points which already call the newer API.
    // Many callers in this project already use PremiumBottomNavBar directly; this shim
    // simply forwards to that implementation where possible.

    // Best-effort: if selectedItem is a String route, forward directly. Otherwise fall
    // back to null currentRoute which PremiumBottomNavBar treats as no selection.
    val currentRoute = when (selectedItem) {
        is String -> selectedItem
        else -> null
    }

    PremiumBottomNavBar(
        currentRoute = currentRoute,
        onNavigate = { route ->
            // Map back to legacy item only if caller expects a String.
            // We pass the route string as-is to the legacy callback; most legacy
            // call sites adapt easily. This keeps behavior safe and avoids complex
            // enum mapping that could break callers.
            onItemSelected(route as Any)
        },
        modifier = modifier
    )
}

