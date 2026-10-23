package com.tanush.bouncer.ui.screens.matchdetail.v2

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.tanush.bouncer.ui.theme.BouncerMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Below this fling speed (px/s) a release settles to the nearer end; above it, to the end the fling points at. */
internal const val SETTLE_VELOCITY_PX_PER_S = 400f

/** Downward speed left after the list reached its top that is read as "bring the header back". */
internal const val POST_FLING_EXPAND_PX_PER_S = 100f

/**
 * The collapse arithmetic, free of Compose so a JVM test can drive it.
 *
 * `offset` runs from `-range` (fully collapsed) to 0 (fully expanded). Every method takes a y delta
 * in the scroll's own sign (negative = content moving up) and returns how much of it the header used.
 */
internal class HeaderCollapseMath(range: Float = 0f) {
    var range: Float = range.coerceAtLeast(0f)
        private set
    var offset: Float = 0f
        private set

    /** 0 expanded, 1 collapsed. A header with no measured range counts as expanded. */
    val fraction: Float get() = if (range <= 0f) 0f else (-offset / range).coerceIn(0f, 1f)

    /** The header can be measured after first composition and can resize on rotation; offset follows. */
    fun setRange(newRange: Float) {
        range = newRange.coerceAtLeast(0f)
        offset = offset.coerceIn(-range, 0f)
    }

    fun setOffset(value: Float) {
        offset = value.coerceIn(-range, 0f)
    }

    /** Scrolling up collapses the header before the list moves. */
    fun consumePre(deltaY: Float): Float = if (deltaY < 0f) move(deltaY) else 0f

    /** Delta the list left over: scrolling down, the list is at its top, so the header grows back. */
    fun consumePost(deltaY: Float): Float = if (deltaY > 0f) move(deltaY) else 0f

    fun expand() {
        offset = 0f
    }

    /**
     * Where the header should go once a fling has ended, or null to leave it. A fling that carried
     * on downward after the list hit its top has velocity left over: that is the reader pulling the
     * header back, however short the leftover. Otherwise a half-open header settles like a release.
     */
    fun settleAfterFling(leftoverVelocityY: Float): Float? = when {
        offset == 0f -> null
        leftoverVelocityY > POST_FLING_EXPAND_PX_PER_S -> 0f
        offset == -range -> null
        else -> settleTarget(leftoverVelocityY)
    }

    /** Where a release should come to rest: `-range` or 0. */
    fun settleTarget(velocityY: Float): Float {
        val collapsed = if (abs(velocityY) >= SETTLE_VELOCITY_PX_PER_S) velocityY < 0f else fraction >= 0.5f
        return if (collapsed) -range else 0f
    }

    private fun move(delta: Float): Float {
        val next = (offset + delta).coerceIn(-range, 0f)
        val used = next - offset
        offset = next
        return used
    }
}

/**
 * Collapses a header as the list under it scrolls. Wire it as
 * `Modifier.nestedScroll(collapse.connection)` on the container, read `collapse.offsetPx` (a
 * graphicsLayer/offset translation) or `collapse.fraction`, call `setRange(headerHeightPx - pinnedPx)`
 * from the header's onSizeChanged, and call `expand()` on a tab change or tap.
 */
@Stable
internal class HeaderCollapseState(private val scope: CoroutineScope) {
    private val math = HeaderCollapseMath()
    private var settleJob: Job? = null

    var offsetPx by mutableFloatStateOf(0f)
        private set
    var fraction by mutableFloatStateOf(0f)
        private set

    fun setRange(rangePx: Float) {
        math.setRange(rangePx)
        publish()
    }

    fun expand() {
        animateTo(0f)
    }

    val connection = object : NestedScrollConnection {
        // Only y is read, and only y is returned, so the pager's horizontal swipes pass through.
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Horizontal pager drags and settles pass through here with y = 0; cancelling then killed the
            // expand() a page change had just started.
            if (available.y != 0f) settleJob?.cancel()
            return Offset(0f, math.consumePre(available.y).also { publish() })
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            Offset(0f, math.consumePost(available.y).also { publish() })

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (pagerFlingDuringSettle(available)) return Velocity.Zero
            if (math.offset != 0f && math.offset != -math.range) animateTo(math.settleTarget(available.y))
            return Velocity.Zero
        }

        // A fling that scrolls the list to its top ends with velocity left over; without this the
        // header stayed collapsed after a long list (Commentary) was flung back up.
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (pagerFlingDuringSettle(available)) return Velocity.Zero
            math.settleAfterFling(available.y)?.let { animateTo(it) }
            return Velocity.Zero
        }
    }

    // A tab swipe changes page mid-drag, which starts expand(); the pager's release then flings here
    // with y = 0 while the header is part open, and settling to the nearer end collapsed it again.
    private fun pagerFlingDuringSettle(available: Velocity): Boolean =
        available.y == 0f && settleJob?.isActive == true

    private fun animateTo(target: Float) {
        settleJob?.cancel()
        settleJob = scope.launch {
            Animatable(math.offset).animateTo(target, BouncerMotion.Springs.noBounce()) {
                math.setOffset(value)
                publish()
            }
        }
    }

    private fun publish() {
        offsetPx = math.offset
        fraction = math.fraction
    }
}

@Composable
internal fun rememberHeaderCollapse(): HeaderCollapseState {
    val scope = rememberCoroutineScope()
    return remember { HeaderCollapseState(scope) }
}
