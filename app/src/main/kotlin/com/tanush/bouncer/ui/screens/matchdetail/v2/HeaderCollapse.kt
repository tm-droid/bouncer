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
            settleJob?.cancel()
            return Offset(0f, math.consumePre(available.y).also { publish() })
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            Offset(0f, math.consumePost(available.y).also { publish() })

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (math.offset != 0f && math.offset != -math.range) animateTo(math.settleTarget(available.y))
            return Velocity.Zero
        }
    }

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
