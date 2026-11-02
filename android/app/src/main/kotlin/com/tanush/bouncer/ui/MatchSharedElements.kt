package com.tanush.bouncer.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.tanush.bouncer.ui.theme.BouncerMotion

// A Home row's two crests morph into the match header's crests when the match opens, and back on
// close. Every helper is a plain Modifier when a local is missing (a deep-link route, a screen
// outside the shared layout) or when the morph is switched off, so callers need no null checks.

/**
 * What Home's rows need to know about the open match. [openMatchId] hides the matching row's crests
 * while the header shows them; [morph] is off once the match closes into another page (a team, a
 * series), where flying the crests back into a Home that is sliding away would be wrong.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal class MatchShare(
    val scope: SharedTransitionScope,
    val openMatchId: String?,
    val morph: Boolean,
)

@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalMatchShare = compositionLocalOf<MatchShare?> { null }

/** The match overlay's own visibility scope: the header's side of the pair. */
internal val LocalMatchOverlayScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

private fun crestKey(matchId: String, teamIndex: Int) = "crest-$matchId-$teamIndex"

private val CrestBounds = BoundsTransform { _, _ ->
    tween(durationMillis = BouncerMotion.Duration.Medium, easing = FastOutSlowInEasing)
}

/** Home row side: hidden while this match is open, so the header owns the crest. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.homeCrestShared(matchId: String, teamIndex: Int, enabled: Boolean): Modifier {
    val share = LocalMatchShare.current
    if (!enabled || share == null || BouncerMotion.reducedMotionEnabled) return this
    val state = share.scope.rememberSharedContentState(crestKey(matchId, teamIndex))
    val visible = !share.morph || share.openMatchId != matchId
    return with(share.scope) {
        this@homeCrestShared.sharedElementWithCallerManagedVisibility(
            sharedContentState = state,
            visible = visible,
            boundsTransform = CrestBounds,
        )
    }
}

/** Match header side: shares with the Home row of the same match while the overlay animates. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.detailCrestShared(matchId: String, teamIndex: Int, enabled: Boolean): Modifier {
    val share = LocalMatchShare.current
    val overlay = LocalMatchOverlayScope.current
    if (!enabled || share == null || overlay == null || !share.morph || BouncerMotion.reducedMotionEnabled) return this
    val state = share.scope.rememberSharedContentState(crestKey(matchId, teamIndex))
    return with(share.scope) {
        this@detailCrestShared.sharedElement(
            sharedContentState = state,
            animatedVisibilityScope = overlay,
            boundsTransform = CrestBounds,
        )
    }
}
